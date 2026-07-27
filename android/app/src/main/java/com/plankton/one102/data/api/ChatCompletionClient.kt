package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.ApiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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

        // Providers may expose a compatible API under /v1, /v2, /v3, or another versioned path.
        if (Regex("/v\\d+(?:$|/)").containsMatchIn(lower)) return "$url/chat/completions"
        if (lower.endsWith("/openai")) return "$url/chat/completions"

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
        if (Regex("/v\\d+(?:$|/)").containsMatchIn(lower) || lower.endsWith("/openai")) return "$url/completions"
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

    data class TextCallResult(
        val text: String,
        val truncated: Boolean,
    )

    data class ModelListResult(
        val models: List<String> = emptyList(),
        val message: String = "",
    ) {
        val ok: Boolean get() = models.isNotEmpty()
    }

    /**
     * Queries the provider instead of carrying a baked-in model catalogue. The result is only
     * used by the settings UI and is deliberately not persisted, so a retired model cannot be
     * reintroduced from an app-side cache.
     */
    suspend fun listModels(api: ApiConfig): ModelListResult {
        if (api.baseUrl.isBlank()) return ModelListResult(message = "请先填写 Base URL")

        val requestBuilder = Request.Builder()
            .url(resolveModelsUrl(api.baseUrl))
            .get()
            .header("Accept", "application/json")
        applyAuthHeaders(requestBuilder, api.apiKey)

        val response = runCatching { execute(requestBuilder.build()) }.getOrElse { error ->
            return ModelListResult(message = error.message ?: "获取模型列表失败")
        }
        if (!response.ok) {
            val detail = extractErrorMessage(response.raw) ?: response.raw.takeIf { it.isNotBlank() }
            return ModelListResult(
                message = buildString {
                    append("获取模型列表失败：${response.code} ${response.message}")
                    if (!detail.isNullOrBlank()) append(" - $detail")
                },
            )
        }

        val models = extractModelIds(response.raw)
        return if (models.isEmpty()) {
            ModelListResult(message = "接口未返回可识别的模型列表；可手动填写 Model")
        } else {
            ModelListResult(models = models, message = "已获取 ${models.size} 个模型")
        }
    }

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

        val reasoning = listOf(
            parsed.path("choices", 0, "message", "reasoning_content"),
            parsed.path("choices", 0, "message", "reasoning"),
            parsed.path("choices", 0, "message", "thoughts"),
            parsed.path("data", "choices", 0, "message", "reasoning_content"),
            parsed.path("data", "choices", 0, "message", "reasoning"),
        ).firstNotNullOfOrNull { it?.textContent() }

        val content = listOf(
            parsed.path("choices", 0, "message", "content"),
            parsed.path("choices", 0, "delta", "content"),
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
        ).firstNotNullOfOrNull { it?.textContent() }

        if (!content.isNullOrBlank()) return mergeReasoningAndAnswer(reasoning, content)
        val msgObj = (obj?.get("message") as? JsonObject)
        val direct = msgObj?.stringAny("content", "text", "value")?.trim()?.takeIf { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return mergeReasoningAndAnswer(reasoning, direct)
        return reasoning?.takeIf { it.isNotBlank() }?.let { "<think>\n$it\n</think>" }
    }

    private fun mergeReasoningAndAnswer(reasoning: String?, answer: String): String {
        val cleanAnswer = answer.trim()
        val cleanReasoning = reasoning?.trim().orEmpty()
        if (cleanReasoning.isBlank() || cleanAnswer.contains("<think", ignoreCase = true) || normalizeForExactComparison(cleanReasoning) == normalizeForExactComparison(cleanAnswer)) return cleanAnswer
        return "<think>\n$cleanReasoning\n</think>\n$cleanAnswer"
    }

    private fun normalizeForExactComparison(value: String): String = value
        .lowercase()
        .replace(Regex("[\\s\\p{Punct}]+"), "")

    private fun responseWasTruncated(raw: String): Boolean {
        val parsed = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: return false
        val terminalReasons = listOf(
            parsed.path("choices", 0, "finish_reason"),
            parsed.path("choices", 0, "finishReason"),
            parsed.path("choices", 0, "stop_reason"),
            parsed.path("choices", 0, "stopReason"),
            parsed.path("output", "finish_reason"),
            parsed.path("output", "finishReason"),
            parsed.path("output", "choices", 0, "finish_reason"),
            parsed.path("output", "choices", 0, "finishReason"),
            parsed.path("data", "choices", 0, "finish_reason"),
            parsed.path("data", "choices", 0, "finishReason"),
        ).mapNotNull { it?.textContent()?.trim()?.lowercase() }

        return terminalReasons.any { reason ->
            reason == "length" ||
                reason == "max_tokens" ||
                reason == "max_output_tokens" ||
                reason == "max_tokens_exceeded" ||
                reason == "token_limit" ||
                reason.contains("length_limit")
        }
    }

    private fun shouldTryLegacyCompletion(first: HttpResult, second: HttpResult): Boolean =
        !(first.code in 400..499 && second.code in 400..499)

    private fun humanizeProviderError(api: ApiConfig, model: String, detail: String): String {
        val base = api.baseUrl.lowercase()
        val lower = detail.lowercase()
        return if (base.contains("modelscope") && lower.contains("no provider") && lower.contains("supported")) {
            "ModelScope 当前没有为模型“$model”提供可用推理后端；模型目录不等于已部署，请重新获取模型并选择可调用模型"
        } else {
            detail
        }
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
                    content = "你是生态学与浮游动物学助手。请基于可核对来源回答，不得编造引用；若不确定必须直说。最终答案必须清晰、简短、可直接给用户阅读；不要把思考过程混入最终答案。",
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

    suspend fun call(api: ApiConfig, prompt: String, maxTokens: Int? = DEFAULT_MAX_TOKENS): String =
        callResult(api, prompt, maxTokens).text

    suspend fun callResult(api: ApiConfig, prompt: String, maxTokens: Int? = DEFAULT_MAX_TOKENS): TextCallResult {
        val model = api.model.trim()
        require(model.isNotEmpty()) { "Model 不能为空" }

        val attempts = mutableListOf<String>()

        val chatResult = execute(buildChatRequest(api, model, prompt, includeSystem = true, maxTokens = maxTokens))
        if (chatResult.ok) {
            val content = extractContent(chatResult.raw)
            if (!content.isNullOrBlank()) return TextCallResult(content, responseWasTruncated(chatResult.raw))
            val err = extractErrorMessage(chatResult.raw)
            attempts += "chat(system):${err ?: "响应格式不符合预期"}"
        } else {
            val err = extractErrorMessage(chatResult.raw) ?: chatResult.raw.takeIf { it.isNotBlank() }
            attempts += "chat(system):${chatResult.code} ${chatResult.message}${err?.let { " - ${humanizeProviderError(api, model, it)}" }.orEmpty()}"
        }

        val chatLiteResult = execute(buildChatRequest(api, model, prompt, includeSystem = false, maxTokens = maxTokens))
        if (chatLiteResult.ok) {
            val content = extractContent(chatLiteResult.raw)
            if (!content.isNullOrBlank()) return TextCallResult(content, responseWasTruncated(chatLiteResult.raw))
            val err = extractErrorMessage(chatLiteResult.raw)
            attempts += "chat(user):${err ?: "响应格式不符合预期"}"
        } else {
            val err = extractErrorMessage(chatLiteResult.raw) ?: chatLiteResult.raw.takeIf { it.isNotBlank() }
            attempts += "chat(user):${chatLiteResult.code} ${chatLiteResult.message}${err?.let { " - ${humanizeProviderError(api, model, it)}" }.orEmpty()}"
        }

        // /completions is a legacy fallback. Do not append a misleading 404 when the
        // OpenAI-chat endpoint has already rejected the selected model with a client error.
        if (shouldTryLegacyCompletion(chatResult, chatLiteResult)) {
            val completionResult = execute(buildCompletionRequest(api, model, prompt, maxTokens))
            if (completionResult.ok) {
                val content = extractContent(completionResult.raw)
                if (!content.isNullOrBlank()) return TextCallResult(content, responseWasTruncated(completionResult.raw))
                val err = extractErrorMessage(completionResult.raw)
                attempts += "completions:${err ?: "响应格式不符合预期"}"
            } else {
                val err = extractErrorMessage(completionResult.raw) ?: completionResult.raw.takeIf { it.isNotBlank() }
                attempts += "completions:${completionResult.code} ${completionResult.message}${err?.let { " - ${humanizeProviderError(api, model, it)}" }.orEmpty()}"
            }
        }

        throw IllegalStateException(attempts.joinToString("；"))
    }

    suspend fun check(api: ApiConfig, prompt: String, maxTokens: Int? = DEFAULT_MAX_TOKENS): CheckResult {
        val model = api.model.trim()
        if (model.isEmpty()) return CheckResult(ok = false, message = "Model 不能为空")

        val attempts = mutableListOf<String>()
        return try {
            val candidates = listOf(
                "chat(system)" to buildChatRequest(api, model, prompt, includeSystem = true, maxTokens = maxTokens),
                "chat(user)" to buildChatRequest(api, model, prompt, includeSystem = false, maxTokens = maxTokens),
            )

            for ((label, request) in candidates) {
                val res = try {
                    execute(request)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    attempts += "$label:请求失败 ${error.message ?: error.javaClass.simpleName}"
                    continue
                }
                val err = extractErrorMessage(res.raw)
                if (res.ok && err.isNullOrBlank()) {
                    if (!extractContent(res.raw).isNullOrBlank()) {
                        return CheckResult(ok = true, message = "OK（$label）")
                    }
                    attempts += "$label:响应格式不符合预期"
                    continue
                }
                val detail = err ?: res.raw.takeIf { it.isNotBlank() }
                attempts += "$label:${res.code} ${res.message}${detail?.let { " - ${humanizeProviderError(api, model, it)}" }.orEmpty()}"
            }

            CheckResult(ok = false, message = attempts.joinToString("；").ifBlank { "API 检测失败" })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CheckResult(ok = false, message = "请求失败：${error.message ?: error.javaClass.simpleName}")
        }
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

private fun JsonElement.textContent(): String? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> contentOrNull()?.trim()?.takeIf { it.isNotBlank() }
    is JsonArray -> mapNotNull { part ->
        when (part) {
            is JsonObject -> part.stringAny("text", "content", "value") ?: part["text"]?.textContent()
            else -> part.textContent()
        }
    }.joinToString("").trim().takeIf { it.isNotBlank() }
    is JsonObject -> stringAny("text", "content", "value")?.trim()?.takeIf { it.isNotBlank() }
    else -> null
}

private fun JsonPrimitive.contentOrNull(): String? = runCatching { this.content }.getOrNull()

internal fun resolveModelsUrl(input: String): String {
    var url = input.trim()
    require(url.isNotEmpty()) { "Base URL 不能为空" }

    url = url.removeSuffix("/")
    val lower = url.lowercase()
    return when {
        lower.endsWith("/models") -> url
        lower.endsWith("/chat/completions") -> url.removeSuffix("/chat/completions") + "/models"
        lower.endsWith("/completions") -> url.removeSuffix("/completions") + "/models"
        Regex("/v\\d+(?:$|/)").containsMatchIn(lower) -> "$url/models"
        lower.endsWith("/openai") -> "$url/models"
        else -> "$url/v1/models"
    }
}

internal fun extractModelIds(raw: String): List<String> {
    val root = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val seen = linkedSetOf<String>()

    fun addModel(element: JsonElement) {
        when (element) {
            is JsonPrimitive -> element.contentOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(seen::add)
            is JsonObject -> element.stringAny("id", "model", "name")?.trim()?.takeIf { it.isNotBlank() }?.let(seen::add)
            else -> Unit
        }
    }

    fun addModels(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::addModel)
            is JsonObject -> {
                // A few OpenAI-compatible gateways nest the actual list one more level.
                val nested = element["data"] ?: element["models"] ?: element["items"] ?: element["result"]
                if (nested != null) addModels(nested) else addModel(element)
            }
            else -> Unit
        }
    }

    when (root) {
        is JsonArray -> addModels(root)
        is JsonObject -> addModels(root["data"] ?: root["models"] ?: root["items"] ?: root["result"])
        else -> Unit
    }
    return seen.toList()
}
