package com.sideplanetary.overseerPlugin.collector

import com.enderstorage.sentinel.dto.*
import com.sideplanetary.overseerPlugin.config.SentinelConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TelemetryDispatcher(
    private val config: SentinelConfig,private val queue: BoundedTelemetryQueue,
    private val directory: Path,private val logger: Logger
) {
    private val json=Json { encodeDefaults=true; ignoreUnknownKeys=true }
    private val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val worker=Executors.newSingleThreadScheduledExecutor { run -> Thread(run,"caenis-telemetry").apply { isDaemon=true } }
    private var token=""
    private var expires=0L
    private var nextAttempt=0L
    private var backoff=1000L
    private var nextControlAttempt=0L
    private var controlBackoff=1000L
    private var lastHeartbeat=0L
    private var lastFlush=0L
    @Volatile var configuration=AgentConfiguration()
        private set
    @Volatile var latestHealth: Heartbeat?=null
    @Volatile var spoolBytes=0L
        private set
    private var lastWarning=0L

    fun start() {
        worker.scheduleWithFixedDelay({
            try { tick() } catch(error: Exception) {
                if(System.currentTimeMillis()-lastWarning>60000) {
                    logger.warning("Telemetry worker unavailable (${error.javaClass.simpleName}); queued data will retry.")
                    lastWarning=System.currentTimeMillis()
                }
            }
        },0,500,TimeUnit.MILLISECONDS)
    }
    private fun tick() {
        Files.createDirectories(directory)
        persistBatch()
        val now=System.currentTimeMillis()
        // Control traffic has its own retry clock: a NiFi outage must not silence health.
        if(now<nextControlAttempt) return
        try {
            if(now >= expires-60000) {
                val reply=request("enroll","{}".toByteArray())
                check(reply.statusCode()==200) { "Enrollment rejected" }
                val enrolled=json.decodeFromString<Enrollment>(reply.body())
                token=enrolled.token; expires=enrolled.expiresAt; configuration=enrolled.configuration
            }
            if(now-lastHeartbeat>=5000) {
                latestHealth?.let { snapshot ->
                    val health=snapshot.copy(queueDepth=queue.size(),droppedEvents=queue.dropped.get(),spoolBytes=spoolBytes)
                    val reply=request("heartbeat",json.encodeToString(health).toByteArray())
                    if(reply.statusCode()==401) { expires=0; error("Agent session expired") }
                    check(reply.statusCode()==200) { "Heartbeat rejected" }
                    configuration=json.decodeFromString<AgentReply>(reply.body()).configuration
                    lastHeartbeat=now
                }
            }
            controlBackoff=1000
        } catch(error: Exception) {
            nextControlAttempt=now+controlBackoff
            controlBackoff=(controlBackoff*2).coerceAtMost(30000)
            throw error
        }
        if(now<nextAttempt) return
        try {
            if(now-lastFlush>=configuration.flushIntervalMs) {
                val files=spoolFiles()
                files.firstOrNull()?.let { path ->
                    if(Files.getLastModifiedTime(path).toMillis()<now-86400000) {
                        Files.deleteIfExists(path)
                        queue.dropped.addAndGet(eventCount(path.fileName.toString()))
                        logger.warning("Expired telemetry spool batch removed after 24 hours.")
                    } else {
                        val reply=request("mining",Files.readAllBytes(path),config.telemetryUrl,path.fileName.toString().substringBefore('_'))
                        when(reply.statusCode()) {
                            200,202 -> Files.deleteIfExists(path)
                            400,413,422 -> {
                                // Poison batches never hold the entire queue. Keep a bounded quarantine for operators.
                                val quarantine=directory.resolve("rejected")
                                Files.createDirectories(quarantine)
                                Files.move(path,quarantine.resolve(path.fileName),StandardCopyOption.REPLACE_EXISTING)
                                val rejected=Files.list(quarantine).use { it.sorted().toList() }
                                rejected.dropLast(5).forEach { Files.deleteIfExists(it) }
                                queue.dropped.addAndGet(eventCount(path.fileName.toString()))
                                logger.warning("A telemetry batch was rejected; retained in the spool/rejected directory.")
                            }
                            else -> error("Ingestion is unavailable")
                        }
                    }
                }
                lastFlush=now
            }
            backoff=1000
        } catch(error: Exception) {
            nextAttempt=now+backoff
            backoff=(backoff*2).coerceAtMost(30000)
            throw error
        }
    }
    private fun persistBatch() {
        val batch=ArrayList<BlockBreakTelemetry>()
        queue.drainTo(batch,configuration.batchSize.coerceIn(1,500))
        if(batch.isNotEmpty()) {
            val bytes=json.encodeToString(batch).toByteArray()
            val id=UUID.randomUUID()
            val pending=directory.resolve("$id.tmp")
            try {
                Files.write(pending,bytes)
                val target=directory.resolve("${id}_${batch.size}.json")
                try { Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE) }
                catch(_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(pending,target) }
            } catch(error: Exception) {
                queue.dropped.addAndGet(batch.size.toLong())
                throw error
            }
        }
        val files=spoolFiles()
        var size=files.sumOf { Files.size(it) }
        for(path in files) {
            if(size<=config.spoolMaxBytes) break
            val length=Files.size(path)
            Files.deleteIfExists(path); size-=length
            queue.dropped.addAndGet(eventCount(path.fileName.toString()))
        }
        spoolBytes=size
    }
    private fun spoolFiles() = Files.list(directory).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".json") }
            .sorted(compareBy<Path> { Files.getLastModifiedTime(it).toMillis() }.thenBy { it.fileName.toString() }).toList()
    }
    private fun eventCount(name: String) = name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L
    private fun request(purpose: String,body: ByteArray,url: String="${config.backendUrl}/api/v1/agent/${config.instanceId}/$purpose",nonce: String=UUID.randomUUID().toString()): HttpResponse<String> {
        val timestamp=System.currentTimeMillis().toString()
        val signature=RequestSigning.sign(config.secret,config.instanceId,purpose,timestamp,nonce,body)
        val request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
            .header("Content-Type","application/json").header("X-Caenis-Instance",config.instanceId)
            .header("X-Caenis-Timestamp",timestamp).header("X-Caenis-Nonce",nonce)
            .header("X-Caenis-Signature",signature).header("X-Caenis-Token",token)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        return http.send(request,HttpResponse.BodyHandlers.ofString())
    }
    fun close() {
        // Queue drain and disk I/O remain off the tick thread even during disable.
        worker.execute { while(!queue.isEmpty()) runCatching { persistBatch() }; http.close() }
        worker.shutdown()
    }
}
