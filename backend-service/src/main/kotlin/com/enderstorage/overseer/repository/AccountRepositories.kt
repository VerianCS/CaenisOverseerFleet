package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.AuthSession
import com.enderstorage.overseer.entity.UserAccount
import com.enderstorage.overseer.security.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID> {
    fun findByUsername(username: String): UserAccount?
    fun findAllByOrderByUsername(): List<UserAccount>
    fun countByEnabledTrueAndRoleAndIdNot(role: Role, id: UUID): Long

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO accounts(id, username, password_hash, role) VALUES (:id, :username, :passwordHash, 'SUPERADMIN') ON CONFLICT(username) DO NOTHING", nativeQuery = true)
    fun insertAdminIfAbsent(id: UUID, username: String, passwordHash: String): Int

    // Serializes role changes so the "at least one enabled administrator" check cannot race.
    @Modifying
    @Query(value = "LOCK TABLE accounts IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    fun lockForRoleChange()
}

interface AuthSessionRepository : JpaRepository<AuthSession, UUID> {
    @Query("""select a from AuthSession s, UserAccount a where a.id = s.accountId and s.id = :sessionId and a.id = :accountId
        and s.expiresAt > current_timestamp and a.enabled = true""")
    fun findActiveAccount(sessionId: UUID, accountId: UUID): UserAccount?

    @Transactional
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < current_timestamp")
    fun deleteExpired(): Int

    @Transactional
    @Modifying
    @Query("delete from AuthSession s where s.accountId = :accountId")
    fun deleteByAccount(accountId: UUID): Int
}
