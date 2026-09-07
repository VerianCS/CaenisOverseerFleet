package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.*
import com.enderstorage.overseer.security.Account
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/fleet")
class FleetController(private val fleet: FleetService, private val audit: AuditService, private val commands: CommandGateway) {
    @GetMapping fun list() = fleet.all().map { it.view() }
    @GetMapping("/{id}") fun detail(@PathVariable id: String) = fleet.get(id).view()
    @GetMapping("/{id}/history") fun history(@PathVariable id: String, @RequestParam(defaultValue="24") hours: Int) = fleet.history(id,hours)
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    fun create(@RequestBody input: InstanceInput, @AuthenticationPrincipal user: Account): Map<String,Any> {
        val (instance,secret) = fleet.create(input)
        audit.record(user.id.toString(), input.id, "INSTANCE_CREATE", input.name, "SUCCEEDED")
        return mapOf("instance" to instance, "agentSecret" to secret)
    }
    @PutMapping("/{id}/connections")
    @PreAuthorize("hasRole('SUPERADMIN')")
    fun connections(@PathVariable id: String, @RequestBody input: ConnectionInput, @AuthenticationPrincipal user: Account) {
        fleet.connections(id,input); commands.disconnect(id)
        audit.record(user.id.toString(), id, "CONNECTION_UPDATE", "Connection credentials updated", "SUCCEEDED")
    }
    @PutMapping("/{id}/settings")
    @PreAuthorize("hasRole('SUPERADMIN')")
    fun settings(@PathVariable id: String, @RequestBody input: InstanceSettings, @AuthenticationPrincipal user: Account) {
        fleet.settings(id,input); commands.disconnect(id)
        audit.record(user.id.toString(), id, "SETTINGS_UPDATE", "Agent and containment settings updated", "SUCCEEDED")
    }
    @PostMapping("/{id}/rotate-key")
    @PreAuthorize("hasRole('SUPERADMIN')")
    fun rotate(@PathVariable id: String, @AuthenticationPrincipal user: Account): Map<String,String> {
        val secret = fleet.rotate(id)
        audit.record(user.id.toString(), id, "AGENT_KEY_ROTATE", "Agent credentials rotated", "SUCCEEDED")
        return mapOf("agentSecret" to secret)
    }
}

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(private val audit: AuditService) {
    @GetMapping
    fun list(@RequestParam(required=false) before: Long?, @RequestParam(required=false) instanceId: String?, @RequestParam(defaultValue="") q: String) =
        audit.list(before,instanceId,q)
}
