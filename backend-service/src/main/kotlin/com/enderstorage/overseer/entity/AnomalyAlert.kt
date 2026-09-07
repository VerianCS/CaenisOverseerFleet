package com.enderstorage.overseer.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AlertType {
    FAST_MINING_TEMPORAL_BREACH,
    TOPOLOGICAL_OCCLUSION_XRAY
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Entity
@Table(
    name = "anomaly_alerts",
    indexes = [
        Index(name = "idx_alert_player", columnList = "playerId, createdAt"),
        Index(name = "idx_alert_severity", columnList = "severity, createdAt")
    ]
)
class AnomalyAlert(
    @Column(nullable = false, length = 64)
    var instanceId: String = "legacy",
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var playerId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 32)
    var playerName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var alertType: AlertType = AlertType.FAST_MINING_TEMPORAL_BREACH,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var severity: Severity = Severity.LOW,

    @Column(nullable = false, columnDefinition = "TEXT")
    var diagnosticData: String = "",

    @Column(nullable = false)
    var world: String = "",

    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
