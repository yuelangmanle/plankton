package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetBenchmarkFixtureTest {
    @Test
    fun standardFixtureCreatesOneHundredPointsAndFiveHundredSpecies() {
        val dataset = createDatasetBenchmarkFixture()

        assertEquals(100, dataset.points.size)
        assertEquals(500, dataset.species.size)
        assertTrue(dataset.species.all { it.countsByPointId.size == 100 })
    }

    @Test
    fun benchmarkQuickCountReportsDeterministicFinalCount() {
        val dataset = createDatasetBenchmarkFixture(pointCount = 2, speciesCount = 1)
        val result = measureQuickCountBenchmark(dataset, repeats = 10)

        assertEquals(10, result.finalCount)
        assertTrue(result.elapsedMs >= 0)
    }
}
