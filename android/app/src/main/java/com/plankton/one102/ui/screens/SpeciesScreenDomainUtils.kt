package com.plankton.one102.ui.screens

import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.parseIntSmart
import com.plankton.one102.ui.NameMatchKind

internal fun speciesClampNonNegativeInt(v: Int): Int = if (v < 0) 0 else v

internal fun speciesAnyCountPositive(species: Species): Boolean = species.countsByPointId.values.any { (it) > 0 }

internal fun speciesHasTaxonomy(taxonomy: Taxonomy): Boolean {
    return listOf(taxonomy.lvl1, taxonomy.lvl2, taxonomy.lvl3, taxonomy.lvl4, taxonomy.lvl5).any { it.isNotBlank() }
}

internal enum class AutoMatchKind { Taxonomy, WetWeight }

internal data class NameMatchResult(
    val name: String,
    val kind: NameMatchKind,
    val score: Double? = null,
)

internal fun isVisionModel(model: String, baseUrl: String? = null): Boolean {
    val m = model.trim().lowercase()
    if (m.isBlank()) return false
    val host = baseUrl?.trim()?.lowercase().orEmpty()
    if (host.contains("xiaomimimo.com")) return true
    if (m.contains("mimo")) return true
    val keywords = listOf(
        "qwen-vl",
        "qwen2-vl",
        "qwen3-vl",
        "glm-4v",
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4-vision",
        "gpt-4v",
        "gemini",
        "claude-3",
        "pixtral",
        "llava",
        "internvl",
        "cogvl",
        "yi-vl",
        "phi-3-vision",
        "qvq",
        "vision",
    )
    if (keywords.any { m.contains(it) }) return true
    return Regex("(^|[-_/])vl($|[-_/])").containsMatchIn(m)
}

internal fun isVisionUnsupportedError(message: String?): Boolean {
    val text = message?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return false
    if (text.contains("image_url")) return true
    if (text.contains("image url")) return true
    if (text.contains("unknown variant") && text.contains("image")) return true
    if (text.contains("unknown field") && text.contains("image")) return true
    if (text.contains("does not support image")) return true
    if (text.contains("not support") && text.contains("image")) return true
    if (text.contains("expected") && text.contains("text") && text.contains("image")) return true
    if (text.contains("vision") && (text.contains("not") || text.contains("unsupported"))) return true
    return false
}

internal fun isImagePayloadTooLarge(message: String?): Boolean {
    val text = message?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return false
    if (text.contains("413")) return true
    if (text.contains("payload")) return true
    if (text.contains("request entity")) return true
    if (text.contains("content length")) return true
    if (text.contains("too large")) return true
    if (text.contains("size limit")) return true
    return false
}

internal fun isRateLimitError(message: String?): Boolean {
    val text = message?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return false
    if (text.contains("429")) return true
    if (text.contains("rate limit")) return true
    if (text.contains("too many requests")) return true
    if (text.contains("限流")) return true
    if (text.contains("配额")) return true
    if (text.contains("quota")) return true
    if (text.contains("limit you")) return true
    return false
}

internal fun isContextLimitError(message: String?): Boolean {
    val text = message?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return false
    if (text.contains("context length")) return true
    if (text.contains("max tokens")) return true
    if (text.contains("maximum context")) return true
    if (text.contains("tokens") && text.contains("exceed")) return true
    if (text.contains("length") && text.contains("exceed")) return true
    if (text.contains("超过") && text.contains("限制")) return true
    if (text.contains("超出") && text.contains("限制")) return true
    if (text.contains("输入过长")) return true
    return false
}

internal fun shouldThrottleVision(api: ApiConfig): Boolean {
    val model = api.model.trim().lowercase()
    val base = api.baseUrl.trim().lowercase()
    if (base.contains("modelscope")) return true
    if (model.contains("235b")) return true
    if (model.contains("72b")) return true
    if (model.contains("qvq")) return true
    if (model.contains("preview")) return true
    return false
}

internal fun describeVisionError(message: String?): String {
    val text = message?.trim().orEmpty()
    if (isRateLimitError(text)) return "模型限流/配额不足，请稍后重试或更换模型"
    if (isContextLimitError(text)) return "输入超出模型限制，请减少图片数量或分批导入"
    if (isImagePayloadTooLarge(text)) return "图片过大，请降低清晰度或分批导入"
    return text.ifBlank { "未知错误" }
}

internal fun speciesFormatMg(v: Double): String {
    if (!v.isFinite() || v <= 0) return "—"
    val abs = kotlin.math.abs(v)
    return if (abs in 0.001..1000.0) {
        String.format("%.6f", v).trimEnd('0').trimEnd('.')
    } else {
        String.format("%.3g", v)
    }
}

internal fun parseCountExpr(raw: String?): Int? {
    val expr = raw?.trim()
        ?.replace("＋", "+")
        ?.replace("加", "+")
        ?.replace(" ", "")
        .orEmpty()
    if (expr.isBlank()) return null
    val parts = expr.split("+").filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    var sum = 0
    for (p in parts) {
        val token = p.trim()
        if (token.isEmpty()) return null
        val hasNumeric = token.any { it.isDigit() || "一二三四五六七八九十百千两零".contains(it) }
        if (!hasNumeric) return null
        val n = if (token.any { it.isDigit() }) {
            token.toIntOrNull() ?: parseIntSmart(token)
        } else {
            parseIntSmart(token)
        }
        if (n < 0) return null
        sum += n
    }
    return sum
}

internal fun parseCountFromText(raw: String?): Pair<String, Int?> {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return text to null
    val match = Regex("""(.+?)[\s\|｜]+([\d一二三四五六七八九十百千两零＋+加]{1,12})\s*$""").find(text)
    if (match != null) {
        val name = match.groupValues[1].trim()
        val expr = match.groupValues[2].trim()
        val count = parseCountExpr(expr) ?: expr.toIntOrNull()
        return name to count
    }
    return text to null
}

internal fun isLikelyInvalidScribble(nameRaw: String, rawLine: String?): Boolean {
    val text = (rawLine?.ifBlank { null } ?: nameRaw).trim()
    if (text.isBlank()) return true
    val normalized = text.replace(" ", "")
    if (Regex("""^[xX×\-\_—~]{2,}$""").matches(normalized)) return true
    if (Regex("""^[?？]{2,}$""").matches(normalized)) return true
    val strikeTokens = listOf("划掉", "涂掉", "作废", "删除", "无效", "作废")
    if (strikeTokens.any { normalized.contains(it) }) return true
    val strikeCount = normalized.count { it == '×' || it == 'x' || it == 'X' }
    if (strikeCount >= 2 && !normalized.any { it.isDigit() }) return true
    return false
}

internal fun normalizePointLabel(label: String): String {
    return label.trim()
        .replace("－", "-")
        .replace("—", "-")
        .replace("–", "-")
        .replace("＿", "_")
}

internal const val IMAGE_MATCH_MIN_SCORE = 0.78
internal const val IMAGE_CONFIDENCE_WARN = 0.6
