package com.voiceassistant.text

import com.voiceassistant.bridge.PartnerProfile

internal data class DomainNormalizationContext(
    val profile: PartnerProfile = PartnerProfile.GENERIC,
    val pointId: String? = null,
    val species: List<String> = emptyList(),
    val aliases: Map<String, String> = emptyMap(),
)

internal class DomainTranscriptNormalizer {
    fun normalize(original: String, context: DomainNormalizationContext): CommandReviewModel {
        var text = TextConverters.formatTranscript(original).removeSuffix("。")
        context.aliases.forEach { (alias, canonical) ->
            if (alias.isNotBlank() && canonical.isNotBlank()) text = text.replace(alias, canonical)
        }
        text = NUMBER_TOKEN.replace(text) { match ->
            "${match.groupValues[1]} ${toNumber(match.groupValues[2])}" + match.groupValues[3].takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        }

        val known = context.species.toSet()
        val uncertain = mutableListOf<UncertainSpan>()
        val proposed = mutableListOf<ProposedCommandAction>()
        COMMAND_TOKEN.findAll(text).forEach { match ->
            val species = match.groupValues[1].trim()
            val count = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (species !in known) {
                uncertain += UncertainSpan(species, "未在当前物种表中找到")
            } else if (context.profile == PartnerProfile.PLANKTON_V1 && !context.pointId.isNullOrBlank()) {
                proposed += ProposedCommandAction("count.set", context.pointId, species, count)
            }
        }
        return CommandReviewModel(
            originalText = original,
            normalizedText = text,
            uncertainSpans = uncertain.distinctBy(UncertainSpan::text),
            proposedActions = proposed,
            unparsed = uncertain.map(UncertainSpan::text),
        )
    }

    private fun toNumber(raw: String): Int = raw.toIntOrNull() ?: CHINESE_NUMBERS[raw] ?: raw.fold(0) { sum, char -> sum * 10 + (CHINESE_DIGITS[char] ?: 0) }

    private companion object {
        val NUMBER_TOKEN = Regex("([\\p{IsHan}A-Za-z0-9_-]{2,})[：:，,\\s]*(零|一|二|三|四|五|六|七|八|九|十|两|[0-9]+)(个|只|条|枚|株)?")
        val COMMAND_TOKEN = Regex("([\\p{IsHan}A-Za-z0-9_-]{2,})\\s+([0-9]+)(?:个|只|条|枚|株)?")
        val CHINESE_NUMBERS = mapOf("零" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10)
        val CHINESE_DIGITS = CHINESE_NUMBERS.mapKeys { it.key.single() }
    }
}
