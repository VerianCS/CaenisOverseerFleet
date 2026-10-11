package com.enderstorage.overseer.service

import com.enderstorage.overseer.fleet.AuditService
import com.enderstorage.overseer.fleet.FleetService
import com.enderstorage.overseer.repository.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

@Service
class LiveChanges(
    private val messaging: SimpMessagingTemplate, private val fleet: FleetService, private val audit: AuditService,
    private val samples: HealthSampleRepository, private val receipts: IngressReceiptRepository,
    private val events: MiningEventRepository, private val limits: InterventionLimitRepository
) {
    private val dirty = ConcurrentHashMap.newKeySet<String>()
    private val unreachable = ConcurrentHashMap.newKeySet<String>()
    fun changed(kind: String) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object: TransactionSynchronization {
                override fun afterCommit() { dirty.add(kind) }
            })
        } else dirty.add(kind)
    }
    @Scheduled(fixedDelay=1000)
    fun publish() {
        val kinds = dirty.toList()
        dirty.removeAll(kinds.toSet())
        if (kinds.isNotEmpty()) messaging.convertAndSend("/topic/changes", mapOf("kinds" to kinds))
    }
    @Scheduled(fixedDelay=5000)
    fun detectMissingHeartbeats() {
        fleet.all().forEach {
            if (it.status == "UNREACHABLE") {
                if (unreachable.add(it.id)) {
                    audit.record("system",it.id,"NODE_UNREACHABLE","Three heartbeat cycles missed","UNREACHABLE")
                    changed("fleet")
                }
            } else if (unreachable.remove(it.id)) changed("fleet")
        }
    }
    // Operational retention, not a validation task.
    @Scheduled(fixedDelay=3600000)
    fun retention() {
        val now = Instant.now()
        samples.deleteOlderThan(now.minus(7, ChronoUnit.DAYS))
        receipts.deleteOlderThan(now.minus(7, ChronoUnit.DAYS))
        events.deleteOlderThan(now.minus(30, ChronoUnit.DAYS))
        limits.deleteOlderThan(now.minus(7, ChronoUnit.DAYS))
    }
}
