package com.enderstorage.overseer.entity

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "mining_events",
    indexes = [
        Index(name = "idx_mining_coords", columnList = "world, x, z, createdAt"),
        Index(name = "idx_mining_player", columnList = "playerId, createdAt"),
    ],
)
class MiningEvent {

    @Column(nullable = false, length = 64)
    var instanceId: String = "legacy"

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var playerId: UUID? = null

    @Column(nullable = false, length = 32)
    var playerName: String? = null

    @Column(nullable = false, length = 64)
    var world: String? = null

    @Column(nullable = false)
    var x: Int = 0

    @Column(nullable = false)
    var y: Int = 0

    @Column(nullable = false)
    var z: Int = 0

    @Column(nullable = false, length = 64)
    var blockType: String? = null

    @Column(length = 64)
    var toolUsed: String? = null

    var toolEfficiencyLevel: Int = 0
    var hasHaste: Boolean = false
    var hasMiningFatigue: Boolean = false

    @Column(nullable = false)
    var isExposed: Boolean = false

    var breakDeltaMs: Int? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()

    constructor() {
        // JPA requires a no-arg constructor
    }

    private constructor(
        playerId: UUID,
        playerName: String,
        world: String,
        x: Int,
        y: Int,
        z: Int,
        blockType: String,
        toolUsed: String?,
        toolEfficiencyLevel: Int,
        hasHaste: Boolean,
        hasMiningFatigue: Boolean,
        isExposed: Boolean,
        breakDeltaMs: Int?,
        createdAt: Instant,
    ) {
        this.playerId = playerId
        this.playerName = playerName
        this.world = world
        this.x = x
        this.y = y
        this.z = z
        this.blockType = blockType
        this.toolUsed = toolUsed
        this.toolEfficiencyLevel = toolEfficiencyLevel
        this.hasHaste = hasHaste
        this.hasMiningFatigue = hasMiningFatigue
        this.isExposed = isExposed
        this.breakDeltaMs = breakDeltaMs
        this.createdAt = createdAt
    }

    companion object {
        fun fromDto(dto: BlockBreakTelemetry): MiningEvent = MiningEvent(
            playerId = dto.playerId,
            playerName = dto.playerName,
            world = dto.world,
            x = dto.x,
            y = dto.y,
            z = dto.z,
            blockType = dto.blockType,
            toolUsed = dto.toolUsed,
            toolEfficiencyLevel = dto.toolEfficiencyLevel,
            hasHaste = dto.hasHaste,
            hasMiningFatigue = dto.hasMiningFatigue,
            isExposed = dto.isExposedToAirOrCave,
            breakDeltaMs = dto.breakDeltaMs,
            createdAt = Instant.ofEpochMilli(dto.timestampMs),
        ).also { it.instanceId = dto.instanceId }
    }
}
