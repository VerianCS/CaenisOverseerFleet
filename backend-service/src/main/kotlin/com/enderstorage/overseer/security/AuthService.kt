package com.enderstorage.overseer.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import com.enderstorage.overseer.entity.AuthSession
import com.enderstorage.overseer.repository.AuthSessionRepository
import com.enderstorage.overseer.repository.UserAccountRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.UUID

enum class Role { SUPERADMIN, MODERATOR, ANALYST }
data class Account(val id: UUID, val username: String, val role: Role) : java.security.Principal {
    override fun getName() = id.toString()
}
data class Session(val token: String, val account: Account, val expiresAt: Instant)

@Service
class AuthService(
    private val accounts: UserAccountRepository,
    private val sessions: AuthSessionRepository,
    @Value("\${caenis.session-secret}") secret: String,
    @Value("\${caenis.bootstrap-username}") private val adminName: String,
    @Value("\${caenis.bootstrap-password}") private val adminPassword: String
) : ApplicationRunner {
    private val key = secret.toByteArray(Charsets.UTF_8).also { require(it.size >= 32) { "Session secret must contain at least 32 bytes" } }
    val passwords = BCryptPasswordEncoder(12)
    private val dummyHash = passwords.encode(UUID.randomUUID().toString())
    override fun run(args: ApplicationArguments) {
        require(adminPassword.toByteArray().size in 16..72) { "Bootstrap password must contain 16 to 72 bytes" }
        accounts.insertAdminIfAbsent(UUID.randomUUID(), adminName, passwords.encode(adminPassword))
    }

    fun login(username: String, password: String): Session? {
        val row = accounts.findByUsername(username)
        val valid = passwords.matches(password, row?.passwordHash ?: dummyHash)
        if (!valid || row == null || !row.enabled) return null
        val account = Account(row.id, row.username, row.role)
        val id = UUID.randomUUID()
        val expires = Instant.now().plusSeconds(8 * 3600)
        sessions.save(AuthSession(id, account.id, expires))
        val claims = JWTClaimsSet.Builder().issuer("caenis").audience("caenis-console").subject(account.id.toString())
            .jwtID(id.toString()).claim("role", account.role.name).claim("username", account.username)
            .issueTime(Date()).expirationTime(Date.from(expires)).build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(key))
        return Session(jwt.serialize(), account, expires)
    }
    fun authenticate(token: String): Account? = runCatching {
        val claims = claims(token) ?: return null
        sessions.findActiveAccount(UUID.fromString(claims.jwtid), UUID.fromString(claims.subject))
            ?.let { Account(it.id, it.username, it.role) }
    }.getOrNull()
    private fun claims(token: String): JWTClaimsSet? {
        val jwt = SignedJWT.parse(token)
        if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(MACVerifier(key))) return null
        return jwt.jwtClaimsSet.takeIf { it.issuer == "caenis" && "caenis-console" in it.audience && it.expirationTime.after(Date()) }
    }
    fun logout(token: String) {
        runCatching { claims(token)?.getJWTID()?.let { sessions.deleteById(UUID.fromString(it)) } }
    }
    @Scheduled(fixedDelay = 3600000)
    fun expireSessions() { sessions.deleteExpired() }

    @Transactional
    fun revokeAccount(id: UUID) { sessions.deleteByAccount(id) }
}
