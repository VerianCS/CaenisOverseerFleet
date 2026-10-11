package com.enderstorage.overseer.entity

import jakarta.persistence.*
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

// Append-only: the database trigger and grants reject UPDATE/DELETE, so Hibernate must never issue them.
@Entity
@Immutable
@Table(name = "audit_events")
class AuditEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var correlationId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 100)
    var actor: String = "",
    @Column(length = 64)
    var instanceId: String? = null,
    @Column(nullable = false, length = 40)
    var action: String = "",
    @Column(nullable = false)
    var command: String = "",
    @Column(nullable = false, length = 24)
    var outcome: String = "",
    @Column(nullable = false)
    var detail: String = "",
    @Column(insertable = false, updatable = false)
    var createdAt: Instant? = null
)
