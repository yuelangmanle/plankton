package com.voiceassistant.audio

internal data class ModelSpec(
    val id: String,
    val label: String,
    val assetFile: String,
    val quantized: Boolean,
)

internal object ModelCatalog {
    private val items = listOf(
        ModelSpec(
            id = "small-q8_0",
            label = "small-q8_0",
            assetFile = "ggml-small-q8_0.bin",
            quantized = true,
        ),
    )

    val models: List<ModelSpec> = items

    val defaultModelId: String = "small-q8_0"

    fun normalizeId(raw: String): String {
        val value = raw.trim().lowercase()
        if (items.any { it.id == value }) return value
        return when (value) {
            "base" -> "small-q8_0"
            "base-q5_1" -> "small-q8_0"
            "base-q8_0" -> "small-q8_0"
            "small" -> "small-q8_0"
            "small-q5_1" -> "small-q8_0"
            "medium" -> "small-q8_0"
            "medium-q5_1" -> "small-q8_0"
            "medium-q5_0" -> "small-q8_0"
            "medium-q8_0" -> "small-q8_0"
            else -> defaultModelId
        }
    }

    fun findById(raw: String): ModelSpec {
        val normalized = normalizeId(raw)
        return items.firstOrNull { it.id == normalized } ?: items.first()
    }
}
