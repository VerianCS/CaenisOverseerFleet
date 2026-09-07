package com.enderstorage.overseer.detector

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.overseer.entity.*
import com.enderstorage.overseer.fleet.FleetService
import org.springframework.stereotype.Component
import kotlin.math.ceil

@Component
class FastMiningDetector(private val fleet: FleetService) {
    fun evaluate(t: BlockBreakTelemetry): AnomalyAlert? {
        val delta = t.breakDeltaMs ?: return null
        if (!t.timingReliable || t.gameMode != "SURVIVAL" || delta < 0) return null
        val hardness = MinecraftMechanicsRegistry.getHardness(t.blockType)
        if (hardness <= 0) return null
        var speed = MinecraftMechanicsRegistry.getToolMultiplier(t.toolUsed).toDouble()
        if (speed > 1 && t.toolEfficiencyLevel > 0) speed += t.toolEfficiencyLevel.toDouble() * t.toolEfficiencyLevel + 1
        speed *= 1 + 0.2 * t.hasteLevel
        speed *= when(t.fatigueLevel) { 0 -> 1.0; 1 -> 0.3; 2 -> 0.09; 3 -> 0.0027; else -> 0.00081 }
        if (t.underwater && !t.aquaAffinity) speed /= 5
        if (!t.onGround) speed /= 5
        val damage = speed / hardness / (if(t.canHarvest) 30 else 100)
        if (damage >= 1) return null
        val native = t.nativeBreakSpeed?.takeIf { it.isFinite() && it > 0 }
        if (native != null && native >= 1) return null
        val theoretical = minOf(ceil(1 / damage) * 50, native?.let { ceil(1 / it) * 50 } ?: Double.MAX_VALUE)
        val tolerance = fleet.get(t.instanceId).configuration.jitterMs
        if (delta >= theoretical - tolerance || theoretical <= tolerance + 50) return null
        val ratio = delta / theoretical
        return AnomalyAlert(
            playerId=t.playerId,playerName=t.playerName,instanceId=t.instanceId,
            alertType=AlertType.FAST_MINING_TEMPORAL_BREACH,
            severity=when { ratio < .35 -> Severity.CRITICAL; ratio < .6 -> Severity.HIGH; else -> Severity.MEDIUM },
            diagnosticData="Observed ${delta}ms; theoretical ${theoretical.toLong()}ms; tolerance ${tolerance}ms. Timing begins at block damage.",
            world=t.world,x=t.x,y=t.y,z=t.z
        )
    }
}
