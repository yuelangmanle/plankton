package com.plankton.one102.domain

data class ImageCountKey(
    val pointLabel: String,
    val speciesName: String,
)

data class ImageCountCandidate(
    val sourceImageIndex: Int,
    val pointLabel: String,
    val speciesName: String,
    val count: Int,
) {
    val key: ImageCountKey
        get() = ImageCountKey(pointLabel.trim(), speciesName.trim())
}

data class ImageCountConflict(
    val key: ImageCountKey,
    val candidates: List<ImageCountCandidate>,
)

data class ImageCountReconciliation(
    val resolvedCounts: Map<ImageCountKey, Int>,
    val conflicts: List<ImageCountConflict>,
)

sealed class ImageConflictStrategy {
    data object Sum : ImageConflictStrategy()
    data object Max : ImageConflictStrategy()
    data class Overwrite(val sourceImageIndex: Int) : ImageConflictStrategy()
}

/**
 * Equal readings can be used directly.  Different readings are deliberately returned as
 * conflicts: callers must ask a person before converting them into a dataset edit.
 */
fun reconcileImageCounts(candidates: List<ImageCountCandidate>): ImageCountReconciliation {
    val resolved = linkedMapOf<ImageCountKey, Int>()
    val conflicts = mutableListOf<ImageCountConflict>()
    candidates
        .filter { it.key.pointLabel.isNotBlank() && it.key.speciesName.isNotBlank() }
        .groupBy { it.key }
        .forEach { (key, rows) ->
            val distinct = rows.map { it.count.coerceAtLeast(0) }.distinct()
            if (distinct.size <= 1) {
                resolved[key] = distinct.singleOrNull() ?: 0
            } else {
                conflicts += ImageCountConflict(key = key, candidates = rows)
            }
        }
    return ImageCountReconciliation(resolvedCounts = resolved, conflicts = conflicts)
}

fun resolveImageConflict(conflict: ImageCountConflict, strategy: ImageConflictStrategy): Int {
    val values = conflict.candidates.map { it.count.coerceAtLeast(0) }
    return when (strategy) {
        ImageConflictStrategy.Sum -> values.sumOf { it.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        ImageConflictStrategy.Max -> values.maxOrNull() ?: 0
        is ImageConflictStrategy.Overwrite -> conflict.candidates
            .firstOrNull { it.sourceImageIndex == strategy.sourceImageIndex }
            ?.count
            ?.coerceAtLeast(0)
            ?: throw IllegalArgumentException("覆盖来源不存在：第 ${strategy.sourceImageIndex + 1} 张图片")
    }
}
