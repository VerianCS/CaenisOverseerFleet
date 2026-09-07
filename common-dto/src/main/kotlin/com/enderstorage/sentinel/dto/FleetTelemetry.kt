package com.enderstorage.sentinel.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerSnapshot(val id: String, val name: String, val ping: Int, val world: String)

@Serializable
data class Heartbeat(
    val tps: List<Double>,
    val mspt: Double,
    val heapUsed: Long,
    val heapCommitted: Long,
    val heapMax: Long,
    val loadedChunks: Int,
    val entities: Int,
    val players: List<PlayerSnapshot>,
    val worlds: List<String>,
    val queueDepth: Int,
    val droppedEvents: Long,
    val spoolBytes: Long,
    val version: String,
    val sampledAt: Long
)

@Serializable
data class AgentConfiguration(
    val enabled: Boolean = true,
    val debugSampling: Boolean = false,
    val batchSize: Int = 100,
    val flushIntervalMs: Long = 1000,
    val jitterMs: Int = 100
)

@Serializable
data class Enrollment(val token: String, val expiresAt: Long, val configuration: AgentConfiguration)

@Serializable
data class AgentReply(val configuration: AgentConfiguration, val accepted: Int = 0)
