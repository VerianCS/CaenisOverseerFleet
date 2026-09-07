package com.enderstorage.sentinel.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PlayerMovementTelemetry(
    @Contextual val playerId: UUID,
    val playerName: String,
    val timestampMs: Long,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)
