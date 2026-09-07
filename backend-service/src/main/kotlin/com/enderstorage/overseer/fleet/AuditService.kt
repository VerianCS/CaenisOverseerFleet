package com.enderstorage.overseer.fleet

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuditService(private val db: JdbcTemplate) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(actor: String, instance: String?, action: String, command: String, outcome: String, detail: String = "", correlation: UUID = UUID.randomUUID()): UUID {
        db.update("INSERT INTO audit_events(correlation_id,actor,instance_id,action,command,outcome,detail) VALUES (?,?,?,?,?,?,?)",
            correlation, actor, instance, action, command, outcome, detail.take(262144))
        return correlation
    }
    fun list(before: Long?, instance: String?, query: String) = db.queryForList("""
        SELECT id,correlation_id AS "correlationId",actor,instance_id AS "instanceId",action,command,outcome,detail,created_at AS "createdAt"
        FROM audit_events WHERE id < ? AND (?::text IS NULL OR instance_id=?) AND
        (actor ILIKE ? OR command ILIKE ? OR outcome ILIKE ?) ORDER BY id DESC LIMIT 100""",
        before ?: Long.MAX_VALUE, instance, instance, "%${query.take(100)}%", "%${query.take(100)}%", "%${query.take(100)}%")
    fun command(id: UUID, actor: String) = db.queryForList("""
        SELECT id,action,outcome,detail,created_at AS "createdAt" FROM audit_events
        WHERE correlation_id=? AND actor=? ORDER BY id""", id, actor)
}
