package com.plankton.one102.domain

import kotlin.system.measureNanoTime

data class QuickCountBenchmarkResult(
    val elapsedMs: Long,
    val finalCount: Int,
)

/**
 * Deterministic large-data fixture used by unit/performance regression checks.  It does not
 * replace a real user dataset and is never persisted by the application.
 */
fun createDatasetBenchmarkFixture(pointCount: Int = 100, speciesCount: Int = 500): Dataset {
    require(pointCount > 0) { "pointCount must be positive" }
    require(speciesCount > 0) { "speciesCount must be positive" }
    val points = (1..pointCount).map { index ->
        Point(id = "benchmark-point-$index", label = "B$index", vOrigL = 20.0)
    }
    val pointIds = points.map { it.id }
    val species = (1..speciesCount).map { index ->
        Species(
            id = "benchmark-species-$index",
            nameCn = "基准物种$index",
            countsByPointId = pointIds.associateWith { 0 },
        )
    }
    return Dataset(
        id = "benchmark-dataset",
        titlePrefix = "性能基准（不保存）",
        createdAt = "2000-01-01T00:00:00Z",
        updatedAt = "2000-01-01T00:00:00Z",
        points = points,
        species = species,
    )
}

fun measureQuickCountBenchmark(dataset: Dataset, repeats: Int): QuickCountBenchmarkResult {
    require(repeats >= 0) { "repeats must not be negative" }
    val speciesId = dataset.species.firstOrNull()?.id ?: return QuickCountBenchmarkResult(0, 0)
    val pointId = dataset.points.firstOrNull()?.id ?: return QuickCountBenchmarkResult(0, 0)
    var current = dataset
    val elapsedNs = measureNanoTime {
        repeat(repeats) {
            current = applyQuickCount(current, speciesId, pointId, delta = 1).dataset
        }
    }
    val finalCount = current.species.firstOrNull { it.id == speciesId }?.countsByPointId?.get(pointId) ?: 0
    return QuickCountBenchmarkResult(elapsedMs = elapsedNs / 1_000_000L, finalCount = finalCount)
}
