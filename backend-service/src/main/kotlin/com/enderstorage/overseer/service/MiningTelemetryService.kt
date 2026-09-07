package com.enderstorage.overseer.service

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MiningTelemetryService(
    private val db: JdbcTemplate, private val json: ObjectMapper,
    private val detection: AnomalyDetectionService, private val events: ApplicationEventPublisher,
    private val changes: LiveChanges
) {
    @Transactional
    fun processTelemetryBatch(batch: List<BlockBreakTelemetry>): Int {
        var accepted = 0
        // Chronological ordering also makes persisted rolling-window queries deterministic within a batch.
        for (event in batch.sortedBy { it.timestampMs }) {
            val inserted = db.update("""INSERT INTO mining_events(event_id,instance_id,player_id,player_name,world,x,y,z,block_type,
                tool_used,tool_efficiency_level,has_haste,has_mining_fatigue,is_exposed,break_delta_ms,created_at,topology_known,raw_event)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(event_id) DO NOTHING""",
                event.eventId,event.instanceId,event.playerId,event.playerName,event.world,event.x,event.y,event.z,event.blockType,
                event.toolUsed,event.toolEfficiencyLevel,event.hasHaste,event.hasMiningFatigue,event.isExposedToAirOrCave,
                event.breakDeltaMs,java.sql.Timestamp(event.timestampMs),event.topologyKnown,json.writeValueAsString(event))
            if (inserted == 0) continue
            accepted++
            detection.inspect(listOf(event)).forEach { events.publishEvent(DetectedAlert(it,event.timestampMs)) }
        }
        if (accepted > 0) changes.changed("tactical")
        return accepted
    }
}
