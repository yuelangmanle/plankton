package com.voiceassistant.audio

internal data class TranscriptionResult(
    val text: String? = null,
    val error: String? = null,
    val usedGpu: Boolean = false,
    val segmentCount: Int = 0,
    val modelId: String? = null,
    val language: String? = null,
)
