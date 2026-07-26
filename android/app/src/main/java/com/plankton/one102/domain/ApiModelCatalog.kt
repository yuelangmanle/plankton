package com.plankton.one102.domain

/**
 * Turns a provider-returned model list into a stable, explainable default.  This deliberately
 * has no provider-specific model catalogue: providers retire names independently.
 */
fun stableModelIds(modelIds: List<String>): List<String> = modelIds
    .asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinctBy(String::lowercase)
    .sortedWith(compareBy<String> { modelPriority(it) }.thenBy(String::lowercase))
    .toList()

fun recommendedModel(modelIds: List<String>, currentSelection: String): String? {
    val models = stableModelIds(modelIds)
    val current = currentSelection.trim()
    models.firstOrNull { it.equals(current, ignoreCase = true) }?.let { return it }

    val chatCandidates = models.filterNot(::isSpecialPurposeModel)
    return chatCandidates.firstOrNull() ?: models.firstOrNull()
}

/**
 * Offers conservative capability hints from provider-returned model identifiers.  Hints remove
 * the need to understand vendor naming, but the UI still labels them as suggestions because an
 * identifier alone cannot prove a provider's actual entitlement or request format.
 */
fun suggestedCapabilities(modelId: String): Set<ApiCapability> {
    val value = modelId.trim().lowercase()
    if (value.isBlank() || isSpecialPurposeModel(value)) return emptySet()

    return buildSet {
        add(ApiCapability.Text)
        add(ApiCapability.StructuredJson)
        if (listOf("vision", "-vl", "_vl", "omni", "gemini", "gpt-4o", "image").any(value::contains)) {
            add(ApiCapability.Vision)
        }
        if (listOf("long", "context", "128k", "200k", "256k", "1m").any(value::contains)) {
            add(ApiCapability.LongContext)
        }
    }
}

private fun modelPriority(model: String): Int {
    val value = model.lowercase()
    return when {
        listOf("flash", "mini", "lite", "turbo", "small", "fast").any(value::contains) -> 0
        listOf("latest", "chat", "instruct", "assistant").any(value::contains) -> 1
        listOf("pro", "plus", "max", "large", "thinking", "reasoner").any(value::contains) -> 2
        else -> 3
    }
}

private fun isSpecialPurposeModel(model: String): Boolean {
    val value = model.lowercase()
    return listOf(
        "embedding",
        "rerank",
        "moderation",
        "whisper",
        "transcri",
        "speech",
        "tts",
        "audio",
        "dall-e",
        "image-generation",
    ).any(value::contains)
}
