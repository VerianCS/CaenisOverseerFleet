package com.enderstorage.overseer.service

import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.overseer.detector.XrayDetector
import com.enderstorage.overseer.detector.FastMiningDetector
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.entity.AnomalyAlert
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnomalyDetectionService(
    private val fastMiningDetector: FastMiningDetector,
    private val xrayDetector: XrayDetector,
    private val alertRepository: AnomalyAlertRepository
) {
    private val log = LoggerFactory.getLogger(AnomalyDetectionService::class.java)

    @Transactional
    fun inspect(telemetryList: List<BlockBreakTelemetry>): List<AnomalyAlert> {
        val detectedAlerts = mutableListOf<AnomalyAlert>()

        for (telemetry in telemetryList) {
            // Heuristic 1: Kinetic temporal check
            fastMiningDetector.evaluate(telemetry)?.let { detectedAlerts.add(it) }

            // Heuristic 2: Topological occlusion check
            xrayDetector.evaluate(telemetry)?.let { detectedAlerts.add(it) }
        }

        if (detectedAlerts.isNotEmpty()) {
            alertRepository.saveAll(detectedAlerts)
            detectedAlerts.forEach { alert ->
                log.warn("[ANOMALY DETECTED] [${alert.severity}] [${alert.alertType}] Player: ${alert.playerName} at (${alert.x}, ${alert.y}, ${alert.z}) - ${alert.diagnosticData}")
            }
        }

        return detectedAlerts
    }
}