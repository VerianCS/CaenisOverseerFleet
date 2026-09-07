package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.AnomalyAlert
import com.enderstorage.overseer.entity.Severity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AnomalyAlertRepository : JpaRepository<AnomalyAlert, Long> {

    fun findTop20ByPlayerIdOrderByCreatedAtDesc(playerId: UUID): List<AnomalyAlert>

    // 1. Needed by controller for all alerts with pagination
    fun findByOrderByCreatedAtDesc(pageable: Pageable): List<AnomalyAlert>

    // 2. Needed by controller for filtered alerts with pagination (takes 2 args)
    fun findBySeverityOrderByCreatedAtDesc(severity: Severity, pageable: Pageable): List<AnomalyAlert>

    // 3. Needed by controller for /stats endpoint
    fun countBySeverity(severity: Severity): Long
}