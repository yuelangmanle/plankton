package com.plankton.one102.data.api

internal fun extractJsonPayload(text: String, marker: String): String? {
    return extractAllJsonPayloads(text, marker).firstOrNull()
}

internal fun extractAllJsonPayloads(text: String, marker: String? = null): List<String> {
    val results = LinkedHashSet<String>()

    if (!marker.isNullOrBlank()) {
        val markerRegex = Regex("(?is)${Regex.escape(marker)}\\s*:")
        for (match in markerRegex.findAll(text)) {
            val after = text.substring(match.range.last + 1)
            extractBalancedJson(after)?.let { results.add(it) }
        }
    }

    val codeRegex = Regex("(?is)```(?:json)?\\s*(.*?)\\s*```")
    for (match in codeRegex.findAll(text)) {
        val block = match.groupValues.getOrNull(1).orEmpty()
        extractBalancedJson(block)?.let { results.add(it) }
    }

    extractAllBalancedJson(text).forEach { results.add(it) }
    return results.toList()
}

internal fun extractAllBalancedJson(text: String): List<String> {
    val results = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val start = text.indexOfAny(charArrayOf('{', '['), index)
        if (start < 0) break
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escape = false
        var found = false

        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    when (c) {
                        '\\' -> escape = true
                        '"' -> inString = false
                    }
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) {
                        results.add(text.substring(start, i + 1))
                        index = i + 1
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) break
    }

    return results
}

internal fun extractBalancedJson(text: String): String? {
    return extractAllBalancedJson(text).firstOrNull()
}
