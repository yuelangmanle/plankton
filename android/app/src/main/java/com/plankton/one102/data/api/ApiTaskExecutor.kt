package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.ApiInvocationRecord
import com.plankton.one102.domain.ApiRouteMode
import com.plankton.one102.domain.ApiTaskType
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.toConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class RoutedApiResult(
    val primaryText: String? = null,
    val secondaryText: String? = null,
    val primaryConnection: ApiConnection? = null,
    val secondaryConnection: ApiConnection? = null,
    val fallbackUsed: Boolean = false,
    val error: String? = null,
)

class ApiTaskExecutor(private val client: ChatCompletionClient = ChatCompletionClient()) {
    suspend fun callText(
        settings: Settings,
        task: ApiTaskType,
        prompt: String,
        maxTokens: Int?,
        modeOverride: ApiRouteMode? = null,
        primaryOverride: String? = null,
        onRecord: (ApiInvocationRecord) -> Unit = {},
    ): RoutedApiResult = coroutineScope {
        val plan = ApiRouting.resolve(settings, task, modeOverride = modeOverride, primaryOverride = primaryOverride)
        val primary = plan.primary
        if (!plan.hasPrimary || primary == null) {
            return@coroutineScope RoutedApiResult(error = "${task.name} 未配置可用服务或模型")
        }

        if (plan.mode == ApiRouteMode.Dual) {
            val secondary = plan.secondary
            if (secondary == null || secondary.id == primary.id) {
                return@coroutineScope singleText(primary, task, prompt, maxTokens, false, onRecord)
            }
            val first = async { singleText(primary, task, prompt, maxTokens, false, onRecord) }
            val second = async { singleText(secondary, task, prompt, maxTokens, false, onRecord) }
            val firstResult = first.await()
            val secondResult = second.await()
            return@coroutineScope RoutedApiResult(
                primaryText = firstResult.primaryText,
                secondaryText = secondResult.primaryText,
                primaryConnection = primary,
                secondaryConnection = secondary,
                error = listOfNotNull(firstResult.error?.let { "${primary.name}：$it" }, secondResult.error?.let { "${secondary.name}：$it" }).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            )
        }

        val first = singleText(primary, task, prompt, maxTokens, false, onRecord)
        if (first.primaryText != null || plan.mode != ApiRouteMode.Automatic || !settings.apiAutoFallbackEnabled || !isRecoverable(first.error)) {
            return@coroutineScope first
        }
        val fallback = plan.fallback ?: return@coroutineScope first
        val second = singleText(fallback, task, prompt, maxTokens, true, onRecord)
        if (second.primaryText != null) {
            second.copy(error = "主服务 ${primary.name} 调用失败：${first.error}；已切换到 ${fallback.name}")
        } else {
            second.copy(error = "主服务 ${primary.name}：${first.error}\n备用服务 ${fallback.name}：${second.error}")
        }
    }

    suspend fun callVision(
        settings: Settings,
        prompt: String,
        imageUrls: List<String>,
        maxTokens: Int?,
        onRecord: (ApiInvocationRecord) -> Unit = {},
    ): RoutedApiResult {
        val task = ApiTaskType.ImageRecognition
        val plan = ApiRouting.resolve(settings, task)
        val primary = plan.primary ?: return RoutedApiResult(error = "图片识别未配置服务")
        fun configured(connection: ApiConnection): Boolean = connection.baseUrl.isNotBlank() && connection.selectedModel.isNotBlank()
        if (!configured(primary)) return RoutedApiResult(error = "图片识别服务未配置地址或模型")

        suspend fun run(connection: ApiConnection, fallback: Boolean): RoutedApiResult {
            val start = System.currentTimeMillis()
            return try {
                val text = client.callVision(connection.toConfig(), prompt, imageUrls, maxTokens)
                onRecord(record(task, connection, true, "OK", start, fallback))
                RoutedApiResult(primaryText = text, primaryConnection = connection, fallbackUsed = fallback)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                onRecord(record(task, connection, false, message, start, fallback))
                RoutedApiResult(primaryConnection = connection, fallbackUsed = fallback, error = message)
            }
        }

        val first = run(primary, false)
        if (first.primaryText != null || plan.mode != ApiRouteMode.Automatic || !settings.apiAutoFallbackEnabled || !isRecoverable(first.error)) return first
        val fallback = plan.fallback ?: return first
        val second = run(fallback, true)
        return if (second.primaryText != null) second.copy(error = "主图片服务 ${primary.name} 失败：${first.error}；已切换到 ${fallback.name}") else second
    }

    private suspend fun singleText(
        connection: ApiConnection,
        task: ApiTaskType,
        prompt: String,
        maxTokens: Int?,
        fallback: Boolean,
        onRecord: (ApiInvocationRecord) -> Unit,
    ): RoutedApiResult {
        if (connection.baseUrl.isBlank() || connection.selectedModel.isBlank()) {
            return RoutedApiResult(primaryConnection = connection, fallbackUsed = fallback, error = "未配置地址或模型")
        }
        val start = System.currentTimeMillis()
        return try {
            val text = callAiWithContinuation(
                client = client,
                api = connection.toConfig(),
                prompt = prompt,
                maxTokens = maxTokens ?: 2200,
                continuationTokens = 1400,
                maxRounds = 2,
            )
            onRecord(record(task, connection, true, "OK", start, fallback))
            RoutedApiResult(primaryText = text, primaryConnection = connection, fallbackUsed = fallback)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            onRecord(record(task, connection, false, message, start, fallback))
            RoutedApiResult(primaryConnection = connection, fallbackUsed = fallback, error = message)
        }
    }

    private fun record(
        task: ApiTaskType,
        connection: ApiConnection,
        ok: Boolean,
        message: String,
        startedAt: Long,
        fallbackUsed: Boolean,
    ) = ApiInvocationRecord(
        task = task,
        connectionName = connection.name.ifBlank { connection.baseUrl },
        model = connection.selectedModel,
        ok = ok,
        message = redact(message),
        latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        fallbackUsed = fallbackUsed,
        createdAt = nowIso(),
    )

    private fun isRecoverable(message: String?): Boolean {
        val value = message.orEmpty().lowercase()
        return value.contains("429") || value.contains("rate limit") || value.contains("timeout") ||
            value.contains("timed out") || value.contains("socket") || value.contains("5xx") ||
            Regex("\\b50[0-9]\\b").containsMatchIn(value)
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,]+"), "$1***")
        .replace(Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s,]+"), "$1***")
        .replace(Regex("\\bsk-[A-Za-z0-9_-]{8,}\\b"), "sk-***")
}
