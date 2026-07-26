package com.plankton.one102.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExperienceTest {
    @Test
    fun advancedSettingsPreferenceSurvivesSettingsRoundTrip() {
        val settings = Settings(advancedSettingsExpanded = true)

        assertTrue(settings.advancedSettingsExpanded)
    }

    @Test
    fun capabilityHintsRecognizeVisionChatAndSpecialPurposeModels() {
        val visionHints = suggestedCapabilities("qwen-vl-max")
        assertTrue(ApiCapability.Text in visionHints)
        assertTrue(ApiCapability.Vision in visionHints)

        val chatHints = suggestedCapabilities("deepseek-v4-flash")
        assertTrue(ApiCapability.Text in chatHints)
        assertTrue(ApiCapability.StructuredJson in chatHints)

        assertFalse(ApiCapability.Text in suggestedCapabilities("text-embedding-v3"))
    }
}
