package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private const val DEFAULT_MAX_TOKENS = 900

class ChatCompletionClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private enum class VisionFormat {
        OpenAi,
        ImageField,
        ImagesTopLevel,
    }

    private fun resolveChatCompletionsUrl(input: String): String {
        var url = input.trim()
        require(url.isNotEmpty()) { "Base URL 不能为空" }

        url = url.removeSuffix("/")
        val lower = url.lowercase()

        if (lower.endsWith("/chat/completions")) return url

        // If user provided ".../v1" (or any path ending with /v1), append chat/completions.
        if (lower.endsWith("/v1")) return "$url/chat/completions"

        // If the URL already contains "/v1" in the path but doesn't end with /chat/completions, assume it's an OpenAI-compatible base.
        if (Regex("/v1(?:$|/)").containsMatchIn(lower)) return "$url/chat/completions"

        // Otherwise treat it as host base and append /v1/chat/completions.
        return "$url/v1/chat/completions"
    }

    private fun resolveCompletionsUrl(input: String): String {
        var url = input.trim()
        require(url.isNotEmpty()) { "Base URL 不能为空" }

        url = url.removeSuffix("/")
        val lower = url.lowercase()

        if (lower.endsWith("/chat/completions")) {
            return url.removeSuffix("/chat/completions") + "/completions"
        }
        if (lower.endsWith("/completions")) return url
        if (lower.endsWith("/v1")) return "$url/completions"
        if (Regex("/v1(?:$|/)").containsMatchIn(lower)) return "$url/completions"
        return "$url/v1/completions"
    }

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    data class VisionContentPart(
        val type: String,
        val text: String? = null,
        @SerialName("image_url") val imageUrl: ImageUrl? = null,
    ) {
        @Serializable
        data class ImageUrl(
            val url: String,
        )

        companion object {
            fun text(value: String): VisionContentPart = VisionContentPart(type = "text", text = value)
            fun image(url: String): VisionContentPart = VisionContentPart(type = "image_url", imageUrl = ImageUrl(url = url))
        }
    }

    @Serializable
    data class VisionMessage(
        val role: String,
        val content: List<VisionContentPart>,
    )

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val temperature: Double = 0.0,
        val messages: List<ChatMessage>,
        @SerialName("max_tokens") val maxTokens: Int? = null,
    )

    @Serializable
    data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    ) {
        @Serializable
        data class Choice(
            val message: Message? = null,
        ) {
            @Serializable
            data class Message(
                val content: String? = null,
                @SerialName("role") val role: String? = null,
            )
        }
    }

    @Serializable
    data class VisionCompletionRequest(
        val model: String,
        val temperature: Double = 0.0,
        val messages: List<VisionMessage>,
        @SerialName("max_tokens") val maxTokens: Int? = null,
    )

    @Serializable
    data class CompletionRequest(
        val model: String,
        val prompt: String,
        val temperature: Double = 0.0,
        @SerialName("max_tokens") val maxTokens: Int? = null,
    )

    private fun normalizeImageData(url: String): String {
        val trimmed = url.trim()
        val idx = trimmed.indexOf("base64,")
        return if (idx >= 0) trimmed.substring(idx + 7) else trimmed
    }

    private fun resolveVisionFormats(api: ApiConfig): List<VisionFormat> {
        val base = api.baseUrl.trim().lowercase()
        val model = api.model.trim().lowercase()
        val preferAlt = base.contains("modelscope") || base.contains("xiaomimimo") || model.contains("mimo")
        return if (preferAlt) {
            listOf(VisionFormat.ImageField, VisionFormat.OpenAi, VisionFormat.ImagesTopLevel)
        } else {
            listOf(VisionFormat.OpenAi, VisionFormat.ImageField, VisionFormat.ImagesTopLevel)
        }
    }

    private fun buildVisionRequest(
        api: ApiConfig,
        format: VisionFormat,
        prompt: String,
        imageUrls: List<String>,
        maxTokens: Int?,
    ): Request {
        val url = resolveChatCompletionsUrl(api.baseUrl)
        val model = api.model.trim()
        val bodyJson = when (format) {
            VisionFormat.OpenAi -> {
                val parts = buildList {
                    add(VisionContentPart.text(prompt))
                    for (img in imageUrls) {
                        add(VisionContentPart.image(img))
                    }
                }
                val reqBody = VisionCompletionRequest(
                    model = model,
                    temperature = 0.0,
                    messages = listOf(
                        VisionMessage(
                            role = "system",
                            content = listOf(VisionContentPart.text("你是生态学与浮游动物学助手。请严格按照提示输出结构化结果。")),
                        ),
                        VisionMessage(role = "user", content = parts),
                    ),
                    maxTokens = maxTokens?.takeIf { it > 0 },
                )
                AppJson.encodeToString(VisionCompletionRequest.serializer(), reqBody)
            }
            VisionFormat.ImageField -> {
                val systemParts = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("你是生态学与浮游动物学助手。请严格按照提示输出结构化结果。"))
                        },
                    )
                }
                val userParts = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(prompt))
                        },
                    )
                    for (img in imageUrls) {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("image"))
                                put("image", JsonPrimitive(normalizeImageData(img)))
                            },
                        )
                    }
                }
                val payload = buildJsonObject {
                    put("model", JsonPrimitive(model))
                    put("temperature", JsonPrimitive(0.0))
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", JsonPrimitive("system"))
                                    put("content", systemParts)
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("role", JsonPrimitive("user"))
                                    put("content", userParts)
                                },
                            )
                        },
                    )
                    maxTokens?.takeIf { it > 0 }?.let { put("max_tokens", JsonPrimitive(it)) }
                }
                AppJson.encodeToString(JsonElement.serializer(), payload)
            }
            VisionFormat.ImagesTopLevel -> {
                val payload = buildJsonObject {
                    put("model", JsonPrimitive(model))
                    put("temperature", JsonPrimitive(0.0))
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", JsonPrimitive("system"))
                                    put("content", JsonPrimitive("你是生态学与浮游动物学助手。请严格按照提示输出结构化结果。"))
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("role", JsonPrimitive("user"))
                                    put("content", JsonPrimitive(prompt))
                                },
                            )
                        },
                    )
                    put(
                        "images",
                        buildJsonArray {
                            for (img in imageUrls) {
                                add(JsonPrimitive(normalizeImageData(img)))
                            }
                        },
                    )
                    maxTokens?.takeIf { it > 0 }?.let { put("max_tokens", JsonPrimitive(it)) }
                }
                AppJson.encodeToString(JsonElement.serializer(), payload)
            }
        }

        val body = bodyJson.toRequestBody(JSON_MEDIA)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        applyAuthHeaders(requestBuilder, api.apiKey)
        return requestBuilder.build()
    }

    private data class HttpResult(
        val code: Int,
        val message: String,
        val raw: String,
    ) {
        val ok: Boolean = code in 200..299
    }

    data class CheckResult(
        val ok: Boolean,
        val message: String,
    )

    private suspend fun execute(request: Request): HttpResult {
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                HttpResult(code = res.code, message = res.message, raw = raw)
            }
        }
    }

    private fun extractErrorMessage(raw: String): String? {
        val parsed = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        return extractErrorMessage(parsed)
    }

    private fun extractErrorMessage(parsed: JsonObject): String? {
        val errObj = parsed["error"] as? JsonObject
        val err = errObj?.stringAny("message", "msg", "detail", "error")
            ?: errObj?.stringAny("code", "type")
            ?: parsed.stringAny("error", "err", "error_message", "errorMessage")
        if (!err.isNullOrBlank()) return err.trim()

        val success = parsed.boolAny("success", "ok")
        val code = parsed.intAny("code", "status", "status_code", "error_code", "errcode")
        val message = parsed.stringAny("message", "msg", "detail")

        if (success == false) return message ?: "请求失败"
        if (code != null && code != 0 && code != 200) return message ?: "错误码 $code"

        val hasContent = parsed.hasAny("choices", "output", "result", "answer", "response", "content", "text", "data")
        if (!hasContent) {
            val msg = message?.trim()
            if (!msg.isNullOrBlank()) {
                val lowered = msg.lowercase()
                val ack = setOf("ok", "success", "succeeded", "done", "completed", "complete")
                if (lowered !in ack) return msg
            }
        }
        return null
    }

    private fun extractContent(raw: String): String? {
        val parsed = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull()
        if (parsed == null) return raw.takeIf { it.isNotBlank() }

        val obj = parsed as? JsonObject
        if (obj != null) {
            val err = extractErrorMessage(obj)
            if (!err.isNullOrBlank()) return null
        }

        val content = listOf(
            parsed.path("choices", 0, "message", "content"),
            parsed.path("choices", 0, "text"),
            parsed.path("output", "text"),
            parsed.path("output", "content"),
            parsed.path("output", "message", "content"),
            parsed.path("output", "choices", 0, "message", "content"),
            parsed.path("data", "choices", 0, "message", "content"),
            parsed.path("data", "choices", 0, "text"),
            parsed.path("data", "output", "text"),
            parsed.path("data", "output", "content"),
            parsed.path("data", "output", "choices", 0, "message", "content"),
            parsed.path("data", "result"),
            parsed.path("data", "answer"),
            parsed.path("data", "response"),
            parsed.path("data", "content"),
            parsed.path("data", "text"),
            parsed.path("result"),
            parsed.path("answer"),
            parsed.path("response"),
            parsed.path("content"),
            parsed.path("text"),
        ).firstNotNullOfOrNull { it?.stringValue() }

        if (!content.isNullOrBlank()) return content
        val msgObj = (obj?.get("message") as? JsonObject)
        return msgObj?.stringAny("content", "text", "value")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun applyAuthHeaders(builder: Request.Builder, apiKey: String) {
        val key = apiKey.trim()
        if (key.isNotEmpty()) {
            builder.header("Authorization", "Bearer $key")
            builder.header("X-API-Key", key)
            builder.header("api-key", key)
        }
    }

    private fun buildChatRequest(
        api: ApiConfig,
        model: String,
        prompt: String,
        includeSystem: Boolean,
        maxTokens: Int?,
    ): Request {
        val url = resolveChatCompletionsUrl(api.baseUrl)
        val messages = if (includeSystem) {
            listOf(
                ChatMessage(
                    role = "system",
                    content = "你是生态学与浮游动物学助手。请基于可核对来源回答，不得编造引用；若不确定必须直说。",
                ),
                ChatMessage(role = "user", content = prompt),
            )
        } else {
            listOf(ChatMessage(role = "user", content = prompt))
        }
        val reqBody = ChatCompletionRequest(
            model = model,
            temperature = 0.0,
            messages = messages,
            maxTokens = maxTokens?.takeIf { it > 0 },
        )
        val body = AppJson.encodeToString(ChatCompletionRequest.serializer(), reqBody).toRequestBody(JSON_MEDIA)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        applyAuthHeaders(requestBuilder, api.apiKey)
        return requestBuilder.build()
    }

    private fun buildCompletionRequest(
        api: ApiConfig,
        model: String,
        prompt: String,
        maxTokens: Int?,
    ): Request {
        val completionUrl = resolveCompletionsUrl(api.baseUrl)
        val reqBody = CompletionRequest(
            model = model,
            prompt = prompt,
            temperature = 0.0,
            maxTokens = maxTokens?.takeIf { it > 0 },
        )
        val body = AppJson.encodeToString(CompletionRequest.serializer(), reqBody).toRequestBody(JSON_MEDIA)
        val requestBuilder = Request.Builder()
            .url(completionUrl)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        applyAuthHeaders(requestBuilder, api.apiKey)
        return requestBuilder.build()
    }

    suspend fun call(api: ApiConfig, prompt: String, maxTokens: Int? = DEFAULT_MAX_TOKENS): String {
        val model = api.model.trim()
        require(model.isNotEmpty()) { "Model 不能为空" }

        val attempts = mutableListOf<String>()

        val chatResult = execute(buildChatRequest(api, model, prompt, includeSystem = true, maxTokens = maxTokens))
        if (chatResult.ok) {
            val content = extractContent(chatResult.raw)
            if (!content.isNullOrBlank()) return content
            val err = extractErrorMessage(chatResult.raw)
            attempts += "chat(system):${err ?: "响应格式不符合预期"}"
        } else {
            val err = extractErrorMessage(chatResult.raw) ?: chatResult.raw.takeIf { it.isNotBlank() }
            attempts += "chat(system):${chatResult.code} ${chatResult.message}${err?.let { " - $it" }.orEmpty()}"
        }

        val chatLiteResult = execute(buildChatRequest(api, model, prompt, includeSystem = false, maxTokens = maxTokens))
        if (chatLiteResult.ok) {
            val content = extractContent(chatLiteResult.raw)
            if (!content.isNullOrBlank()) return content
            val err = extractErrorMessage(chatLiteResult.raw)
            attempts += "chat(user):${err ?: "响应格式不符合预期"}"
        } else {
            val err = extractErrorMessage(chatLiteResult.raw) ?: chatLiteResult.raw.takeIf { it.isNotBlank() }
            attempts += "chat(user):${chatLiteResult.code} ${chatLiteResult.message}${err?.let { " - $it" }.orEmpty()}"
        }

        val completionResult = execute(buildCompletionRequest(api, model, prompt, maxTokens))
        if (completionResult.ok) {
            val content = extractContent(completionResult.raw)
            if (!content.isNullOrBlank()) return content
            val err = extractErrorMessage(completionResult.raw)
            attempts += "completions:${err ?: "响应格式不符合预期"}"
        } else {
            val err = extractErrorMessage(completionResult.raw) ?: completionResult.raw.takeIf { it.isNotBlank() }
            attempts += "completions:${completionResult.code} ${completionResult.message}${err?.let { " - $it" }.orEmpty()}"
        }

        throw IllegalStateException(attempts.joinToString("；"))
    }

    suspend fun check(api: ApiConfig, prompt: String, maxTokens: Int? = DEFAULT_MAX_TOKENS): CheckResult {
        val model = api.model.trim()
        if (model.isEmpty()) return CheckResult(ok = false, message = "Model 不能为空")

        val attempts = mutableListOf<String>()
        val candidates = listOf(
            "chat(system)" to buildChatRequest(api, model, prompt, includeSystem = true, maxTokens = maxTokens),
            "chat(user)" to buildChatRequest(api, model, prompt, includeSystem = false, maxTokens = maxTokens),
            "completions" to buildCompletionRequest(api, model, prompt, maxTokens),
        )

        for ((label, request) in candidates) {
            val res = execute(request)
            val err = extractErrorMessage(res.raw)
            if (res.ok && err.isNullOrBlank()) {
                return CheckResult(ok = true, message = "OK（$label）")
            }
            val detail = err ?: res.raw.takeIf { it.isNotBlank() }
            attempts += "$label:${res.code} ${res.message}${detail?.let { " - $it" }.orEmpty()}"
        }

        return CheckResult(ok = false, message = attempts.joinToString("；"))
    }

    suspend fun callVision(
        api: ApiConfig,
        prompt: String,
        imageUrls: List<String>,
        maxTokens: Int? = DEFAULT_MAX_TOKENS,
    ): String {
        val model = api.model.trim()
        require(model.isNotEmpty()) { "Model 不能为空" }
        val attempts = mutableListOf<String>()
        for (format in resolveVisionFormats(api)) {
            val label = when (format) {
                VisionFormat.OpenAi -> "vision(openai)"
                VisionFormat.ImageField -> "vision(image)"
                VisionFormat.ImagesTopLevel -> "vision(images)"
            }
            val res = execute(buildVisionRequest(api, format, prompt, imageUrls, maxTokens))
            if (res.ok) {
                val content = extractContent(res.raw)
                if (!content.isNullOrBlank()) return content
                val err = extractErrorMessage(res.raw)
                attempts += "$label:${err ?: "响应格式不符合预期"}"
            } else {
                val err = extractErrorMessage(res.raw) ?: res.raw.takeIf { it.isNotBlank() }
                attempts += "$label:${res.code} ${res.message}${err?.let { " - $it" }.orEmpty()}"
                if (res.code == 401 || res.code == 403 || res.code == 429) break
            }
        }

        throw IllegalStateException(attempts.joinToString("；"))
    }
}

