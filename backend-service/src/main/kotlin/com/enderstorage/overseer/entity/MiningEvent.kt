package com.enderstorage.overseer.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

// Rows are written by MiningEventRepository.insertIfAbsent; the generated PostGIS `position` column is not mapped.
@Entity
@Table(name = "mining_events")
class MiningEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = 36)
    var eventId: String? = null,
    @Column(nullable = false, length = 64)
    var instanceId: String = "",
    @Column(nullable = false)
    var playerId: UUID = UUID(0, 0),
    @Column(nullable = false, length = 32)
    var playerName: String = "",
    @Column(nullable = false, length = 64)
    var world: String = "",
    @Column(nullable = false)
    var x: Int = 0,
    @Column(nullable = false)
    var y: Int = 0,
    @Column(nullable = false)
    var z: Int = 0,
    @Column(nullable = false, length = 64)
    var blockType: String = "",
    @Column(length = 64)
    var toolUsed: String? = null,
    @Column(nullable = false)
    var toolEfficiencyLevel: Int = 0,
    @Column(nullable = false)
    var hasHaste: Boolean = false,
    @Column(nullable = false)
    var hasMiningFatigue: Boolean = false,
    @Column(nullable = false)
    var isExposed: Boolean = false,
    var breakDeltaMs: Int? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var topologyKnown: Boolean = true,
    @JdbcTypeCode(SqlTypes.JSON)
    var rawEvent: String? = null
)
