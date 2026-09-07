package com.enderstorage.overseer.fleet

import com.enderstorage.overseer.security.*
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

data class CommandRequest(val instanceId: String, val command: String? = null, val action: String? = null, val playerId: String? = null)
data class CommandEvent(val id: UUID, val status: String, val output: String = "")
data class LifecycleRequest(val action: String)

@Service
class CommandGateway(
    private val fleet: FleetService, private val pool: RconPool, private val vault: CredentialProvider,
    private val auth: AuthService, private val secrets: Secrets, private val audit: AuditService,
    private val messaging: SimpMessagingTemplate, private val json: ObjectMapper
) {
    private val workers = ThreadPoolExecutor(4,8,30,TimeUnit.SECONDS,ArrayBlockingQueue(64))
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val occupied = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun disconnect(id: String) = pool.disconnect(id)
    fun submit(user: Account, token: String, input: CommandRequest): UUID {
        require(user.role != Role.ANALYST) { "This role cannot issue commands" }
        if (input.command != null && user.role != Role.SUPERADMIN) throw ResponseStatusException(HttpStatus.FORBIDDEN,"Raw RCON requires SuperAdmin")
        fleet.get(input.instanceId)
        val preview = input.command ?: "${input.action} ${input.playerId ?: ""}"
        require(preview.toByteArray().size in 1..1446 && !preview.any { it == '\n' || it == '\r' || it == '\u0000' })
        return enqueue(user.id.toString(),input.instanceId,"RCON",preview) { emit ->
            val current = auth.authenticate(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
            val node = fleet.get(input.instanceId)
            check(node.enabled && node.status == "ONLINE") { "Instance is not reachable" }
            val command = resolve(current,node,input)
            // Vault credentials are fetched for every command, so rotations take effect immediately.
            if (node.vaultPath != null) pool.disconnect(node.id)
            pool.execute(node.id,node.rconHost ?: error("RCON host is missing"),node.rconPort,vault.password(node),command,emit)
        }
    }
    private fun resolve(user: Account, node: RegisteredInstance, input: CommandRequest): String {
        input.command?.let {
            require(user.role == Role.SUPERADMIN) { "Raw RCON requires SuperAdmin" }
            return it
        }
        require(user.role == Role.SUPERADMIN || user.role == Role.MODERATOR)
        val player = node.health?.players?.firstOrNull { it.id == input.playerId } ?: error("Player is no longer on this instance")
        require(player.name.matches(Regex("[a-zA-Z0-9_]{1,16}")))
        val uuid = UUID.fromString(player.id)
        return when(input.action) {
            "kick" -> "kick ${player.name} Removed by server moderation"
            "freeze" -> "caenis freeze $uuid"
            "unfreeze" -> "caenis unfreeze $uuid"
            "spectate" -> "gamemode spectator ${player.name}"
            else -> error("Unsupported moderation action")
        }
    }
    fun lifecycle(user: Account, token: String, id: String, action: String): UUID {
        require(user.role == Role.SUPERADMIN && action in listOf("start","stop","restart"))
        fleet.get(id)
        return enqueue(user.id.toString(),id,"LIFECYCLE",action) { _ ->
            require(auth.authenticate(token)?.role == Role.SUPERADMIN) { "Administrator session expired" }
            val node = fleet.get(id)
            require(node.enabled) { "Instance is disabled" }
            val url = node.managerUrl ?: error("Host manager is not configured")
            val key = node.managerKey?.let(secrets::decrypt) ?: error("Host manager key is missing")
            val body = json.writeValueAsString(mapOf("action" to action))
            val request = HttpRequest.newBuilder(URI.create("$url/manager/v1/instances/$id/lifecycle"))
                .timeout(Duration.ofSeconds(50)).header("Content-Type","application/json").header("X-Caenis-Manager-Key",key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build()
            val result = http.send(request,HttpResponse.BodyHandlers.ofString())
            check(result.statusCode() in 200..299) { "Host manager rejected the operation" }
            pool.disconnect(id)
            result.body().take(65536)
        }
    }
    fun automated(nodeId: String, playerId: UUID, playerName: String, action: String) {
        enqueue("automation",nodeId,"AUTOMATED", "$action $playerId") { emit ->
            val node = fleet.get(nodeId)
            require(node.enabled && node.status == "ONLINE" && node.automationEnabled)
            require(node.automationAction == action)
            require(node.health?.players?.any { it.id == playerId.toString() && it.name == playerName } == true)
            require(playerName.matches(Regex("[a-zA-Z0-9_]{1,16}")))
            if (node.vaultPath != null) pool.disconnect(node.id)
            val command = when(action) { "kick" -> "kick $playerName Automated telemetry flag"; "freeze" -> "caenis freeze $playerId"; else -> error("Invalid containment") }
            pool.execute(node.id,node.rconHost ?: error("RCON is missing"),node.rconPort,vault.password(node),command,emit)
        }
    }
    private fun enqueue(actor: String, node: String, action: String, command: String, run: ((String) -> Unit) -> String): UUID {
        if (!occupied.add(node)) throw ResponseStatusException(HttpStatus.CONFLICT, "An operation is already running on this instance")
        val id = try { audit.record(actor,node,action,command,"QUEUED") } catch(error: Exception) { occupied.remove(node); throw error }
        try {
            workers.execute {
                val emit: (String) -> Unit = { text -> messaging.convertAndSendToUser(actor,"/queue/commands",CommandEvent(id,"OUTPUT",text)) }
                try {
                    audit.record(actor,node,action,command,"DISPATCHING",correlation=id)
                    val result = run(emit)
                    audit.record(actor,node,action,command,"SUCCEEDED",result,id)
                    messaging.convertAndSendToUser(actor,"/queue/commands",CommandEvent(id,"SUCCEEDED",result))
                } catch (error: Exception) {
                    // Failure does not prove that the remote side did not apply the command.
                    val detail = "Operation failed or response was lost (${error.javaClass.simpleName}). Inspect the instance before retrying."
                    audit.record(actor,node,action,command,"UNCERTAIN",detail,id)
                    messaging.convertAndSendToUser(actor,"/queue/commands",CommandEvent(id,"UNCERTAIN",detail))
                } finally {
                    occupied.remove(node)
                    messaging.convertAndSend("/topic/changes",mapOf("kind" to "audit"))
                }
            }
        } catch(error: RejectedExecutionException) {
            occupied.remove(node)
            audit.record(actor,node,action,command,"REJECTED","Command queue is full",id)
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Command queue is full")
        }
        return id
    }
    @PreDestroy fun shutdown() { workers.shutdown() }
}
