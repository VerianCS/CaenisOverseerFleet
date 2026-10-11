package com.enderstorage.overseer.fleet

import com.enderstorage.overseer.entity.AuditEvent
import com.enderstorage.overseer.repository.AuditEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuditService(private val events: AuditEventRepository) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(actor: String, instance: String?, action: String, command: String, outcome: String, detail: String = "", correlation: UUID = UUID.randomUUID()): UUID {
        events.save(AuditEvent(correlationId = correlation, actor = actor, instanceId = instance, action = action,
            command = command, outcome = outcome, detail = detail.take(262144)))
        return correlation
    }
    fun list(before: Long?, instance: String?, query: String) = events.search(before ?: Long.MAX_VALUE, instance, "%${query.take(100)}%")
    fun command(id: UUID, actor: String) = events.findByCorrelationIdAndActorOrderById(id, actor).map {
        mapOf("id" to it.id, "action" to it.action, "outcome" to it.outcome, "detail" to it.detail, "createdAt" to it.createdAt)
    }
}
