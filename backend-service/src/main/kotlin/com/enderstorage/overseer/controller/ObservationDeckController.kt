package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.FleetService
import com.enderstorage.overseer.security.*
import com.enderstorage.overseer.entity.Severity
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.repository.AuditEventRepository
import com.enderstorage.overseer.repository.MiningEventRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/deck")
class ObservationDeckController(
    private val events: MiningEventRepository, private val alerts: AnomalyAlertRepository, private val audit: AuditEventRepository,
    private val fleet: FleetService, private val secrets: Secrets
) {
    @GetMapping("/scopes")
    fun scopes() = fleet.all().map { mapOf("id" to it.id,"name" to it.name,"worlds" to (it.health?.worlds ?: emptyList<String>())) }
    @GetMapping("/map")
    fun map(@RequestParam instanceId: String, @RequestParam(defaultValue="world") world: String,
        @RequestParam minX: Int,@RequestParam maxX: Int,@RequestParam minZ: Int,@RequestParam maxZ: Int,
        @RequestParam(defaultValue="60") minutesBack: Int,@RequestParam(defaultValue="2000") limit: Int,
        @RequestParam(defaultValue="0") cellSize: Int,@AuthenticationPrincipal account: Account): List<Map<String,Any?>> {
        require(minX in -30000000..30000000 && maxX in minX..30000000 && minZ in -30000000..30000000 && maxZ in minZ..30000000)
        require(world.length in 1..64)
        val minutes = minutesBack.coerceIn(1,1440)
        val max = limit.coerceIn(1,2000)
        val rows = if(cellSize > 0) events.mapClusters(instanceId,world,minX,minZ,maxX,maxZ,minutes,max,cellSize.coerceIn(16,2048))
            else events.mapPoints(instanceId,world,minX,minZ,maxX,maxZ,minutes,max)
        return rows.map { row ->
            row.toMutableMap().also {
                if(account.role == Role.ANALYST && it["playerId"] != null) {
                    val alias = secrets.alias(it["playerId"].toString()); it["playerId"]=alias; it["playerName"]=alias
                }
            }
        }
    }
    @GetMapping("/alerts")
    fun alerts(@RequestParam(required=false) instanceId: String?,@RequestParam(defaultValue="") q: String,
        @RequestParam(defaultValue="50") limit: Int,@AuthenticationPrincipal account: Account): List<Map<String,Any?>> {
        // Analysts search only public diagnostics; their real UUID/name must not become a search oracle.
        return alerts.search(instanceId,"%${q.take(100)}%",account.role != Role.ANALYST,limit.coerceIn(1,200)).map { row ->
                row.toMutableMap().also {
                    if(account.role == Role.ANALYST) { val alias=secrets.alias(it["playerId"].toString()); it["playerId"]=alias; it["playerName"]=alias }
                }
            }
    }
    @GetMapping("/stats")
    fun stats() = mapOf(
        "totalEventsLogged" to events.count(),
        "activeThreatsCount" to alerts.count(),
        "criticalThreatsCount" to alerts.countBySeverity(Severity.CRITICAL),
        "eventsPerSecond" to events.countByCreatedAtAfter(Instant.now().minusSeconds(60)) / 60.0,
        "automatedInterventions" to audit.countByActorAndOutcome("automation","SUCCEEDED")
    )
}
