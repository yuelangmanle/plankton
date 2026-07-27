package com.voiceassistant.benchmark

internal data class TranscriptMetrics(
    val characterErrorRate: Double,
    val actionExactMatchRate: Double,
    val p95LatencyMs: Long,
)

internal object TranscriptScoring {
    fun score(expectedText: String, actualText: String, expectedActions: List<String>, actualActions: List<String>, latenciesMs: List<Long> = emptyList()): TranscriptMetrics {
        val denominator = expectedText.length.coerceAtLeast(1)
        val cer = levenshtein(expectedText, actualText).toDouble() / denominator
        val actionsMatch = if (expectedActions == actualActions) 1.0 else 0.0
        val p95 = latenciesMs.sorted().let { values ->
            if (values.isEmpty()) 0 else values[(kotlin.math.ceil(values.size * 0.95).toInt() - 1).coerceIn(0, values.lastIndex)]
        }
        return TranscriptMetrics(cer, actionsMatch, p95)
    }

    private fun levenshtein(expected: String, actual: String): Int {
        var previous = IntArray(actual.length + 1) { it }
        expected.forEachIndexed { row, expectedChar ->
            val current = IntArray(actual.length + 1)
            current[0] = row + 1
            actual.forEachIndexed { column, actualChar ->
                current[column + 1] = minOf(current[column] + 1, previous[column + 1] + 1, previous[column] + if (expectedChar == actualChar) 0 else 1)
            }
            previous = current
        }
        return previous.last()
    }
}
