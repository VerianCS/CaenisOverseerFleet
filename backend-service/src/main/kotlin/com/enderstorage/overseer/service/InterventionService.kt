package com.enderstorage.overseer.service

import com.enderstorage.overseer.entity.*
import com.enderstorage.overseer.fleet.*
import jakarta.annotation.PreDestroy
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.repository.InstanceRepository
import com.enderstorage.overseer.repository.InterventionLimitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class DetectedAlert(val alert: AnomalyAlert, val observedAtMs: Long)

@Service
class InterventionService(
    private val instances: InstanceRepository, private val alerts: AnomalyAlertRepository, private val limits: InterventionLimitRepository,
    private val fleet: FleetService, private val gateway: CommandGateway,
    private val audit: AuditService, transactionManager: PlatformTransactionManager
) {
    private val workers=ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,ArrayBlockingQueue(128))
    private val transaction=TransactionTemplate(transactionManager)
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    fun onAlert(event: DetectedAlert) {
        val alert=event.alert
        val age=System.currentTimeMillis()-event.observedAtMs
        if(age !in 0..15000) return
        if(alert.severity!=Severity.CRITICAL || alert.alertType!=AlertType.FAST_MINING_TEMPORAL_BREACH) return
        runCatching { workers.execute { transaction.executeWithoutResult { consider(alert) } } }.onFailure {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Containment queue full; manual review required")
        }
    }
    private fun consider(alert: AnomalyAlert) {
        instances.lockById(alert.instanceId)
        val node=fleet.get(alert.instanceId)
        if(!node.automationEnabled || node.status!="ONLINE") return
        val recent=alerts.countByInstanceIdAndPlayerIdAndAlertTypeAndSeverityAndCreatedAtAfter(node.id,alert.playerId,
            AlertType.FAST_MINING_TEMPORAL_BREACH,Severity.CRITICAL,Instant.now().minusSeconds(60))
        if(recent<3) return
        val global=limits.countLastMinute(node.id)
        if(global>=5) return
        val claimed=limits.claim(node.id,alert.playerId)
        if(claimed==0) return
        runCatching { gateway.automated(node.id,alert.playerId,alert.playerName,node.automationAction) }.onFailure {
            audit.record("automation",node.id,"AUTOMATED_SKIPPED",alert.playerId.toString(),"REJECTED","Instance command gateway was unavailable")
        }
    }
    @PreDestroy fun close() { workers.shutdown() }
}
