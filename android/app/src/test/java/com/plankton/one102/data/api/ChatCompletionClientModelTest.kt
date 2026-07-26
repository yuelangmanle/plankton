package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionClientModelTest {
    @Test
    fun check_returnsFailedResult_whenNetworkCallCannotBeOpened() = runBlocking {
        val result = ChatCompletionClient().check(
            ApiConfig(
                baseUrl = "http://127.0.0.1:1/v1",
                model = "test-model",
            ),
            prompt = "ping",
            maxTokens = 1,
        )

        assertFalse(result.ok)
        assertTrue(result.message.contains("请求失败"))
    }

    @Test
    fun resolvesModelsEndpointFromOpenAiBaseUrl() {
        assertEquals(
            "https://example.com/v1/models",
            resolveModelsUrl("https://example.com/v1"),
        )
    }

    @Test
    fun resolvesModelsEndpointFromFullChatCompletionUrl() {
        assertEquals(
            "https://example.com/v1/models",
            resolveModelsUrl("https://example.com/v1/chat/completions"),
        )
    }

    @Test
    fun extractsOpenAiModelListWithoutAddingMetadata() {
        val models = extractModelIds(
            """{
                "object":"list",
                "data":[
                    {"id":"model-a","object":"model"},
                    {"id":"model-b","object":"model"}
                ]
            }""",
        )

        assertEquals(listOf("model-a", "model-b"), models)
    }

    @Test
    fun extractsCommonGatewayModelsShapeAndRemovesDuplicates() {
        val models = extractModelIds(
            """{"models":[{"name":"vision"}, {"model":"chat"}, {"name":"vision"}]}""",
        )

        assertEquals(listOf("vision", "chat"), models)
    }
}
