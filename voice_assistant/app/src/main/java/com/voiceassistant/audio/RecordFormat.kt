package com.voiceassistant.audio

internal enum class RecordFormat(
    val id: String,
    val label: String,
    val extension: String,
) {
    WAV("wav", "WAV（兼容）", "wav"),
    M4A("m4a", "M4A（AAC）", "m4a"),
    ;

    companion object {
        fun fromId(raw: String?): RecordFormat {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == normalized } ?: M4A
        }
    }
}
