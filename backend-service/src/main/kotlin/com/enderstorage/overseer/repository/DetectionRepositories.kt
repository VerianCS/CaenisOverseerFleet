package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface BaselineModelRepository : JpaRepository<BaselineModel, BaselineModelId> {
    fun findAllByOrderByTrainedAtDesc(page: Pageable): List<BaselineModel>

    @Modifying
    @Query(value = """INSERT INTO baseline_models(instance_id, player_id, world, samples, occluded, trained_at)
        SELECT instance_id, player_id, world, count(*), count(*) FILTER (WHERE NOT is_exposed), now()
        FROM mining_events WHERE instance_id = :instanceId AND created_at BETWEEN :from AND :until AND topology_known
        AND (block_type LIKE '%DIAMOND_ORE' OR block_type LIKE '%GOLD_ORE' OR block_type LIKE '%EMERALD_ORE' OR block_type = 'ANCIENT_DEBRIS')
        GROUP BY instance_id, player_id, world HAVING count(*) >= 100
        ON CONFLICT(instance_id, player_id, world) DO UPDATE SET samples = excluded.samples, occluded = excluded.occluded, trained_at = excluded.trained_at""",
        nativeQuery = true)
    fun train(instanceId: String, from: Instant, until: Instant): Int
}

interface InterventionLimitRepository : JpaRepository<InterventionLimit, InterventionLimitId> {
    // Database clock on both sides, matching the now() written by claim().
    @Query(value = "SELECT count(*) FROM intervention_limits WHERE instance_id = :instanceId AND last_action > now() - interval '1 minute'", nativeQuery = true)
    fun countLastMinute(instanceId: String): Long

    // Claims the per-player slot only if the last action is older than five minutes; returns 0 otherwise.
    @Modifying
    @Query(value = """INSERT INTO intervention_limits(instance_id, player_id, last_action) VALUES (:instanceId, :playerId, now())
        ON CONFLICT(instance_id, player_id) DO UPDATE SET last_action = now()
        WHERE intervention_limits.last_action < now() - interval '5 minutes'""", nativeQuery = true)
    fun claim(instanceId: String, playerId: UUID): Int

    @Transactional
    @Modifying
    @Query("delete from InterventionLimit l where l.lastAction < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}
