package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.AlertType
import com.enderstorage.overseer.entity.AnomalyAlert
import com.enderstorage.overseer.entity.Severity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AnomalyAlertRepository : JpaRepository<AnomalyAlert, Long> {
    fun countBySeverity(severity: Severity): Long

    fun countByInstanceIdAndPlayerIdAndAlertTypeAndCreatedAtAfter(instanceId: String, playerId: UUID, alertType: AlertType, after: Instant): Long

    fun countByInstanceIdAndPlayerIdAndAlertTypeAndSeverityAndCreatedAtAfter(
        instanceId: String, playerId: UUID, alertType: AlertType, severity: Severity, after: Instant
    ): Long

    // includeIdentity=false keeps analysts from using real names/UUIDs as a search oracle.
    @Query(value = """SELECT id, instance_id AS "instanceId", player_id AS "playerId", player_name AS "playerName",
        alert_type AS "alertType", severity, diagnostic_data AS "diagnosticData", world, x, y, z, created_at AS "createdAt"
        FROM anomaly_alerts WHERE (CAST(:instanceId AS text) IS NULL OR instance_id = :instanceId) AND
        (alert_type ILIKE :term OR diagnostic_data ILIKE :term OR concat(x, ' ', y, ' ', z) ILIKE :term
         OR (:includeIdentity AND (player_name ILIKE :term OR CAST(player_id AS text) ILIKE :term)))
        ORDER BY created_at DESC, id DESC LIMIT :limit""", nativeQuery = true)
    fun search(instanceId: String?, term: String, includeIdentity: Boolean, limit: Int): List<Map<String, Any?>>
}
