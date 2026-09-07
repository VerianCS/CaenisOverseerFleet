package com.enderstorage.overseer.dto

import com.enderstorage.overseer.entity.AlertType
import com.enderstorage.overseer.entity.AnomalyAlert
import com.enderstorage.overseer.entity.MiningEvent
import com.enderstorage.overseer.entity.Severity
import java.time.Instant
import java.util.UUID

/**
 * Lightweight spatial coordinate node for 2D/3D map rendering
 */
data class MapMarkerDTO(
    val id: Long,
    val playerName: String,
    val blockType: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val isExposed: Boolean,
    val timestamp: Instant
) {
    companion object {
        fun from(event: MiningEvent) = MapMarkerDTO(
            id = event.id ?: 0L,
            playerName = event.playerName ?: "Player Name Unknow",
            blockType = event.blockType ?: "Block Type Unknow",
            x = event.x,
            y = event.y,
            z = event.z,
            isExposed = event.isExposed,
            timestamp = event.createdAt
        )
    }
}

/**
 * Tactical alert payload for threat log feeds
 */
data class ThreatAlertDTO(
    val id: Long,
    val playerId: UUID,
    val playerName: String,
    val alertType: AlertType,
    val severity: Severity,
    val diagnosticData: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(alert: AnomalyAlert) = ThreatAlertDTO(
            id = alert.id ?: 0L,
            playerId = alert.playerId,
            playerName = alert.playerName,
            alertType = alert.alertType,
            severity = alert.severity,
            diagnosticData = alert.diagnosticData,
            world = alert.world,
            x = alert.x,
            y = alert.y,
            z = alert.z,
            createdAt = alert.createdAt
        )
    }
}

/**
 * Summary metrics for dashboard status counters
 */
data class OverviewStatsDTO(
    val totalEventsLogged: Long,
    val activeThreatsCount: Long,
    val criticalThreatsCount: Long
)