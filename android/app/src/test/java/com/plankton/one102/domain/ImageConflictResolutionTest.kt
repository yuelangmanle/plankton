package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageConflictResolutionTest {
    private val candidates = listOf(
        ImageCountCandidate(sourceImageIndex = 0, pointLabel = "LS-1", speciesName = "剑水蚤", count = 4),
        ImageCountCandidate(sourceImageIndex = 1, pointLabel = "LS-1", speciesName = "剑水蚤", count = 7),
    )

    @Test
    fun differentCountsFromMultipleImagesRemainUnresolved() {
        val reconciliation = reconcileImageCounts(candidates)

        assertEquals(1, reconciliation.conflicts.size)
        assertTrue(reconciliation.resolvedCounts.isEmpty())
    }

    @Test
    fun conflictStrategiesProduceExplicitUserChosenCount() {
        val conflict = reconcileImageCounts(candidates).conflicts.single()

        assertEquals(11, resolveImageConflict(conflict, ImageConflictStrategy.Sum))
        assertEquals(7, resolveImageConflict(conflict, ImageConflictStrategy.Max))
        assertEquals(4, resolveImageConflict(conflict, ImageConflictStrategy.Overwrite(sourceImageIndex = 0)))
    }
}
