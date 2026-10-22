package com.enderstorage.overseer.detector

import com.enderstorage.overseer.entity.AlertType
import com.enderstorage.overseer.entity.Severity
import com.enderstorage.overseer.fleet.FleetService
import com.enderstorage.overseer.fleet.RegisteredInstance
import com.enderstorage.sentinel.dto.AgentConfiguration
import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FastMiningDetectorTest {
    private val fleet = mock(FleetService::class.java).also {
        `when`(it.get("srv")).thenReturn(RegisteredInstance("srv", "n", "c", "r", true, "s", null, 25575, null, null, null, null,
            null, null, AgentConfiguration(jitterMs = 100), false, "kick"))
    }
    private val detector = FastMiningDetector(fleet)

    private fun dig(delta: Int?, block: String = "DIAMOND_ORE", tool: String = "DIAMOND_PICKAXE", reliable: Boolean = true,
                    native: Double? = null, gameMode: String = "SURVIVAL") = BlockBreakTelemetry(
        playerId = UUID.randomUUID(), playerName = "Steve", timestampMs = 0, world = "world", x = 0, y = 0, z = 0,
        blockType = block, toolUsed = tool, isExposedToAirOrCave = true, breakDeltaMs = delta, instanceId = "srv",
        timingReliable = reliable, nativeBreakSpeed = native, gameMode = gameMode)

    // Diamond pickaxe on diamond ore: 8 / 3 / 30 per tick -> 12 ticks -> 600 ms; tolerance 100 ms.
    @Test
    fun `severity scales with observed over theoretical`() {
        val critical = detector.evaluate(dig(100))!!
        assertEquals(AlertType.FAST_MINING_TEMPORAL_BREACH, critical.alertType)
        assertEquals(Severity.CRITICAL, critical.severity)
        assertTrue("theoretical 600ms" in critical.diagnosticData, critical.diagnosticData)
        assertEquals(Severity.HIGH, detector.evaluate(dig(300))!!.severity)
        assertEquals(Severity.MEDIUM, detector.evaluate(dig(450))!!.severity)
    }

    @Test
    fun `legitimate or unmeasurable digs never alert`() {
        assertNull(detector.evaluate(dig(520)))                     // within tolerance of 600 ms
        assertNull(detector.evaluate(dig(100, reliable = false)))   // tool/effect changed mid-dig
        assertNull(detector.evaluate(dig(null)))
        assertNull(detector.evaluate(dig(100, gameMode = "CREATIVE")))
        assertNull(detector.evaluate(dig(100, block = "DIRT")))     // unknown hardness is skipped, not guessed
    }

    @Test
    fun `Paper's native break speed caps the theoretical time`() {
        // Native 0.25 damage/tick -> 200 ms, so 150 ms is within tolerance even though the vanilla formula says 600 ms.
        assertNull(detector.evaluate(dig(150, native = 0.25)))
        assertNull(detector.evaluate(dig(0, native = 1.0)))         // instant break
    }

    @Test
    fun `copper pickaxe is a real tier, not bare hands`() {
        // Bare-hand speed would expect ~4500 ms and flag this; copper (speed 5) expects ~900 ms.
        assertNull(detector.evaluate(dig(1000, tool = "COPPER_PICKAXE")))
    }

    @Test
    fun `common ores are covered`() {
        for (ore in listOf("COAL_ORE", "DEEPSLATE_COPPER_ORE", "LAPIS_ORE", "REDSTONE_ORE", "EMERALD_ORE", "NETHER_QUARTZ_ORE"))
            assertEquals(Severity.CRITICAL, detector.evaluate(dig(50, block = ore))?.severity, ore)
    }
}
