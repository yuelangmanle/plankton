package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProviderPresetsTest {
    @Test
    fun presetsCoverCommonDomesticAndInternationalOpenAiCompatibleProviders() {
        val ids = ApiProviderPresets.entries.map { it.id }.toSet()

        assertTrue(
            ids.containsAll(
                setOf(
                    "mimo-payg",
                    "mimo-token-plan-cn",
                    "deepseek",
                    "qwen",
                    "zhipu",
                    "moonshot",
                    "openai",
                    "google-gemini",
                    "groq",
                    "openrouter",
                ),
            ),
        )
    }

    @Test
    fun intelligentRecommendationKeepsExistingChoiceAndAvoidsNonChatModels() {
        assertEquals(
            "deepseek-v4-pro",
            recommendedModel(
                listOf("deepseek-v4-flash", "deepseek-v4-pro"),
                "deepseek-v4-pro",
            ),
        )
        assertEquals(
            "qwen-turbo",
            recommendedModel(
                listOf("text-embedding-v3", "qwen-vl-max", "qwen-plus-latest", "qwen-turbo"),
                "",
            ),
        )
    }
}
