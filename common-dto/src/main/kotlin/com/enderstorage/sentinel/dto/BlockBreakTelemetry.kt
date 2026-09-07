@file:UseSerializers(com.enderstorage.sentinel.dto.serializers.UUIDSerializer::class)
package com.enderstorage.sentinel.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
data class BlockBreakTelemetry(
    val playerId: UUID,
    val playerName: String,
    val timestampMs: Long,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val blockType: String,
    val toolUsed: String? = null,
    val toolEfficiencyLevel: Int = 0,
    val hasHaste: Boolean = false,
    val hasMiningFatigue: Boolean = false,
    val isExposedToAirOrCave: Boolean,
    // Measured from block damage start, never between unrelated breaks.
    val breakDeltaMs: Int? = null,
    val instanceId: String,
    val eventId: String = UUID.randomUUID().toString(),
    val hasteLevel: Int = 0,
    val fatigueLevel: Int = 0,
    val underwater: Boolean = false,
    val onGround: Boolean = true,
    val aquaAffinity: Boolean = false,
    val canHarvest: Boolean = true,
    val gameMode: String = "SURVIVAL",
    val exposureMask: Int = 0,
    val topologyKnown: Boolean = true,
    val timingReliable: Boolean = false,
    // Native reference only; all comparisons and diagnostic decisions remain in the core.
    val nativeBreakSpeed: Double? = null
)
