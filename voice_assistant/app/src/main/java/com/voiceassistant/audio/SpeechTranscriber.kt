package com.voiceassistant.audio

import android.content.Context
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.TranscriptionEngine

internal class SpeechTranscriber(context: Context) {
    private val whisper = WhisperManager(context)
    private val sherpa = SherpaManager(context)

    suspend fun transcribe(
        wavPath: String,
        request: TranscriptionRequest,
        deviceProfile: DeviceProfile,
        onProgress: ((String) -> Unit)? = null,
        onPartial: ((String) -> Unit)? = null,
    ): TranscriptionResult {
        return when (request.engine) {
            TranscriptionEngine.WHISPER -> whisper.transcribe(wavPath, request, deviceProfile, onProgress, onPartial)
            TranscriptionEngine.SHERPA_STREAMING,
            TranscriptionEngine.SHERPA_SENSEVOICE -> sherpa.transcribe(wavPath, request, deviceProfile, onProgress, onPartial)
        }
    }

    fun resolveWhisperModelPath(modelId: String): String? = whisper.resolveModelPath(modelId)

    fun release() {
        whisper.release()
        sherpa.release()
    }
}
