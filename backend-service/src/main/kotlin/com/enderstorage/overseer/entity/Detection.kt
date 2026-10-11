package com.enderstorage.overseer.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class BaselineModelId(val instanceId: String = "", val playerId: UUID = UUID(0, 0), val world: String = "") : Serializable

@Entity
@IdClass(BaselineModelId::class)
@Table(name = "baseline_models")
class BaselineModel(
    @Id
    var instanceId: String = "",
    @Id
    var playerId: UUID = UUID(0, 0),
    @Id
    var world: String = "",
    @Column(nullable = false)
    var samples: Long = 0,
    @Column(nullable = false)
    var occluded: Long = 0,
    @Column(nullable = false)
    var trainedAt: Instant = Instant.now()
)

data class InterventionLimitId(val instanceId: String = "", val playerId: UUID = UUID(0, 0)) : Serializable

@Entity
@IdClass(InterventionLimitId::class)
@Table(name = "intervention_limits")
class InterventionLimit(
    @Id
    var instanceId: String = "",
    @Id
    var playerId: UUID = UUID(0, 0),
    @Column(nullable = false)
    var lastAction: Instant = Instant.now()
)
