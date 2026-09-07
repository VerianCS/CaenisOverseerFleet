package com.enderstorage.backendserviceservermanager

import jakarta.annotation.PreDestroy
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.withLock

@ConfigurationProperties("caenis.manager")
data class ManagerProperties(
    var key: String = "", var root: String = "./servers", var javaCommand: String = "java",
    var instances: Map<String,ManagedServer> = emptyMap()
)
data class ManagedServer(var directory: String = "", var jar: String = "paper.jar", var memoryMb: Int = 2048)
data class LifecycleInput(val action: String)

@SpringBootApplication
@EnableConfigurationProperties(ManagerProperties::class)
class BackendServiceServermanagerApplication {
    @Bean
    fun authentication(settings: ManagerProperties): OncePerRequestFilter {
        require(settings.key.length >= 32) { "CAENIS_MANAGER_KEY must contain at least 32 characters" }
        return object: OncePerRequestFilter() {
            override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
                val provided=request.getHeader("X-Caenis-Manager-Key") ?: ""
                if(!MessageDigest.isEqual(provided.toByteArray(),settings.key.toByteArray())) { response.sendError(401); return }
                chain.doFilter(request,response)
            }
        }
    }
}
fun main(args: Array<String>) { runApplication<BackendServiceServermanagerApplication>(*args) }

@Service
class ProcessManager(private val settings: ManagerProperties) {
    private val ownership = ConcurrentHashMap<String,Pair<FileChannel,FileLock>>()
    private val processes=ConcurrentHashMap<String,Process>()
    private val locks=ConcurrentHashMap<String,ReentrantLock>()
    private fun configured(id: String): ManagedServer {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{2,63}")))
        return settings.instances[id] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND,"Instance is not configured on this host")
    }
    fun status(id: String): Map<String,Any> {
        configured(id)
        val process=processes[id]
        return mapOf("id" to id,"state" to if(process?.isAlive==true) "RUNNING" else "STOPPED","pid" to if(process?.isAlive==true) process.pid() else 0)
    }
    fun action(id: String,action: String): Map<String,Any> {
        val config=configured(id)
        require(action in listOf("start","stop","restart"))
        val lock=locks.computeIfAbsent(id) { ReentrantLock() }
        if(!lock.tryLock()) throw ResponseStatusException(HttpStatus.CONFLICT,"A lifecycle operation is already running")
        try {
            if(action=="stop" || action=="restart") stop(id)
            if(action=="start" || action=="restart") start(id,config)
            return status(id)
        } finally { lock.unlock() }
    }
    private fun start(id: String,config: ManagedServer) {
        if(processes[id]?.isAlive==true) throw ResponseStatusException(HttpStatus.CONFLICT,"Instance is already running")
        val root=Path.of(settings.root).toRealPath()
        val directory=root.resolve(config.directory).toRealPath()
        require(directory.startsWith(root) && directory != root) { "Server directory must stay inside the configured root" }
        val jar=directory.resolve(config.jar).toRealPath()
        require(jar.startsWith(directory) && Files.isRegularFile(jar) && jar.fileName.toString().endsWith(".jar")) { "Invalid server jar" }
        require(config.memoryMb in 512..65536)
        val eula=directory.resolve("eula.txt")
        require(Files.isRegularFile(eula) && Files.readAllLines(eula).any { it.trim()=="eula=true" }) { "The operator must accept the Minecraft EULA in eula.txt" }
        val marker=directory.resolve(".caenis-process")
        if(Files.isRegularFile(marker)) {
            val identity=Files.readString(marker).trim().split("|")
            val pid=identity.firstOrNull()?.toLongOrNull()
            val existing=pid?.let { ProcessHandle.of(it).orElse(null) }
            val started=existing?.info()?.startInstant()?.orElse(null)?.toString()
            require(existing?.isAlive!=true || identity.getOrNull(1)!=started) {
                "This instance is still owned by a previous manager process. Stop it through RCON before starting another."
            }
        }
        val ownershipFile=FileChannel.open(directory.resolve(".caenis-owner.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)
        val ownershipLock=runCatching { ownershipFile.tryLock() }.getOrNull()
        if(ownershipLock==null) { ownershipFile.close(); throw ResponseStatusException(HttpStatus.CONFLICT,"Another manager owns this instance") }
        ownership[id]=ownershipFile to ownershipLock
        // No shell: every argument has a fixed purpose, and none comes from the request.
        val process=try {
            ProcessBuilder(settings.javaCommand,"-Xms512M","-Xmx${config.memoryMb}M","-jar",jar.toString(),"--nogui")
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        } catch(error: Exception) {
            ownership.remove(id)?.let { it.second.release(); it.first.close() }; throw error
        }
        processes[id]=process
        Files.writeString(marker,"${process.pid()}|${process.info().startInstant().orElseThrow()}")
        process.onExit().thenAccept { exited -> locks.getValue(id).withLock { cleanup(id,exited,marker) } }
    }
    private fun stop(id: String) {
        val process=processes[id] ?: return
        if(!process.isAlive) { processes.remove(id,process); return }
        process.outputStream.write("stop\n".toByteArray()); process.outputStream.flush()
        if(!process.waitFor(35,TimeUnit.SECONDS)) {
            // Do not silently kill a world that is still saving.
            throw ResponseStatusException(HttpStatus.CONFLICT,"Graceful shutdown is still running; inspect the Paper log")
        }
        val directory=Path.of(settings.root).toRealPath().resolve(configured(id).directory).toRealPath()
        cleanup(id,process,directory.resolve(".caenis-process"))
    }
    private fun cleanup(id: String,process: Process,marker: Path) {
        if(processes.remove(id,process)) {
            runCatching { Files.deleteIfExists(marker) }
            ownership.remove(id)?.let { lock -> runCatching { lock.second.release() }; lock.first.close() }
        }
    }
    @PreDestroy
    fun close() {
        // Request a normal Paper shutdown for owned children only.
        processes.values.filter { it.isAlive }.forEach { runCatching { it.outputStream.write("stop\n".toByteArray()); it.outputStream.flush() } }
    }
}
@RestController
@RequestMapping("/manager/v1/instances")
class ManagerController(private val manager: ProcessManager) {
    @GetMapping("/{id}") fun status(@PathVariable id: String)=manager.status(id)
    @PostMapping("/{id}/lifecycle") fun lifecycle(@PathVariable id: String,@RequestBody request: LifecycleInput)=manager.action(id,request.action)
}
