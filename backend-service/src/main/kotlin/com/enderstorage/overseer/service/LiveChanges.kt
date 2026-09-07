package com.enderstorage.overseer.service

import com.enderstorage.overseer.fleet.AuditService
import com.enderstorage.overseer.fleet.FleetService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap

@Service
class LiveChanges(private val messaging: SimpMessagingTemplate, private val fleet: FleetService, private val audit: AuditService, private val db: JdbcTemplate) {
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
        db.update("DELETE FROM health_samples WHERE recorded_at<now()-interval '7 days'")
        db.update("DELETE FROM ingress_receipts WHERE received_at<now()-interval '7 days'")
        db.update("DELETE FROM mining_events WHERE created_at<now()-interval '30 days'")
        db.update("DELETE FROM intervention_limits WHERE last_action<now()-interval '7 days'")
    }
}
