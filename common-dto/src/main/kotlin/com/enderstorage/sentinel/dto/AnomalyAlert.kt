package com.enderstorage.sentinel.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AnomalyAlert(
    val id: String,
    @Contextual val playerId: UUID,
    val playerName: String,
    val timestampMs: Long,
    val type: AnomalyType,
    val severity: Severity,
    val confidence: Double,
    val details: String,
)

enum class AnomalyType {
    FAST_BREAK,
    XRAY,
    OTHER,
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}
