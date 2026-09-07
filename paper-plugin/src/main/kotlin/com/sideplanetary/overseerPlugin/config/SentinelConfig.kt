package com.sideplanetary.overseerPlugin.config

import org.bukkit.configuration.file.FileConfiguration
import java.net.URI

data class SentinelConfig(
    val instanceId: String,val backendUrl: String,val telemetryUrl: String,val secret: String,
    val queueCapacity: Int,val spoolMaxBytes: Long,val requestTimeoutSeconds: Long
) {
    companion object {
        fun load(config: FileConfiguration): SentinelConfig {
            val id = System.getenv("CAENIS_INSTANCE_ID") ?: config.getString("instance.id").orEmpty()
            val key = System.getenv("CAENIS_AGENT_SECRET") ?: config.getString("instance.secret").orEmpty()
            val backend = (System.getenv("CAENIS_BACKEND_URL") ?: config.getString("backend.url").orEmpty()).trimEnd('/')
            val telemetry = config.getString("backend.telemetry-url")?.takeIf { it.isNotBlank() } ?: "$backend/api/v1/agent/$id/mining"
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{2,63}"))) { "Configure instance.id from the fleet console" }
            require(key.length in 32..256 && !key.startsWith("CHANGE")) { "Configure the agent secret from the fleet console" }
            for(url in listOf(backend,telemetry)) {
                val uri=URI.create(url)
                require(uri.scheme in listOf("http","https") && uri.host != null && uri.userInfo == null) { "Use a valid backend URL" }
            }
            return SentinelConfig(id,backend,telemetry,key,config.getInt("collector.queue-capacity",8192).coerceIn(512,65536),
                config.getLong("collector.spool-max-mb",128).coerceIn(8,1024)*1024*1024,
                config.getLong("backend.timeout-seconds",3).coerceIn(1,10))
        }
    }
}
