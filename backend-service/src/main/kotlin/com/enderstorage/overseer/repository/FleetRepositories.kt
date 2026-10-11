package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface InstanceRepository : JpaRepository<Instance, String> {
    // SELECT ... FOR UPDATE: one agent request per instance at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instance i where i.id = :id")
    fun lockById(id: String): Instance?

    // Transaction-scoped advisory lock serializing fleet registration (capacity check + insert).
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(1128351054)) AS registry", nativeQuery = true)
    fun lockRegistry(): Int

    @Query("select i.agentTokenHash from Instance i where i.id = :id and i.agentTokenExpiresAt > current_timestamp")
    fun activeTokenHash(id: String): String?
}

interface HealthSampleRepository : JpaRepository<HealthSample, Long> {
    @Query(value = """SELECT date_trunc('minute', recorded_at) AS time, avg(tps) AS tps, avg(mspt) AS mspt,
        CAST(avg(heap_used) AS bigint) AS "heapUsed", max(heap_max) AS "heapMax", max(players) AS players
        FROM health_samples WHERE instance_id = :instanceId AND recorded_at >= now() - (:hours * interval '1 hour')
        GROUP BY 1 ORDER BY 1""", nativeQuery = true)
    fun history(instanceId: String, hours: Int): List<Map<String, Any?>>

    @Transactional
    @Modifying
    @Query("delete from HealthSample h where h.recordedAt < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}

interface IngressReceiptRepository : JpaRepository<IngressReceipt, IngressReceiptId> {
    @Modifying
    @Query(value = """INSERT INTO ingress_receipts(instance_id, nonce, purpose, body_hash)
        VALUES (:instanceId, :nonce, :purpose, :bodyHash) ON CONFLICT DO NOTHING""", nativeQuery = true)
    fun insertIfAbsent(instanceId: String, nonce: UUID, purpose: String, bodyHash: String): Int

    @Transactional
    @Modifying
    @Query("delete from IngressReceipt r where r.receivedAt < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}

interface AuditEventRepository : JpaRepository<AuditEvent, Long> {
    @Query(value = """SELECT id, correlation_id AS "correlationId", actor, instance_id AS "instanceId", action, command, outcome, detail,
        created_at AS "createdAt" FROM audit_events
        WHERE id < :before AND (CAST(:instanceId AS text) IS NULL OR instance_id = :instanceId)
        AND (actor ILIKE :term OR command ILIKE :term OR outcome ILIKE :term) ORDER BY id DESC LIMIT 100""", nativeQuery = true)
    fun search(before: Long, instanceId: String?, term: String): List<Map<String, Any?>>

    fun findByCorrelationIdAndActorOrderById(correlationId: UUID, actor: String): List<AuditEvent>
    fun countByActorAndOutcome(actor: String, outcome: String): Long
}
