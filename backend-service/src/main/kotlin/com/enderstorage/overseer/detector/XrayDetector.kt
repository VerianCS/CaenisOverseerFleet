package com.enderstorage.overseer.detector

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.overseer.entity.*
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.repository.BaselineModelRepository
import com.enderstorage.overseer.repository.MiningEventRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.sqrt

@Component
class XrayDetector(
    private val events: MiningEventRepository, private val baselines: BaselineModelRepository, private val alerts: AnomalyAlertRepository
) {
    fun evaluate(t: BlockBreakTelemetry): AnomalyAlert? {
        if (!t.topologyKnown || t.gameMode != "SURVIVAL" || !valuable(t.blockType)) return null
        val at = Instant.ofEpochMilli(t.timestampMs)
        val window = events.recentValuableExposure(t.playerId,t.instanceId,t.world,at.minusSeconds(900),at,PageRequest.of(0,25))
        if (window.size < 10) return null
        val hidden = window.count { !it }
        val ratio = hidden.toDouble() / window.size
        val baseline = baselines.findByIdOrNull(BaselineModelId(t.instanceId,t.playerId,t.world))
        val samples = baseline?.samples ?: 0
        val historical = baseline?.occluded?.toDouble() ?: 0.0
        val mean = (historical + 1) / (samples + 2)
        val deviation = sqrt(mean * (1 - mean) / (samples + 3))
        val threshold = if(samples >= 100) maxOf(.75, (mean + 3 * deviation + .1).coerceAtMost(.98)) else .75
        if (ratio < threshold) return null
        val recent = alerts.countByInstanceIdAndPlayerIdAndAlertTypeAndCreatedAtAfter(t.instanceId,t.playerId,
            AlertType.TOPOLOGICAL_OCCLUSION_XRAY,Instant.now().minusSeconds(120))
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
