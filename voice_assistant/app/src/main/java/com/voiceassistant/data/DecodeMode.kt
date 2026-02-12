package com.voiceassistant.data

internal enum class DecodeMode {
    FAST,
    ACCURATE,
}

internal fun DecodeMode.labelZh(): String {
    return when (this) {
        DecodeMode.FAST -> "快速"
        DecodeMode.ACCURATE -> "准确"
    }
}
