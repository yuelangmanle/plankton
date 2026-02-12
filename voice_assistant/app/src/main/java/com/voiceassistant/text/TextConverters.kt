package com.voiceassistant.text

import android.icu.text.Transliterator

internal object TextConverters {
    private val converter: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
    }

    fun toSimplified(text: String): String {
        val instance = converter ?: return text
        return runCatching { instance.transliterate(text) }.getOrDefault(text)
    }

    fun formatTranscript(
        text: String,
        applyPunctuation: Boolean = true,
        applySimplify: Boolean = true,
    ): String {
        val normalized = if (applySimplify) toSimplified(text) else text
        val trimmed = normalized.trim()
        if (!applyPunctuation) return trimmed
        return SmartPunctuator.format(trimmed)
    }

    private object SmartPunctuator {
        private val punctuationChars = setOf('，', '。', '！', '？', '；', '：', ',', '.', '!', '?', ';', ':')
        private val connectors = listOf(
            "但是", "所以", "因为", "如果", "不过", "同时", "另外",
            "并且", "而且", "因此", "于是", "比如", "例如", "最后",
            "首先", "其次", "然后", "再者", "此外",
        )

        fun format(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return trimmed
            val punctuationCount = trimmed.count { punctuationChars.contains(it) }
            if (punctuationCount > 0) {
                val density = punctuationCount.toDouble() / trimmed.length.coerceAtLeast(1)
                if (punctuationCount >= 3 || density >= 0.02) return trimmed
            }
            val normalized = normalizeSpacing(trimmed)
            return if (containsCjk(normalized)) punctuateChinese(normalized) else punctuateEnglish(normalized)
        }

        private fun normalizeSpacing(text: String): String {
            return text.replace(Regex("\\s+"), " ").trim()
        }

        private fun containsCjk(text: String): Boolean {
            return text.any { ch ->
                val block = Character.UnicodeBlock.of(ch)
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                    block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            }
        }

        private fun punctuateChinese(text: String): String {
            val compact = text.replace(" ", "")
            val builder = StringBuilder()
            var clauseLen = 0
            var sentenceLen = 0

            for (i in compact.indices) {
                val ch = compact[i]
                builder.append(ch)
                if (punctuationChars.contains(ch)) {
                    clauseLen = 0
                    sentenceLen = 0
                    continue
                }
                clauseLen += 1
                sentenceLen += 1

                val next = compact.getOrNull(i + 1)
                if (next == null) break

                if (endsWithConnector(builder)) {
                    if (!punctuationChars.contains(builder.last())) {
                        builder.append('，')
                        clauseLen = 0
                    }
                    continue
                }

                if (sentenceLen >= 28) {
                    builder.append('。')
                    clauseLen = 0
                    sentenceLen = 0
                    continue
                }

                if (clauseLen >= 14) {
                    builder.append('，')
                    clauseLen = 0
                }
            }

            if (builder.isNotEmpty() && !punctuationChars.contains(builder.last())) {
                builder.append('。')
            }
            return builder.toString()
        }

        private fun endsWithConnector(builder: StringBuilder): Boolean {
            val text = builder.toString()
            return connectors.any { word -> text.endsWith(word) }
        }

        private fun punctuateEnglish(text: String): String {
            val words = text.split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return text
            if (words.size == 1) {
                return if (text.endsWith('.')) text else "$text."
            }

            val builder = StringBuilder()
            for (i in words.indices) {
                builder.append(words[i])
                if (i == words.lastIndex) break
                if ((i + 1) % 12 == 0) {
                    builder.append(". ")
                } else {
                    builder.append(' ')
                }
            }
            if (builder.isNotEmpty() && !punctuationChars.contains(builder.last())) {
                builder.append('.')
            }
            return builder.toString()
        }
    }
}
