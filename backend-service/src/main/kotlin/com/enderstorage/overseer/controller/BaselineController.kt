package com.enderstorage.overseer.controller

import com.enderstorage.overseer.fleet.AuditService
import com.enderstorage.overseer.security.Account
import com.enderstorage.overseer.repository.BaselineModelRepository
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class BaselineTraining(val instanceId: String, val from: Instant, val until: Instant, val reviewedAsLegitimate: Boolean)

@RestController
@RequestMapping("/api/v1/settings/baselines")
class BaselineController(private val baselines: BaselineModelRepository, private val audit: AuditService) {
    @GetMapping fun models() = baselines.findAllByOrderByTrainedAtDesc(PageRequest.of(0,500)).map {
        mapOf("instanceId" to it.instanceId, "playerId" to it.playerId, "world" to it.world,
            "samples" to it.samples, "occluded" to it.occluded, "trainedAt" to it.trainedAt)
    }
    @PostMapping("/train")
    @Transactional
    fun train(@RequestBody input: BaselineTraining, @AuthenticationPrincipal actor: Account): Map<String,Int> {
        require(input.reviewedAsLegitimate) { "Training data must first be reviewed as legitimate gameplay" }
        require(input.from.isBefore(input.until) && input.until <= Instant.now() && java.time.Duration.between(input.from,input.until).toDays() <= 30)
        val models = baselines.train(input.instanceId,input.from,input.until)
        audit.record(actor.id.toString(),input.instanceId,"BASELINE_TRAIN","Reviewed interval ${input.from} to ${input.until}","SUCCEEDED","$models player baselines")
        return mapOf("models" to models)
    }
}
