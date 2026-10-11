package com.enderstorage.overseer.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "instances")
class Instance(
    @Id
    var id: String = "",
    @Column(nullable = false, length = 100)
    var name: String = "",
    @Column(nullable = false, length = 64)
    var clusterGroup: String = "",
    @Column(nullable = false, length = 64)
    var region: String = "",
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(nullable = false)
    var agentSecret: String = "",
    var agentTokenHash: String? = null,
    var agentTokenExpiresAt: Instant? = null,
    var rconHost: String? = null,
    @Column(nullable = false)
    var rconPort: Int = 25575,
    var rconPassword: String? = null,
    var vaultPath: String? = null,
    var managerUrl: String? = null,
    var managerKey: String? = null,
    var lastSeen: Instant? = null,
    // JSON documents stay as text; FleetService owns their (de)serialization.
    @JdbcTypeCode(SqlTypes.JSON)
    var health: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var configuration: String = "{}",
    @Column(nullable = false)
    var automationEnabled: Boolean = false,
    @Column(nullable = false, length = 10)
    var automationAction: String = "kick",
    @Column(insertable = false, updatable = false)
    var createdAt: Instant? = null
)

@Entity
@Table(name = "health_samples")
class HealthSample(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, length = 64)
    var instanceId: String = "",
    @Column(insertable = false, updatable = false)
    var recordedAt: Instant? = null,
    @Column(nullable = false)
    var tps: Double = 0.0,
    @Column(nullable = false)
    var mspt: Double = 0.0,
    @Column(nullable = false)
    var heapUsed: Long = 0,
    @Column(nullable = false)
    var heapMax: Long = 0,
    @Column(nullable = false)
    var players: Int = 0
)

data class IngressReceiptId(val instanceId: String = "", val nonce: UUID = UUID(0, 0)) : Serializable

@Entity
@IdClass(IngressReceiptId::class)
@Table(name = "ingress_receipts")
class IngressReceipt(
    @Id
    var instanceId: String = "",
    @Id
    var nonce: UUID = UUID(0, 0),
    @Column(nullable = false, length = 16)
    var purpose: String = "",
    @Column(nullable = false)
    var bodyHash: String = "",
    @Column(insertable = false, updatable = false)
    var receivedAt: Instant? = null
)
