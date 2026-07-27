package com.plankton.one102.domain

data class AiAnswerParts(
    val answerText: String,
    val reasoningText: String,
) {
    val hasReasoning: Boolean get() = reasoningText.isNotBlank()
}

data class AiDisplayAnswer(
    val visibleText: String,
    val fullVisibleText: String,
    val reasoningText: String,
) {
    val hasReasoning: Boolean get() = reasoningText.isNotBlank()
}

private val thinkBlockRegex = Regex("<think\\b[^>]*>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val reasoningFenceRegex = Regex(
    "```\\s*(?:thinking|reasoning|thought|chain-of-thought|思考|推理)[^\\n]*\\n.*?```",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val finalMarkerLineRegex = Regex("^\\s*FINAL_[A-Z0-9_]+\\s*:.*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

fun splitAiAnswer(raw: String): AiAnswerParts {
    if (raw.isBlank()) return AiAnswerParts(answerText = "", reasoningText = "")

    val reasoningBlocks = mutableListOf<String>()
    var text = raw.replace("\r\n", "\n")
    text = thinkBlockRegex.replace(text) { match ->
        val body = match.value
            .replace(Regex("^<think\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</think>$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (body.isNotBlank()) reasoningBlocks += body
        "\n"
    }
    text = reasoningFenceRegex.replace(text) { match ->
        val lines = match.value.lineSequence().drop(1).toList()
        val body = lines
            .dropLastWhile { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        if (body.isNotBlank()) reasoningBlocks += body
        "\n"
    }

    val answerText = text.replace(Regex("\\n{3,}"), "\n\n").trim()
    val reasoningText = reasoningBlocks.joinToString("\n\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    return AiAnswerParts(
        answerText = answerText,
        reasoningText = reasoningText,
    )
}

fun stripAiReasoning(raw: String): String = splitAiAnswer(raw).answerText

fun stripFinalAnswerMarkers(text: String): String = finalMarkerLineRegex
    .replace(text, "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

fun buildAiDisplayAnswer(raw: String, maxPreviewChars: Int? = null): AiDisplayAnswer {
    val parts = splitAiAnswer(raw)
    val fullVisible = stripFinalAnswerMarkers(parts.answerText).ifBlank {
        if (raw.isBlank()) "" else "服务只返回了思考过程或结构化标记，尚未返回最终答案；系统会自动续写。若仍未得到结论，请重试或更换模型。"
    }
    val visible = if (maxPreviewChars == null || fullVisible.length <= maxPreviewChars) {
        fullVisible
    } else {
        fullVisible.take(maxPreviewChars).trimEnd() + "\n\n（内容较长，点击“查看全文”查看完整可见回答。）"
    }
    return AiDisplayAnswer(
        visibleText = visible,
        fullVisibleText = fullVisible,
        reasoningText = parts.reasoningText,
    )
}
