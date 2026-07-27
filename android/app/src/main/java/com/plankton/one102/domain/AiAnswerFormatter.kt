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
private val reasoningHeadingRegex = Regex(
    "^\\s*(?:#{1,6}\\s*)?(?:思考过程|推理过程|分析过程|内部分析|思路分析|我的思考|推理链|思维链|chain\\s*of\\s*thought|reasoning|thought\\s*process)\\s*[:：]?.*$",
    RegexOption.IGNORE_CASE,
)
private val reasoningLeadRegex = Regex(
    "^\\s*(?:好的[,，。]?\\s*)?(?:我们需要先|让我先|我先|首先需要|接下来我会|先来分析|先判断|先分析一下|思考一下这个问题).*$",
)
private val publicAnswerStartRegex = Regex(
    "^\\s*(?:#{1,6}\\s*)?(?:结论|最终结论|答案|回答|推荐|结果|依据|来源|参考|SUMMARY|ANSWER|RESULT|FINAL_[A-Z0-9_]+)(?:\\s*[:：].*|\\s+.*|$)",
    RegexOption.IGNORE_CASE,
)

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

    val kept = mutableListOf<String>()
    var collectingReasoning = false
    val currentReasoning = mutableListOf<String>()

    fun flushReasoning() {
        val body = currentReasoning.joinToString("\n").trim()
        if (body.isNotBlank()) reasoningBlocks += body
        currentReasoning.clear()
    }

    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            if (collectingReasoning) {
                currentReasoning += line
            } else {
                kept += line
            }
            continue
        }
        if (!collectingReasoning && (reasoningHeadingRegex.matches(trimmed) || reasoningLeadRegex.matches(trimmed))) {
            collectingReasoning = true
            if (!reasoningHeadingRegex.matches(trimmed)) currentReasoning += line
            continue
        }
        if (collectingReasoning) {
            if (publicAnswerStartRegex.matches(trimmed)) {
                flushReasoning()
                collectingReasoning = false
                kept += line
            } else {
                currentReasoning += line
            }
            continue
        }
        kept += line
    }
    flushReasoning()

    return AiAnswerParts(
        answerText = kept.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim(),
        reasoningText = reasoningBlocks.joinToString("\n\n").replace(Regex("\\n{3,}"), "\n\n").trim(),
    )
}

fun stripAiReasoning(raw: String): String = splitAiAnswer(raw).answerText

fun stripFinalAnswerMarkers(text: String): String = finalMarkerLineRegex
    .replace(text, "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

fun buildAiDisplayAnswer(raw: String, maxPreviewChars: Int = 1200): AiDisplayAnswer {
    val parts = splitAiAnswer(raw)
    val fullVisible = stripFinalAnswerMarkers(parts.answerText).ifBlank {
        if (raw.isBlank()) "" else "模型只返回了思考过程或结构化标记；可展开查看思考过程，建议换用非推理模型或在服务端关闭思考输出。"
    }
    val visible = if (fullVisible.length <= maxPreviewChars) {
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
