package com.enderstorage.overseer.fleet

import com.enderstorage.overseer.security.Secrets
import com.enderstorage.sentinel.dto.AgentConfiguration
import com.enderstorage.sentinel.dto.Heartbeat
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
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
class FleetService(private val db: JdbcTemplate, private val json: ObjectMapper, private val secrets: Secrets) {
    private fun read(rs: ResultSet) = RegisteredInstance(
        rs.getString("id"), rs.getString("name"), rs.getString("cluster_group"), rs.getString("region"), rs.getBoolean("enabled"),
        rs.getString("agent_secret"), rs.getString("rcon_host"), rs.getInt("rcon_port"), rs.getString("rcon_password"),
        rs.getString("vault_path"), rs.getString("manager_url"), rs.getString("manager_key"), rs.getTimestamp("last_seen")?.toInstant(),
        rs.getString("health")?.let { json.readValue(it, Heartbeat::class.java) },
        json.readValue(rs.getString("configuration"), AgentConfiguration::class.java), rs.getBoolean("automation_enabled"), rs.getString("automation_action")
    )
    fun get(id: String): RegisteredInstance = db.query("SELECT * FROM instances WHERE id=?", { rs, _ -> read(rs) }, id).firstOrNull()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Instance not found")
    fun all() = db.query("SELECT * FROM instances ORDER BY cluster_group,name,id", { rs, _ -> read(rs) })
    @org.springframework.transaction.annotation.Transactional
    fun create(input: InstanceInput): Pair<InstanceView, String> {
        db.queryForList("SELECT pg_advisory_xact_lock(1128351054)")
        require(input.id.matches(Regex("[a-z0-9][a-z0-9-]{2,63}"))) { "Instance ID must contain 3 to 64 lowercase letters, digits or hyphens" }
        require(input.name.isNotBlank() && input.name.length <= 100 && input.clusterGroup.length in 1..64 && input.region.length in 1..64)
        require((db.queryForObject("SELECT count(*) FROM instances", Long::class.java) ?: 0) < 256) { "Fleet capacity is 256 instances" }
        val secret = secrets.randomToken()
        val changed = db.update("INSERT INTO instances(id,name,cluster_group,region,agent_secret) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
            input.id, input.name, input.clusterGroup, input.region, secrets.encrypt(secret))
        if (changed == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "Instance ID already exists")
        return get(input.id).view() to secret
    }
    fun connections(id: String, input: ConnectionInput) {
        get(id)
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
        db.update("""UPDATE instances SET rcon_host=?,rcon_port=?,rcon_password=coalesce(?,rcon_password),
            vault_path=?,manager_url=?,manager_key=coalesce(?,manager_key) WHERE id=?""",
            input.rconHost?.takeIf { it.isNotBlank() }, input.rconPort, input.rconPassword?.let(secrets::encrypt),
            input.vaultPath?.takeIf { it.isNotBlank() }, input.managerUrl?.trimEnd('/'), input.managerKey?.let(secrets::encrypt), id)
    }
    fun settings(id: String, input: InstanceSettings) {
        get(id)
        require(input.configuration.batchSize in 1..500 && input.configuration.flushIntervalMs in 500..10000 && input.configuration.jitterMs in 0..2000)
        require(input.automationAction in listOf("kick","freeze"))
        db.update("UPDATE instances SET enabled=?,configuration=?::jsonb,automation_enabled=?,automation_action=? WHERE id=?",
            input.enabled, json.writeValueAsString(input.configuration), input.automationEnabled, input.automationAction, id)
    }
    fun rotate(id: String): String {
        get(id)
        val secret = secrets.randomToken()
        db.update("UPDATE instances SET agent_secret=?,agent_token_hash=NULL,agent_token_expires_at=NULL WHERE id=?", secrets.encrypt(secret), id)
        return secret
    }
    fun history(id: String, hours: Int) = db.queryForList("""
        SELECT date_trunc('minute',recorded_at) AS time, avg(tps) AS tps, avg(mspt) AS mspt,
        avg(heap_used)::bigint AS "heapUsed", max(heap_max) AS "heapMax", max(players) AS players
        FROM health_samples WHERE instance_id=? AND recorded_at >= now() - (? * interval '1 hour')
        GROUP BY 1 ORDER BY 1""", id, hours.coerceIn(1,72))
}
