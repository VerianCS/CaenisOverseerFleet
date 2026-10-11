package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.MiningEvent
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface MiningEventRepository : JpaRepository<MiningEvent, Long> {
    // ON CONFLICT keeps ingest idempotent per eventId across retried batches.
    @Modifying
    @Query(value = """INSERT INTO mining_events(event_id, instance_id, player_id, player_name, world, x, y, z, block_type,
        tool_used, tool_efficiency_level, has_haste, has_mining_fatigue, is_exposed, break_delta_ms, created_at, topology_known, raw_event)
        VALUES (:eventId, :instanceId, :playerId, :playerName, :world, :x, :y, :z, :blockType, :toolUsed, :toolEfficiencyLevel,
        :hasHaste, :hasMiningFatigue, :isExposed, :breakDeltaMs, :createdAt, :topologyKnown, CAST(:rawEvent AS jsonb))
        ON CONFLICT(event_id) DO NOTHING""", nativeQuery = true)
    fun insertIfAbsent(
        eventId: String, instanceId: String, playerId: UUID, playerName: String, world: String, x: Int, y: Int, z: Int,
        blockType: String, toolUsed: String?, toolEfficiencyLevel: Int, hasHaste: Boolean, hasMiningFatigue: Boolean,
        isExposed: Boolean, breakDeltaMs: Int?, createdAt: Instant, topologyKnown: Boolean, rawEvent: String
    ): Int

    @Query("""select e.isExposed from MiningEvent e where e.playerId = :playerId and e.instanceId = :instanceId and e.world = :world
        and e.topologyKnown = true and e.createdAt between :from and :until
        and (e.blockType like '%DIAMOND_ORE' or e.blockType like '%GOLD_ORE' or e.blockType like '%EMERALD_ORE' or e.blockType = 'ANCIENT_DEBRIS')
        order by e.createdAt desc, e.id desc""")
    fun recentValuableExposure(playerId: UUID, instanceId: String, world: String, from: Instant, until: Instant, page: Pageable): List<Boolean>

    @Query(value = """SELECT id, player_id AS "playerId", player_name AS "playerName", block_type AS "blockType",
        x, y, z, is_exposed AS "isExposed", created_at AS timestamp, 1 AS count FROM mining_events
        WHERE instance_id = :instanceId AND world = :world AND ST_Intersects(position, ST_MakeEnvelope(:minX, :minZ, :maxX, :maxZ, 0))
        AND created_at >= now() - (:minutes * interval '1 minute')
        ORDER BY created_at DESC, id DESC LIMIT :limit""", nativeQuery = true)
    fun mapPoints(instanceId: String, world: String, minX: Int, minZ: Int, maxX: Int, maxZ: Int, minutes: Int, limit: Int): List<Map<String, Any?>>

    // Grid keys are computed once in the subquery so GROUP BY does not depend on repeated bind parameters.
    @Query(value = """SELECT min(id) AS id, 'cluster' AS "playerName", 'CLUSTER' AS "blockType",
        cx * :cell AS x, CAST(avg(y) AS int) AS y, cz * :cell AS z, bool_and(is_exposed) AS "isExposed",
        max(created_at) AS timestamp, count(*) AS count
        FROM (SELECT id, y, is_exposed, created_at, floor(x / :cell) AS cx, floor(z / :cell) AS cz FROM mining_events
            WHERE instance_id = :instanceId AND world = :world AND ST_Intersects(position, ST_MakeEnvelope(:minX, :minZ, :maxX, :maxZ, 0))
            AND created_at >= now() - (:minutes * interval '1 minute')) AS scoped
        GROUP BY cx, cz ORDER BY max(created_at) DESC LIMIT :limit""", nativeQuery = true)
    fun mapClusters(instanceId: String, world: String, minX: Int, minZ: Int, maxX: Int, maxZ: Int, minutes: Int, limit: Int, cell: Int): List<Map<String, Any?>>

    fun countByCreatedAtAfter(after: Instant): Long

    @Transactional
    @Modifying
    @Query("delete from MiningEvent e where e.createdAt < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}
