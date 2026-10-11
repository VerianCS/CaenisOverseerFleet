package com.enderstorage.overseer.service

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import com.enderstorage.overseer.repository.MiningEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class MiningTelemetryService(
    private val events: MiningEventRepository, private val json: ObjectMapper,
    private val detection: AnomalyDetectionService, private val publisher: ApplicationEventPublisher,
    private val changes: LiveChanges
) {
    @Transactional
    fun processTelemetryBatch(batch: List<BlockBreakTelemetry>): Int {
        var accepted = 0
        // Chronological ordering also makes persisted rolling-window queries deterministic within a batch.
        for (event in batch.sortedBy { it.timestampMs }) {
            val inserted = events.insertIfAbsent(event.eventId,event.instanceId,event.playerId,event.playerName,event.world,event.x,event.y,event.z,
                event.blockType,event.toolUsed,event.toolEfficiencyLevel,event.hasHaste,event.hasMiningFatigue,event.isExposedToAirOrCave,
                event.breakDeltaMs,Instant.ofEpochMilli(event.timestampMs),event.topologyKnown,json.writeValueAsString(event))
            if (inserted == 0) continue
            accepted++
            detection.inspect(listOf(event)).forEach { publisher.publishEvent(DetectedAlert(it,event.timestampMs)) }
        }
        if (accepted > 0) changes.changed("tactical")
        return accepted
    }
}
