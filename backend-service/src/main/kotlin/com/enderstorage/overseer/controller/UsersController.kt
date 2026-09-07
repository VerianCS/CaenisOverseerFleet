package com.enderstorage.overseer.controller

import com.enderstorage.overseer.security.*
import com.enderstorage.overseer.fleet.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class UserInput(val username: String, val password: String, val role: Role)
data class UserUpdate(val role: Role, val enabled: Boolean, val password: String? = null)

@RestController
@RequestMapping("/api/v1/users")
class UsersController(private val db: JdbcTemplate, private val auth: AuthService, private val audit: AuditService) {
    @GetMapping fun list() = db.queryForList("SELECT id,username,role,enabled,created_at AS \"createdAt\" FROM accounts ORDER BY username")
    @PostMapping
    fun create(@RequestBody input: UserInput, @AuthenticationPrincipal actor: Account) {
        require(input.username.matches(Regex("[a-zA-Z0-9._-]{3,64}")))
        require(input.password.toByteArray().size in 16..72) { "Password must contain 16 to 72 bytes" }
        db.update("INSERT INTO accounts(id,username,password_hash,role) VALUES (?,?,?,?)",
            UUID.randomUUID(), input.username, auth.passwords.encode(input.password), input.role.name)
        audit.record(actor.id.toString(), null, "USER_CREATE", input.username, "SUCCEEDED")
    }
    @PutMapping("/{id}")
    @Transactional
    fun update(@PathVariable id: UUID, @RequestBody input: UserUpdate, @AuthenticationPrincipal actor: Account) {
        require(id != actor.id || (input.enabled && input.role == Role.SUPERADMIN)) { "You cannot disable or demote your own administrator account" }
        db.execute("LOCK TABLE accounts IN SHARE ROW EXCLUSIVE MODE")
        val superadmins = db.queryForObject("SELECT count(*) FROM accounts WHERE enabled AND role='SUPERADMIN' AND id<>?", Long::class.java, id) ?: 0
        require(superadmins > 0 || (input.enabled && input.role == Role.SUPERADMIN)) { "At least one enabled administrator is required" }
        input.password?.let { require(it.toByteArray().size in 16..72) { "Password must contain 16 to 72 bytes" } }
        db.update("UPDATE accounts SET role=?,enabled=?,password_hash=coalesce(?,password_hash) WHERE id=?",
            input.role.name, input.enabled, input.password?.let(auth.passwords::encode), id)
        auth.revokeAccount(id)
        audit.record(actor.id.toString(), null, "USER_UPDATE", id.toString(), "SUCCEEDED")
    }
}
