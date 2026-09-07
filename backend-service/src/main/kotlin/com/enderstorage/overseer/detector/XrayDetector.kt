package com.enderstorage.overseer.detector

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.overseer.entity.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import kotlin.math.sqrt

@Component
class XrayDetector(private val db: JdbcTemplate) {
    fun evaluate(t: BlockBreakTelemetry): AnomalyAlert? {
        if (!t.topologyKnown || t.gameMode != "SURVIVAL" || !valuable(t.blockType)) return null
        val window = db.queryForList("""SELECT is_exposed FROM mining_events WHERE player_id=? AND instance_id=? AND world=?
            AND topology_known AND created_at BETWEEN ?::timestamptz - interval '15 minutes' AND ?::timestamptz
            AND (block_type LIKE '%DIAMOND_ORE' OR block_type LIKE '%GOLD_ORE' OR block_type LIKE '%EMERALD_ORE' OR block_type='ANCIENT_DEBRIS')
            ORDER BY created_at DESC,id DESC LIMIT 25""",t.playerId,t.instanceId,t.world,java.sql.Timestamp(t.timestampMs),java.sql.Timestamp(t.timestampMs))
        if (window.size < 10) return null
        val hidden = window.count { it["is_exposed"] == false }
        val ratio = hidden.toDouble() / window.size
        val baseline = db.queryForList("SELECT samples,occluded FROM baseline_models WHERE instance_id=? AND player_id=? AND world=?",t.instanceId,t.playerId,t.world).firstOrNull()
        val samples = (baseline?.get("samples") as? Number)?.toLong() ?: 0
        val historical = (baseline?.get("occluded") as? Number)?.toDouble() ?: 0.0
        val mean = (historical + 1) / (samples + 2)
        val deviation = sqrt(mean * (1 - mean) / (samples + 3))
        val threshold = if(samples >= 100) maxOf(.75, (mean + 3 * deviation + .1).coerceAtMost(.98)) else .75
        if (ratio < threshold) return null
        val recent = db.queryForObject("""SELECT count(*) FROM anomaly_alerts WHERE instance_id=? AND player_id=?
            AND alert_type='TOPOLOGICAL_OCCLUSION_XRAY' AND created_at>now()-interval '2 minutes'""",Long::class.java,t.instanceId,t.playerId) ?: 0
        if (recent > 0) return null
        return AnomalyAlert(playerId=t.playerId,playerName=t.playerName,instanceId=t.instanceId,
            alertType=AlertType.TOPOLOGICAL_OCCLUSION_XRAY,
            // Occlusion is supporting evidence, never sufficient for automatic containment.
            severity=if (ratio >= .95 && window.size >= 20) Severity.HIGH else Severity.MEDIUM,
            diagnosticData="Occluded $hidden/${window.size}; threshold ${(threshold*100).toInt()}%; historical samples $samples. Review vein geometry before acting.",
            world=t.world,x=t.x,y=t.y,z=t.z)
    }
    private fun valuable(type: String) = type.endsWith("DIAMOND_ORE") || type.endsWith("GOLD_ORE") || type.endsWith("EMERALD_ORE") || type == "ANCIENT_DEBRIS"
}
