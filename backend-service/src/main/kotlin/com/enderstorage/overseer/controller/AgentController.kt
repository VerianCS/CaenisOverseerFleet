package com.enderstorage.overseer.controller

import com.enderstorage.overseer.entity.HealthSample
import com.enderstorage.overseer.entity.IngressReceiptId
import com.enderstorage.overseer.fleet.FleetService
import com.enderstorage.overseer.repository.HealthSampleRepository
import com.enderstorage.overseer.repository.IngressReceiptRepository
import com.enderstorage.overseer.repository.InstanceRepository
import com.enderstorage.overseer.security.Secrets
import com.enderstorage.overseer.service.MiningTelemetryService
import com.enderstorage.overseer.service.LiveChanges
import com.enderstorage.sentinel.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.Base64

@RestController
@RequestMapping("/api/v1/agent/{id}")
class AgentController(
    private val fleet: FleetService, private val secrets: Secrets, private val json: ObjectMapper,
    private val instances: InstanceRepository, private val receipts: IngressReceiptRepository,
    private val samples: HealthSampleRepository, private val mining: MiningTelemetryService, private val changes: LiveChanges
) {
    @PostMapping("/{purpose}")
    @Transactional
    fun receive(@PathVariable id: String, @PathVariable purpose: String, request: HttpServletRequest): Any {
        if (purpose !in listOf("enroll","heartbeat","mining","config")) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val bytes = request.inputStream.readNBytes(2 * 1024 * 1024 + 1)
        if (bytes.size > 2 * 1024 * 1024) throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE)
        val timestamp = request.getHeader("X-Caenis-Timestamp") ?: unauthorized()
        val nonce = request.getHeader("X-Caenis-Nonce") ?: unauthorized()
        val signature = request.getHeader("X-Caenis-Signature") ?: unauthorized()
        val sentAt = timestamp.toLongOrNull() ?: unauthorized()
        if (sentAt > System.currentTimeMillis() + 60000 || sentAt < System.currentTimeMillis() - (if(purpose == "mining") 86400000 else 60000)) unauthorized()
        val nonceId = runCatching { UUID.fromString(nonce) }.getOrElse { unauthorized() }
        val locked = instances.lockById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Instance not found")
        val node = fleet.get(id)
        if (!node.enabled) unauthorized()
        val expected = RequestSigning.sign(secrets.decrypt(node.agentSecret),id,purpose,timestamp,nonce,bytes)
        if (!RequestSigning.equal(signature,expected)) unauthorized()
        if (purpose in listOf("heartbeat","config")) {
            val token = request.getHeader("X-Caenis-Token") ?: unauthorized()
            val stored = instances.activeTokenHash(id)
            if (stored == null || !RequestSigning.equal(stored,secrets.hash(token))) unauthorized()
        }
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
        val inserted = receipts.insertIfAbsent(id,nonceId,purpose,hash)
        if (inserted == 0) {
            val previous = receipts.findByIdOrNull(IngressReceiptId(id,nonceId)) ?: unauthorized()
            if (previous.purpose != purpose || previous.bodyHash != hash) unauthorized()
            if (purpose == "enroll") throw ResponseStatusException(HttpStatus.CONFLICT, "Enrollment already processed; use a new nonce")
            return AgentReply(node.configuration)
        }
        return when(purpose) {
            "enroll" -> {
                val token = secrets.randomToken()
                val expires = Instant.now().plusSeconds(900)
                locked.agentTokenHash = secrets.hash(token)
                locked.agentTokenExpiresAt = expires
                Enrollment(token,expires.toEpochMilli(),node.configuration)
            }
            "heartbeat" -> {
                val health = json.readValue<Heartbeat>(bytes)
                require(health.tps.size == 3 && health.tps.all { it.isFinite() && it in 0.0..20.1 })
                require(health.mspt.isFinite() && health.mspt in 0.0..60000.0 && health.heapUsed >= 0 && health.heapMax > 0 && health.heapUsed <= health.heapMax)
                require(health.heapCommitted in 0..health.heapMax && health.loadedChunks >= 0 && health.entities >= 0 && health.queueDepth >= 0)
                require(health.players.size <= 2000 && health.worlds.size <= 100 && health.worlds.all { it.length in 1..64 })
                require(health.players.all { it.name.matches(Regex("[a-zA-Z0-9_]{1,16}")) && it.world.length in 1..64 && it.ping >= 0 && runCatching { UUID.fromString(it.id) }.isSuccess })
                require(health.sampledAt in (System.currentTimeMillis()-15000)..(System.currentTimeMillis()+60000)) { "Heartbeat snapshot is stale" }
                locked.lastSeen = Instant.now()
                locked.health = json.writeValueAsString(health)
                samples.save(HealthSample(instanceId=id,tps=health.tps.first(),mspt=health.mspt,heapUsed=health.heapUsed,heapMax=health.heapMax,players=health.players.size))
                changes.changed("fleet")
                AgentReply(node.configuration)
            }
            "mining" -> {
                val batch = decodeMining(bytes,id)
                AgentReply(node.configuration,mining.processTelemetryBatch(batch))
            }
            else -> AgentReply(node.configuration)
        }
    }
    private fun decodeMining(bytes: ByteArray,id: String): List<BlockBreakTelemetry> {
        val batch = json.readValue<List<BlockBreakTelemetry>>(bytes)
        require(batch.size in 1..500)
        batch.forEach {
            val toolUsed = it.toolUsed
            val breakDeltaMs = it.breakDeltaMs
            val nativeBreakSpeed = it.nativeBreakSpeed

            require(it.instanceId == id && runCatching { UUID.fromString(it.eventId) }.isSuccess)
            require(it.playerName.matches(Regex("[a-zA-Z0-9_]{1,16}")) && it.world.length in 1..64)
            require(it.blockType.matches(Regex("[A-Z_]{1,64}")) && (toolUsed == null || toolUsed.matches(Regex("[A-Z_]{1,64}"))))
            require(it.x in -30000000..30000000 && it.z in -30000000..30000000 && it.y in -4096..4096)
            require(it.timestampMs in (System.currentTimeMillis()-86400000)..(System.currentTimeMillis()+60000))
            require(it.toolEfficiencyLevel in 0..255 && it.hasteLevel in 0..256 && it.fatigueLevel in 0..256)
            require(breakDeltaMs == null || breakDeltaMs in 0..3600000)
            require(nativeBreakSpeed == null || (nativeBreakSpeed.isFinite() && nativeBreakSpeed >= 0))
        }
        return batch
    }
    private fun unauthorized(): Nothing = throw ResponseStatusException(HttpStatus.UNAUTHORIZED,"Agent authentication rejected")
}
