package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.splitAiAnswer

fun looksTruncatedAnswer(text: String): Boolean {
    val parts = splitAiAnswer(text)
    if (parts.answerText.isBlank() && parts.reasoningText.isNotBlank()) return true
    val trimmed = text.trimEnd()
    if (trimmed.length < 200) return false
    val last = trimmed.last()
    if (last in listOf('。', '！', '？', '!', '?', '.', '…')) return false
    if (last in listOf('，', '、', '：', ':', ';', '；', '（', '(', '［', '[', '【', '{', '『', '“', '"', '\'')) return true
    return last.isLetterOrDigit()
}

suspend fun callAiWithContinuation(
    client: ChatCompletionClient,
    api: ApiConfig,
    prompt: String,
    maxTokens: Int = 2600,
    continuationTokens: Int = 1600,
    maxRounds: Int = 2,
): String {
    var first = client.callResult(api, prompt, maxTokens = maxTokens)
    var result = first.text
    var rounds = 0
    while ((first.truncated || looksTruncatedAnswer(result)) && rounds < maxRounds) {
        val tail = result.takeLast(420)
        val continuePrompt = """
            上一段回答因长度限制未完成。请继续输出剩余部分，不要重复已输出的内容。
            优先直接给出用户可用的最终结论、数值或结构化 FINAL 标记，不要只继续思考过程。
            若需承接，请从下方“末尾片段”之后继续。
            末尾片段：
            $tail

            原始任务末尾要求：
            ${prompt.takeLast(900)}
        """.trimIndent()
        first = runCatching { client.callResult(api, continuePrompt, maxTokens = continuationTokens) }.getOrNull() ?: break
        val next = first.text
        if (next.isBlank()) break
        result = result.trimEnd() + "\n" + next.trimStart()
        rounds += 1
        if (!first.truncated && !looksTruncatedAnswer(next)) break
    }
    return result
}
