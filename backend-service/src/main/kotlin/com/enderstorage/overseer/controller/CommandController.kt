package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.*
import com.enderstorage.overseer.security.Account
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
class CommandController(private val gateway: CommandGateway, private val audit: AuditService) {
    @PostMapping("/api/v1/commands")
    fun command(@RequestBody input: CommandRequest, auth: Authentication) =
        mapOf("id" to gateway.submit(auth.principal as Account,auth.credentials as String,input))
    @PostMapping("/api/v1/fleet/{id}/lifecycle")
    fun lifecycle(@PathVariable id: String, @RequestBody input: LifecycleRequest, auth: Authentication) =
        mapOf("id" to gateway.lifecycle(auth.principal as Account,auth.credentials as String,id,input.action))
    @GetMapping("/api/v1/commands/{id}")
    fun status(@PathVariable id: UUID, auth: Authentication) = audit.command(id,(auth.principal as Account).id.toString())
    @MessageMapping("/command")
    fun websocket(input: CommandRequest, principal: Principal) {
        val auth = principal as Authentication
        gateway.submit(auth.principal as Account,auth.credentials as String,input)
    }
}
