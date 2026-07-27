package com.plankton.one102.data.interop

import com.plankton.one102.domain.ApiConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApilotInteropTest {
    private val source = ApiConnection(
        id = "deepseek-prod",
        name = "DeepSeek Production",
        providerId = "deepseek",
        baseUrl = "https://api.deepseek.com/v1",
        selectedModel = "deepseek-chat",
        modelIds = listOf("deepseek-chat", "deepseek-reasoner"),
        apiKey = "secret-value",
    )

    @Test
    fun exportedProfilesOmitSecretUnlessUserExplicitlyAuthorizesIt() {
        val json = buildApilotImportPayload(listOf(source), includeApiKeys = false, sourceSignatureSha256 = "AA:BB")

        assertTrue(json.contains("\"schemaVersion\":2"))
        assertTrue(json.contains("\"provider\":{\"id\":\"deepseek\""))
        assertFalse(json.contains("secret-value"))
    }

    @Test
    fun pickedProfilePreservesProviderAndOnlyUsesGrantedSecretScope() {
        val profile = parseApilotPickedProfile(
            """
            {
              "schemaVersion":2,
              "grantedScopes":["connection","models.default"],
              "apiProfile":{
                "connection":{"name":"From Apilot","baseUrl":"https://api.deepseek.com/v1"},
                "provider":{"id":"deepseek"},
                "protocol":{"id":"openai_compatible"},
                "models":{"selectedModel":"deepseek-chat","availableModels":["deepseek-chat"]},
                "secrets":{"apiKey":"must-not-be-read"}
              }
            }
            """.trimIndent(),
        )

        requireNotNull(profile)
        assertEquals("deepseek", profile.connection.providerId)
        assertEquals("deepseek-chat", profile.connection.selectedModel)
        assertEquals("", profile.connection.apiKey)
    }

    @Test
    fun pickedProfileReadsSecretOnlyWhenUserGrantedScope() {
        val profile = parseApilotPickedProfile(
            """
            {
              "schemaVersion":2,
              "grantedScopes":["connection","models.default","secret.api_key"],
              "apiProfile":{
                "connection":{"name":"From Apilot","baseUrl":"https://api.deepseek.com/v1"},
                "provider":{"id":"deepseek"},
                "protocol":{"id":"openai_compatible"},
                "models":{"selectedModel":"deepseek-chat"},
                "secrets":{"apiKey":"granted-secret"}
              }
            }
            """.trimIndent(),
        )

        requireNotNull(profile)
        assertEquals("granted-secret", profile.connection.apiKey)
    }
}
