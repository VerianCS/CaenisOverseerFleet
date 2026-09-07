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
import org.springframework.jdbc.core.JdbcTemplate
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
    private val db: JdbcTemplate,
    @Value("\${caenis.session-secret}") secret: String,
    @Value("\${caenis.bootstrap-username}") private val adminName: String,
    @Value("\${caenis.bootstrap-password}") private val adminPassword: String
) : ApplicationRunner {
    private val key = secret.toByteArray(Charsets.UTF_8).also { require(it.size >= 32) { "Session secret must contain at least 32 bytes" } }
    val passwords = BCryptPasswordEncoder(12)
    private val dummyHash = passwords.encode(UUID.randomUUID().toString())
    override fun run(args: ApplicationArguments) {
        require(adminPassword.toByteArray().size in 16..72) { "Bootstrap password must contain 16 to 72 bytes" }
        db.update("INSERT INTO accounts(id, username, password_hash, role) VALUES (?, ?, ?, 'SUPERADMIN') ON CONFLICT(username) DO NOTHING",
            UUID.randomUUID(), adminName, passwords.encode(adminPassword))
    }

    fun login(username: String, password: String): Session? {
        val rows = db.queryForList("SELECT * FROM accounts WHERE username = ?", username)
        val row = rows.firstOrNull()
        val valid = passwords.matches(password, row?.get("password_hash") as? String ?: dummyHash)
        if (!valid || row == null || row["enabled"] != true) return null
        val account = Account(row["id"] as UUID, row["username"] as String, Role.valueOf(row["role"] as String))
        val id = UUID.randomUUID()
        val expires = Instant.now().plusSeconds(8 * 3600)
        db.update("INSERT INTO sessions(id, account_id, expires_at) VALUES (?, ?, ?)", id, account.id, java.sql.Timestamp.from(expires))
        val claims = JWTClaimsSet.Builder().issuer("caenis").audience("caenis-console").subject(account.id.toString())
            .jwtID(id.toString()).claim("role", account.role.name).claim("username", account.username)
            .issueTime(Date()).expirationTime(Date.from(expires)).build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(key))
        return Session(jwt.serialize(), account, expires)
    }
    fun authenticate(token: String): Account? {
        try {
            val claims = claims(token)
            if (claims == null) {
                println(">>> [AuthService] JWT Signature or Claims validation FAILED")
                return null
            }
            println(">>> [AuthService] JWT valid. SessionID=${claims.jwtid}, AccountID=${claims.subject}")

            val sql = "SELECT a.id, a.username, a.role FROM sessions s JOIN accounts a ON a.id = s.account_id WHERE s.id = ? AND a.id = ? AND s.expires_at > now() AND a.enabled"

            val rows = db.query(sql, { rs, _ ->
                val rawId = rs.getObject("id")
                val uuid = if (rawId is UUID) rawId else UUID.fromString(rawId.toString())
                Account(uuid, rs.getString("username"), Role.valueOf(rs.getString("role")))
            }, UUID.fromString(claims.jwtid), UUID.fromString(claims.subject))

            if (rows.isEmpty()) {
                println(">>> [AuthService] No active session found in DB for session_id=${claims.jwtid}. Checking why...")
                val count = db.queryForObject("SELECT count(*) FROM sessions WHERE id = ?", Long::class.java, UUID.fromString(claims.jwtid))
                println(">>> [AuthService] Does session exist in DB at all? Count: $count")
            }

            return rows.firstOrNull()
        } catch (e: Throwable) {
            println(">>> [AuthService EXCEPTION] in authenticate(): ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    private fun claims(token: String): JWTClaimsSet? {
        val jwt = SignedJWT.parse(token)
        if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(MACVerifier(key))) return null
        return jwt.jwtClaimsSet.takeIf { it.issuer == "caenis" && "caenis-console" in it.audience && it.expirationTime.after(Date()) }
    }
    fun logout(token: String) {
        runCatching { claims(token)?.getJWTID()?.let { db.update("DELETE FROM sessions WHERE id=?", UUID.fromString(it)) } }
    }
    @Scheduled(fixedDelay = 3600000)
    fun expireSessions() { db.update("DELETE FROM sessions WHERE expires_at < now()") }

    @Transactional
    fun revokeAccount(id: UUID) { db.update("DELETE FROM sessions WHERE account_id=?", id) }
}
