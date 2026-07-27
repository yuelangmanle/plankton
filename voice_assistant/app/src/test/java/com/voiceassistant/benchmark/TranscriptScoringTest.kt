package com.voiceassistant.benchmark

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptScoringTest {
    @Test
    fun actionExactMatchRequiresPointSpeciesAndValueToAllMatch() {
        val metrics = TranscriptScoring.score(
            expectedText = "桡足类 5",
            actualText = "桡足类 5",
            expectedActions = listOf("count.set|LC|桡足类|5"),
            actualActions = listOf("count.set|LC|桡足类|4"),
        )

        assertEquals(0.0, metrics.actionExactMatchRate, 0.0)
        assertEquals(0.0, metrics.characterErrorRate, 0.0)
    }

    @Test
    fun p95UsesStableNearestRankForSmallCorpus() {
        assertEquals(90L, TranscriptScoring.score("", "", emptyList(), emptyList(), listOf(10, 30, 90)).p95LatencyMs)
    }
}
