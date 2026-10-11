package com.enderstorage.overseer.fleet

import com.enderstorage.overseer.entity.Instance
import com.enderstorage.overseer.repository.HealthSampleRepository
import com.enderstorage.overseer.repository.InstanceRepository
import com.enderstorage.overseer.security.Secrets
import com.enderstorage.sentinel.dto.AgentConfiguration
import com.enderstorage.sentinel.dto.Heartbeat
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class RegisteredInstance(
    val id: String, val name: String, val clusterGroup: String, val region: String, val enabled: Boolean,
    val agentSecret: String, val rconHost: String?, val rconPort: Int, val rconPassword: String?,
    val vaultPath: String?, val managerUrl: String?, val managerKey: String?, val lastSeen: Instant?,
    val health: Heartbeat?, val configuration: AgentConfiguration, val automationEnabled: Boolean, val automationAction: String
) {
    val status: String get() = if (!enabled) "DISABLED" else if (lastSeen == null) "AWAITING_AGENT"
        else if (lastSeen.isBefore(Instant.now().minusSeconds(15))) "UNREACHABLE" else "ONLINE"
    fun view() = InstanceView(id, name, clusterGroup, region, enabled, status, lastSeen, health,
        rconHost != null && (rconPassword != null || vaultPath != null), managerUrl != null, configuration, automationEnabled, automationAction,
        rconHost,rconPort,vaultPath,managerUrl)
}
data class InstanceView(
    val id: String, val name: String, val clusterGroup: String, val region: String, val enabled: Boolean,
    val status: String, val lastSeen: Instant?, val health: Heartbeat?, val rconConfigured: Boolean,
    val lifecycleConfigured: Boolean, val configuration: AgentConfiguration, val automationEnabled: Boolean, val automationAction: String,
    val rconHost: String?,val rconPort: Int,val vaultPath: String?,val managerUrl: String?
)
data class InstanceInput(val id: String, val name: String, val clusterGroup: String = "default", val region: String = "local")
data class ConnectionInput(
    val rconHost: String? = null, val rconPort: Int = 25575, val rconPassword: String? = null,
    val vaultPath: String? = null, val managerUrl: String? = null, val managerKey: String? = null
)
data class InstanceSettings(
    val enabled: Boolean, val configuration: AgentConfiguration,
    val automationEnabled: Boolean = false, val automationAction: String = "kick"
)

@Service
class FleetService(
    private val instances: InstanceRepository, private val samples: HealthSampleRepository,
    private val json: ObjectMapper, private val secrets: Secrets
) {
    private fun read(i: Instance) = RegisteredInstance(
        i.id, i.name, i.clusterGroup, i.region, i.enabled, i.agentSecret, i.rconHost, i.rconPort, i.rconPassword,
        i.vaultPath, i.managerUrl, i.managerKey, i.lastSeen, i.health?.let { json.readValue(it, Heartbeat::class.java) },
        json.readValue(i.configuration, AgentConfiguration::class.java), i.automationEnabled, i.automationAction
    )
    private fun entity(id: String) = instances.findByIdOrNull(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Instance not found")
    fun get(id: String): RegisteredInstance = read(entity(id))
    fun all() = instances.findAll(Sort.by("clusterGroup", "name", "id")).map(::read)
    @Transactional
    fun create(input: InstanceInput): Pair<InstanceView, String> {
        instances.lockRegistry()
        require(input.id.matches(Regex("[a-z0-9][a-z0-9-]{2,63}"))) { "Instance ID must contain 3 to 64 lowercase letters, digits or hyphens" }
        require(input.name.isNotBlank() && input.name.length <= 100 && input.clusterGroup.length in 1..64 && input.region.length in 1..64)
        require(instances.count() < 256) { "Fleet capacity is 256 instances" }
        if (instances.existsById(input.id)) throw ResponseStatusException(HttpStatus.CONFLICT, "Instance ID already exists")
        val secret = secrets.randomToken()
        val created = instances.save(Instance(id = input.id, name = input.name, clusterGroup = input.clusterGroup,
            region = input.region, agentSecret = secrets.encrypt(secret)))
        return read(created).view() to secret
    }
    @Transactional
    fun connections(id: String, input: ConnectionInput) {
        val instance = entity(id)
        require(input.rconPort in 1..65535)
        input.rconHost?.let { require(it.length in 1..253 && it.matches(Regex("[a-zA-Z0-9.:-]+"))) { "Invalid RCON host" } }
        input.rconPassword?.let { require(it.toByteArray().size in 1..1024 && !it.contains('\u0000')) }
        input.vaultPath?.let { require(it.matches(Regex("[a-zA-Z0-9/_-]{1,200}")) && ".." !in it) }
        input.managerUrl?.let {
            val uri = java.net.URI(it)
            require(uri.scheme in listOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Manager URL must be an HTTP(S) origin" }
            require(uri.path.isNullOrEmpty() || uri.path == "/")
        }
        input.managerKey?.let { require(it.length in 32..256) }
        instance.rconHost = input.rconHost?.takeIf { it.isNotBlank() }
        instance.rconPort = input.rconPort
        input.rconPassword?.let { instance.rconPassword = secrets.encrypt(it) }
        instance.vaultPath = input.vaultPath?.takeIf { it.isNotBlank() }
        instance.managerUrl = input.managerUrl?.trimEnd('/')
        input.managerKey?.let { instance.managerKey = secrets.encrypt(it) }
    }
    @Transactional
    fun settings(id: String, input: InstanceSettings) {
        val instance = entity(id)
        require(input.configuration.batchSize in 1..500 && input.configuration.flushIntervalMs in 500..10000 && input.configuration.jitterMs in 0..2000)
        require(input.automationAction in listOf("kick","freeze"))
        instance.enabled = input.enabled
        instance.configuration = json.writeValueAsString(input.configuration)
        instance.automationEnabled = input.automationEnabled
        instance.automationAction = input.automationAction
    }
    @Transactional
    fun rotate(id: String): String {
        val instance = entity(id)
        val secret = secrets.randomToken()
        instance.agentSecret = secrets.encrypt(secret)
        instance.agentTokenHash = null
        instance.agentTokenExpiresAt = null
        return secret
    }
    fun history(id: String, hours: Int) = samples.history(id, hours.coerceIn(1,72))
}
