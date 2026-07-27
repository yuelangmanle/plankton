package com.voiceassistant.text

import com.voiceassistant.bridge.PartnerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTranscriptNormalizerTest {
    private val normalizer = DomainTranscriptNormalizer()

    @Test
    fun keepsKnownSpeciesAndMarksUnknownWordForReview() {
        val result = normalizer.normalize(
            "桡足类五个，摇足类三",
            DomainNormalizationContext(profile = PartnerProfile.PLANKTON_V1, pointId = "LC", species = listOf("桡足类")),
        )

        assertEquals("桡足类 5 个，摇足类 3", result.normalizedText)
        assertEquals(listOf("摇足类"), result.uncertainSpans.map(UncertainSpan::text))
        assertEquals(listOf(ProposedCommandAction("count.set", "LC", "桡足类", 5)), result.proposedActions)
    }

    @Test
    fun genericPartnerDoesNotReceivePlanktonSpecificActionHints() {
        val result = normalizer.normalize(
            "桡足类五个",
            DomainNormalizationContext(profile = PartnerProfile.GENERIC, pointId = "LC", species = listOf("桡足类")),
        )

        assertTrue(result.proposedActions.isEmpty())
        assertEquals("桡足类 5 个", result.normalizedText)
    }
}
