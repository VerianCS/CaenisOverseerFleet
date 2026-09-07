package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.FleetService
import com.enderstorage.overseer.security.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/deck")
class ObservationDeckController(private val db: JdbcTemplate, private val fleet: FleetService, private val secrets: Secrets) {
    @GetMapping("/scopes")
    fun scopes() = fleet.all().map { mapOf("id" to it.id,"name" to it.name,"worlds" to (it.health?.worlds ?: emptyList<String>())) }
    @GetMapping("/map")
    fun map(@RequestParam instanceId: String, @RequestParam(defaultValue="world") world: String,
        @RequestParam minX: Int,@RequestParam maxX: Int,@RequestParam minZ: Int,@RequestParam maxZ: Int,
        @RequestParam(defaultValue="60") minutesBack: Int,@RequestParam(defaultValue="2000") limit: Int,
        @RequestParam(defaultValue="0") cellSize: Int,@AuthenticationPrincipal account: Account): List<Map<String,Any?>> {
        require(minX in -30000000..30000000 && maxX in minX..30000000 && minZ in -30000000..30000000 && maxZ in minZ..30000000)
        require(world.length in 1..64)
        val args = arrayOf<Any>(instanceId,world,minX,minZ,maxX,maxZ,minutesBack.coerceIn(1,1440),limit.coerceIn(1,2000))
        val filter = """instance_id=? AND world=? AND ST_Intersects(position,ST_MakeEnvelope(?,?,?,?,0))
            AND created_at>=now()-(? * interval '1 minute')"""
        val rows = if(cellSize > 0) {
            val cell = cellSize.coerceIn(16,2048)
            db.queryForList("""SELECT min(id) AS id, 'cluster' AS "playerName", 'CLUSTER' AS "blockType",
                floor(x/$cell)*$cell AS x,avg(y)::int AS y,floor(z/$cell)*$cell AS z,bool_and(is_exposed) AS "isExposed",
                max(created_at) AS timestamp,count(*) AS count
                FROM mining_events WHERE $filter GROUP BY floor(x/$cell),floor(z/$cell) ORDER BY max(created_at) DESC LIMIT ?""",*args)
        } else db.queryForList("""SELECT id,player_id AS "playerId",player_name AS "playerName",block_type AS "blockType",
            x,y,z,is_exposed AS "isExposed",created_at AS timestamp,1 AS count FROM mining_events WHERE $filter ORDER BY created_at DESC,id DESC LIMIT ?""",*args)
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
        val term = "%${q.take(100)}%"
        // Analysts search only public diagnostics; their real UUID/name must not become a search oracle.
        val identity = if(account.role == Role.ANALYST) "" else "OR player_name ILIKE ? OR player_id::text ILIKE ?"
        val args = mutableListOf<Any?>(instanceId,instanceId,term,term,term)
        if(account.role != Role.ANALYST) args.addAll(listOf(term,term))
        args.add(limit.coerceIn(1,200))
        return db.queryForList("""SELECT id,instance_id AS "instanceId",player_id AS "playerId",player_name AS "playerName",
            alert_type AS "alertType",severity,diagnostic_data AS "diagnosticData",world,x,y,z,created_at AS "createdAt"
            FROM anomaly_alerts WHERE (?::text IS NULL OR instance_id=?) AND
            (alert_type ILIKE ? OR diagnostic_data ILIKE ? OR concat(x,' ',y,' ',z) ILIKE ? $identity)
            ORDER BY created_at DESC,id DESC LIMIT ?""",*args.toTypedArray()).map { row ->
                row.toMutableMap().also {
                    if(account.role == Role.ANALYST) { val alias=secrets.alias(it["playerId"].toString()); it["playerId"]=alias; it["playerName"]=alias }
                }
            }
    }
    @GetMapping("/stats")
    fun stats() = mapOf(
        "totalEventsLogged" to db.queryForObject("SELECT count(*) FROM mining_events",Long::class.java),
        "activeThreatsCount" to db.queryForObject("SELECT count(*) FROM anomaly_alerts",Long::class.java),
        "criticalThreatsCount" to db.queryForObject("SELECT count(*) FROM anomaly_alerts WHERE severity='CRITICAL'",Long::class.java),
        "eventsPerSecond" to ((db.queryForObject("SELECT count(*) FROM mining_events WHERE created_at>now()-interval '1 minute'",Long::class.java) ?: 0) / 60.0),
        "automatedInterventions" to db.queryForObject("SELECT count(*) FROM audit_events WHERE actor='automation' AND outcome='SUCCEEDED'",Long::class.java)
    )
}