private fun JsonObject.stringAny(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k] ?: continue
        val prim = v as? JsonPrimitive
        if (prim != null) {
            val text = prim.contentOrNull()?.trim()
            if (!text.isNullOrBlank()) return text
        }
    }
    return null
}

private fun JsonObject.boolAny(vararg keys: String): Boolean? {
    for (k in keys) {
        val v = this[k] ?: continue
        val prim = v as? JsonPrimitive ?: continue
        val direct = prim.booleanOrNull
        if (direct != null) return direct
        val text = prim.contentOrNull()?.trim()?.lowercase().orEmpty()
        when (text) {
            "true", "yes", "y", "1" -> return true
            "false", "no", "n", "0" -> return false
        }
    }
    return null
}

private fun JsonObject.intAny(vararg keys: String): Int? {
    for (k in keys) {
        val v = this[k] ?: continue
        val prim = v as? JsonPrimitive ?: continue
        val direct = prim.intOrNull ?: prim.doubleOrNull?.toInt()
        if (direct != null) return direct
        val text = prim.contentOrNull()?.trim()
        val parsed = text?.toIntOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private fun JsonObject.hasAny(vararg keys: String): Boolean {
    return keys.any { containsKey(it) }
}

private fun JsonElement.path(vararg steps: Any): JsonElement? {
    var cur: JsonElement = this
    for (step in steps) {
        cur = when (step) {
            is String -> (cur as? JsonObject)?.get(step) ?: return null
            is Int -> (cur as? JsonArray)?.getOrNull(step) ?: return null
            else -> return null
        }
    }
    return cur
}

private fun JsonElement.stringValue(): String? {
    val prim = this as? JsonPrimitive ?: return null
    val text = prim.contentOrNull()?.trim()
    return text?.takeIf { it.isNotBlank() }
}

private fun JsonPrimitive.contentOrNull(): String? = runCatching { this.content }.getOrNull()
