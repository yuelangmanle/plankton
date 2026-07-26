package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {
    private val point = Point(id = "p-1", label = "LS-1", vOrigL = 10.0)
    private val species = Species(id = "s-1", nameCn = "剑水蚤", countsByPointId = mapOf(point.id to 2))
    private val dataset = Dataset(
        id = "d-1",
        titlePrefix = "夏季调查",
        createdAt = "2026-07-27T00:00:00Z",
        updatedAt = "2026-07-27T00:00:00Z",
        points = listOf(point),
        species = listOf(species),
    )

    @Test
    fun workSessionExposesCurrentDatasetPointAndSaveState() {
        val session = buildWorkSession(
            dataset = dataset,
            activePointId = point.id,
            savePhase = DatasetSavePhase.Saving,
            lastSavedAt = "2026-07-27T01:00:00Z",
            undoCount = 2,
            redoCount = 1,
            activeTaskCount = 3,
        )

        assertEquals("夏季调查", session.datasetLabel)
        assertEquals("LS-1", session.pointLabel)
        assertEquals(DatasetSavePhase.Saving, session.savePhase)
        assertEquals(2, session.undoCount)
        assertEquals(1, session.redoCount)
        assertEquals(3, session.activeTaskCount)
    }

    @Test
    fun quickCountChangesOnlyRequestedPointAndNeverBelowZero() {
        val incremented = applyQuickCount(dataset, species.id, point.id, delta = 1)
        val decremented = applyQuickCount(incremented.dataset, species.id, point.id, delta = -99)

        assertEquals(3, incremented.newCount)
        assertEquals(0, decremented.newCount)
        assertEquals(0, decremented.dataset.species.single().countsByPointId[point.id])
    }

    @Test
    fun consecutiveQuickCountsForSameCellMergeOneUndoEntry() {
        val key = QuickCountKey(dataset.id, species.id, point.id)

        assertTrue(shouldMergeQuickCountUndo(key, key, elapsedMs = 700))
        assertFalse(shouldMergeQuickCountUndo(key, key.copy(pointId = "p-2"), elapsedMs = 100))
        assertFalse(shouldMergeQuickCountUndo(key, key, elapsedMs = 801))
    }
}
