package com.plankton.one102.domain

/** A small, UI-independent summary of the dataset currently being edited. */
enum class DatasetSavePhase { Saved, Saving, Unsaved }

data class WorkSession(
    val datasetLabel: String,
    val pointLabel: String,
    val savePhase: DatasetSavePhase,
    val lastSavedAt: String?,
    val undoCount: Int,
    val redoCount: Int,
    val activeTaskCount: Int,
)

fun buildWorkSession(
    dataset: Dataset?,
    activePointId: Id?,
    savePhase: DatasetSavePhase,
    lastSavedAt: String?,
    undoCount: Int,
    redoCount: Int,
    activeTaskCount: Int,
): WorkSession {
    val point = dataset?.points?.firstOrNull { it.id == activePointId }
    return WorkSession(
        datasetLabel = dataset?.titlePrefix?.trim().takeUnless { it.isNullOrBlank() } ?: "未命名数据集",
        pointLabel = point?.label?.trim().takeUnless { it.isNullOrBlank() } ?: "未选择点位",
        savePhase = savePhase,
        lastSavedAt = lastSavedAt,
        undoCount = undoCount.coerceAtLeast(0),
        redoCount = redoCount.coerceAtLeast(0),
        activeTaskCount = activeTaskCount.coerceAtLeast(0),
    )
}

data class QuickCountKey(
    val datasetId: Id,
    val speciesId: Id,
    val pointId: Id,
)

data class QuickCountResult(
    val dataset: Dataset,
    val newCount: Int,
)

fun applyQuickCount(
    dataset: Dataset,
    speciesId: Id,
    pointId: Id,
    delta: Int,
): QuickCountResult {
    var applied = false
    var resultCount = 0
    val species = dataset.species.map { item ->
        if (item.id != speciesId) return@map item
        applied = true
        val next = ((item.countsByPointId[pointId] ?: 0).toLong() + delta.toLong())
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        resultCount = next
        item.copy(countsByPointId = item.countsByPointId + (pointId to next))
    }
    return if (applied) {
        QuickCountResult(dataset.copy(species = species), resultCount)
    } else {
        QuickCountResult(dataset, 0)
    }
}

fun shouldMergeQuickCountUndo(
    previous: QuickCountKey?,
    current: QuickCountKey,
    elapsedMs: Long,
    windowMs: Long = 800L,
): Boolean = previous == current && elapsedMs in 0..windowMs
