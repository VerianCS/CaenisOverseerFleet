package com.enderstorage.overseer.detector

import com.enderstorage.overseer.entity.AlertType
import com.enderstorage.overseer.entity.BaselineModel
import com.enderstorage.overseer.entity.BaselineModelId
import com.enderstorage.overseer.entity.Severity
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.repository.BaselineModelRepository
import com.enderstorage.overseer.repository.MiningEventRepository
import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XrayDetectorTest {
    private val player = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T12:00:00Z")
    private val events = mock(MiningEventRepository::class.java)
    private val baselines = mock(BaselineModelRepository::class.java)
    private val alerts = mock(AnomalyAlertRepository::class.java)
    private val detector = XrayDetector(events, baselines, alerts)

    // Mockito matchers return null; the unchecked cast keeps Kotlin from null-checking it at the call site.
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyOf(type: Class<T>): T { ArgumentMatchers.any(type); return null as T }

    private fun window(exposed: Int, hidden: Int) =
        `when`(events.recentValuableExposure(player, "srv", "world", at.minusSeconds(900), at, PageRequest.of(0, 25)))
            .thenReturn(List(exposed) { true } + List(hidden) { false })

    private fun breakOf(block: String = "DIAMOND_ORE", topologyKnown: Boolean = true) = BlockBreakTelemetry(
        playerId = player, playerName = "Steve", timestampMs = at.toEpochMilli(), world = "world", x = 1, y = -50, z = 2,
        blockType = block, isExposedToAirOrCave = false, instanceId = "srv", topologyKnown = topologyKnown)

    @Test
    fun `mostly occluded valuable ore alerts with severity by evidence size`() {
        window(0, 10)
        val alert = detector.evaluate(breakOf())!!
        assertEquals(AlertType.TOPOLOGICAL_OCCLUSION_XRAY, alert.alertType)
        assertEquals(Severity.MEDIUM, alert.severity)
        window(0, 20)
        assertEquals(Severity.HIGH, detector.evaluate(breakOf())!!.severity)
    }

    @Test
    fun `insufficient or unusable evidence never alerts`() {
        window(0, 9)
        assertNull(detector.evaluate(breakOf()))                       // fewer than 10 samples
        window(0, 10)
        assertNull(detector.evaluate(breakOf(block = "STONE")))        // not a valuable ore
        assertNull(detector.evaluate(breakOf(topologyKnown = false)))  // neighbour chunk was not loaded
        window(5, 5)
        assertNull(detector.evaluate(breakOf()))                       // 50% is below the 0.75 threshold
    }

    @Test
    fun `a trained baseline raises the threshold for natural tunnelers`() {
        window(1, 9)                                                   // 90% occluded
        assertEquals(Severity.MEDIUM, detector.evaluate(breakOf())?.severity)
        `when`(baselines.findById(BaselineModelId("srv", player, "world")))
            .thenReturn(Optional.of(BaselineModel("srv", player, "world", samples = 200, occluded = 190)))
        assertNull(detector.evaluate(breakOf()))                       // threshold is now capped at 0.98
    }

    @Test
    fun `two-minute cooldown suppresses repeat alerts`() {
        window(0, 10)
        `when`(alerts.countByInstanceIdAndPlayerIdAndAlertTypeAndCreatedAtAfter(anyOf(String::class.java), anyOf(UUID::class.java),
            anyOf(AlertType::class.java), anyOf(Instant::class.java))).thenReturn(1L)
        assertNull(detector.evaluate(breakOf()))
    }
}
