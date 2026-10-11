package com.enderstorage.overseer.controller

import com.enderstorage.overseer.entity.UserAccount
import com.enderstorage.overseer.security.*
import com.enderstorage.overseer.fleet.AuditService
import com.enderstorage.overseer.repository.UserAccountRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class UserInput(val username: String, val password: String, val role: Role)
data class UserUpdate(val role: Role, val enabled: Boolean, val password: String? = null)

@RestController
@RequestMapping("/api/v1/users")
class UsersController(private val accounts: UserAccountRepository, private val auth: AuthService, private val audit: AuditService) {
    @GetMapping fun list() = accounts.findAllByOrderByUsername().map {
        mapOf("id" to it.id, "username" to it.username, "role" to it.role, "enabled" to it.enabled, "createdAt" to it.createdAt)
    }
    @PostMapping
    fun create(@RequestBody input: UserInput, @AuthenticationPrincipal actor: Account) {
        require(input.username.matches(Regex("[a-zA-Z0-9._-]{3,64}")))
        require(input.password.toByteArray().size in 16..72) { "Password must contain 16 to 72 bytes" }
        accounts.save(UserAccount(username = input.username, passwordHash = auth.passwords.encode(input.password), role = input.role))
        audit.record(actor.id.toString(), null, "USER_CREATE", input.username, "SUCCEEDED")
    }
    @PutMapping("/{id}")
    @Transactional
    fun update(@PathVariable id: UUID, @RequestBody input: UserUpdate, @AuthenticationPrincipal actor: Account) {
        require(id != actor.id || (input.enabled && input.role == Role.SUPERADMIN)) { "You cannot disable or demote your own administrator account" }
        accounts.lockForRoleChange()
        val superadmins = accounts.countByEnabledTrueAndRoleAndIdNot(Role.SUPERADMIN, id)
        require(superadmins > 0 || (input.enabled && input.role == Role.SUPERADMIN)) { "At least one enabled administrator is required" }
        input.password?.let { require(it.toByteArray().size in 16..72) { "Password must contain 16 to 72 bytes" } }
        accounts.findByIdOrNull(id)?.let { account ->
            account.role = input.role
            account.enabled = input.enabled
            input.password?.let { account.passwordHash = auth.passwords.encode(it) }
        }
        auth.revokeAccount(id)
        audit.record(actor.id.toString(), null, "USER_UPDATE", id.toString(), "SUCCEEDED")
    }
}
