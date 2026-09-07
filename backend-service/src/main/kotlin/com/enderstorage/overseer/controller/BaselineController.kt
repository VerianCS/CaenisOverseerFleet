package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.AuditService
import com.enderstorage.overseer.security.Account
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class BaselineTraining(val instanceId: String, val from: Instant, val until: Instant, val reviewedAsLegitimate: Boolean)

@RestController
@RequestMapping("/api/v1/settings/baselines")
class BaselineController(private val db: JdbcTemplate, private val audit: AuditService) {
    @GetMapping fun models() = db.queryForList("SELECT instance_id AS \"instanceId\",player_id AS \"playerId\",world,samples,occluded,trained_at AS \"trainedAt\" FROM baseline_models ORDER BY trained_at DESC LIMIT 500")
    @PostMapping("/train")
    @Transactional
    fun train(@RequestBody input: BaselineTraining, @AuthenticationPrincipal actor: Account): Map<String,Int> {
        require(input.reviewedAsLegitimate) { "Training data must first be reviewed as legitimate gameplay" }
        require(input.from.isBefore(input.until) && input.until <= Instant.now() && java.time.Duration.between(input.from,input.until).toDays() <= 30)
        val models = db.update("""INSERT INTO baseline_models(instance_id,player_id,world,samples,occluded,trained_at)
            SELECT instance_id,player_id,world,count(*),count(*) FILTER(WHERE NOT is_exposed),now()
            FROM mining_events WHERE instance_id=? AND created_at BETWEEN ? AND ? AND topology_known
            AND (block_type LIKE '%DIAMOND_ORE' OR block_type LIKE '%GOLD_ORE' OR block_type LIKE '%EMERALD_ORE' OR block_type='ANCIENT_DEBRIS')
            GROUP BY instance_id,player_id,world HAVING count(*)>=100
            ON CONFLICT(instance_id,player_id,world) DO UPDATE SET samples=excluded.samples,occluded=excluded.occluded,trained_at=excluded.trained_at""",
            input.instanceId,java.sql.Timestamp.from(input.from),java.sql.Timestamp.from(input.until))
        audit.record(actor.id.toString(),input.instanceId,"BASELINE_TRAIN","Reviewed interval ${input.from} to ${input.until}","SUCCEEDED","$models player baselines")
        return mapOf("models" to models)
    }
}
