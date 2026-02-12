package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConfig

fun looksTruncatedAnswer(text: String): Boolean {
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
    maxTokens: Int = 2200,
    continuationTokens: Int = 1200,
    maxRounds: Int = 2,
): String {
    var result = client.call(api, prompt, maxTokens = maxTokens)
    var rounds = 0
    while (looksTruncatedAnswer(result) && rounds < maxRounds) {
        val tail = result.takeLast(420)
        val continuePrompt = """
            请继续输出上文回答的剩余部分，不要重复已输出的内容。
            若需承接，请从下方“末尾片段”之后继续。
            末尾片段：
            $tail
        """.trimIndent()
        val next = runCatching { client.call(api, continuePrompt, maxTokens = continuationTokens) }.getOrNull()
        if (next.isNullOrBlank()) break
        result = result.trimEnd() + "\n" + next.trimStart()
        rounds += 1
        if (!looksTruncatedAnswer(next)) break
    }
    return result
}
