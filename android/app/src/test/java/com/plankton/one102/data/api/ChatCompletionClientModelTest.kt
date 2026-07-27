package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    @Test
    fun connectionCheckRejectsSuccessfulEnvelopeWithoutReadableAnswer() = runBlocking {
        val client = ChatCompletionClient(jsonClient("""{"choices":[{"message":{"role":"assistant","content":null}}]}"""))

        val result = client.check(ApiConfig(baseUrl = "https://example.test/v1", model = "test"), "ping")

        assertFalse(result.ok)
        assertTrue(result.message.contains("响应格式"))
    }

    @Test
    fun callReadsOpenAiContentPartsResponse() = runBlocking {
        val client = ChatCompletionClient(
            jsonClient(
                """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"已解析"}]}}]}""",
            ),
        )

        assertEquals("已解析", client.call(ApiConfig(baseUrl = "https://example.test/v1", model = "test"), "ping"))
    }

    @Test
    fun callResultMarksProviderLengthTerminationForContinuation() = runBlocking {
        val client = ChatCompletionClient(
            jsonClient(
                """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"尚未完成"}}]}""",
            ),
        )

        val result = client.callResult(ApiConfig(baseUrl = "https://example.test/v1", model = "test"), "ping")

        assertEquals("尚未完成", result.text)
        assertTrue(result.truncated)
    }

    private fun jsonClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }
        .build()
}
