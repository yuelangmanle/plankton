package com.voiceassistant.audio

internal object WhisperBridge {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun nativeInit(modelPath: String, useGpu: Boolean): Long
    external fun nativeFree(handle: Long)
    external fun nativeTranscribe(
        handle: Long,
        wavPath: String,
        language: String,
        mode: Int,
        beamSize: Int,
        bestOf: Int,
        enableTimestamps: Boolean,
        useMultithread: Boolean,
        threadCount: Int,
    ): String?
    external fun nativeGetLastError(): String?
}
