package com.enderstorage.overseer.repository

import com.enderstorage.overseer.entity.MiningEvent
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface MiningEventRepository : JpaRepository<MiningEvent, Long> {

    @Query(
        """
        SELECT e FROM MiningEvent e
        WHERE e.world = :world
          AND e.x BETWEEN :minX AND :maxX
          AND e.z BETWEEN :minZ AND :maxZ
          AND e.createdAt >= :since
        ORDER BY e.createdAt DESC
        """
    )
    fun findInBoundingBox(
        @Param("world") world: String,
        @Param("minX") minX: Int,
        @Param("maxX") maxX: Int,
        @Param("minZ") minZ: Int,
        @Param("maxZ") maxZ: Int,
        @Param("since") since: Instant,
        pageable: Pageable // <-- Added Pageable to limit bounding box results
    ): List<MiningEvent>

    fun findTop50ByPlayerIdOrderByCreatedAtDesc(playerId: UUID): List<MiningEvent>
}