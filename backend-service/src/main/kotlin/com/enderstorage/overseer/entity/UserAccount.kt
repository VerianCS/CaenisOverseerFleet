package com.enderstorage.overseer.entity

import com.enderstorage.overseer.security.Role
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
class UserAccount(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 64)
    var username: String = "",
    @Column(nullable = false)
    var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var role: Role = Role.ANALYST,
    @Column(nullable = false)
    var enabled: Boolean = true,
    // Filled by the database default.
    @Column(insertable = false, updatable = false)
    var createdAt: Instant? = null
)

@Entity
@Table(name = "sessions")
class AuthSession(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var expiresAt: Instant = Instant.now()
)
