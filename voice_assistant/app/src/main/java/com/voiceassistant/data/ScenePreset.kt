package com.voiceassistant.data

internal enum class ScenePreset(val id: String, val label: String) {
    NONE("none", "自定义"),
    MEETING("meeting", "会议"),
    OUTDOOR("outdoor", "户外"),
    LAB("lab", "实验室"),
    ;

    companion object {
        fun fromId(raw: String?): ScenePreset {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == normalized } ?: NONE
        }
    }
}

