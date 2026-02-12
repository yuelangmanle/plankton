package com.voiceassistant.audio

import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine

internal data class TranscriptionRequest(
    val engine: TranscriptionEngine,
    val modelId: String,
    val decodeMode: DecodeMode,
    val language: String,
    val useGpuPreference: Boolean,
    val autoStrategy: Boolean,
    val useMultithread: Boolean,
    val threadCount: Int,
    val sherpaProvider: SherpaProvider,
    val sherpaStreamingModel: SherpaStreamingModel,
    val sherpaOfflineModel: SherpaOfflineModel,
)

internal data class TranscriptionPlan(
    val modelId: String,
    val language: String,
    val useGpu: Boolean,
    val beamSize: Int,
    val bestOf: Int,
    val enableTimestamps: Boolean,
    val enableVad: Boolean,
    val minSegmentSec: Int,
    val maxSegmentSec: Int,
)

internal object TranscriptionStrategy {
    private const val MinSegmentSec = 20
    private const val MaxSegmentSec = 30

    fun resolve(
        device: DeviceProfile,
        request: TranscriptionRequest,
        audioDurationSec: Float,
    ): TranscriptionPlan {
        val normalizedModel = ModelCatalog.normalizeId(request.modelId)
        val effectiveModel = if (request.autoStrategy) {
            chooseModel(device, audioDurationSec)
        } else {
            normalizedModel
        }

        val effectiveLanguage = chooseLanguage(device, request.decodeMode, request.language)
        val effectiveGpu = if (request.autoStrategy) {
            shouldUseGpu(device, request.useGpuPreference, effectiveModel, audioDurationSec)
        } else {
            request.useGpuPreference
        }

        val decodeParams = decodeParams(request.decodeMode)
        val enableVad = audioDurationSec >= 30f

        return TranscriptionPlan(
            modelId = effectiveModel,
            language = effectiveLanguage,
            useGpu = effectiveGpu,
            beamSize = decodeParams.beamSize,
            bestOf = decodeParams.bestOf,
            enableTimestamps = decodeParams.enableTimestamps,
            enableVad = enableVad,
            minSegmentSec = MinSegmentSec,
            maxSegmentSec = MaxSegmentSec,
        )
    }

    private fun chooseLanguage(device: DeviceProfile, mode: DecodeMode, raw: String): String {
        val normalized = raw.trim().lowercase()
        if (normalized != "auto") return normalized
        if (mode != DecodeMode.FAST) return "auto"
        return if (device.localeLanguage.startsWith("zh")) "zh" else "en"
    }

    private fun chooseModel(device: DeviceProfile, durationSec: Float): String {
        val ramGb = device.totalRamGb
        val memoryClass = device.memoryClassMb
        val longAudio = durationSec >= 180f

        return when {
            ramGb < 8 || memoryClass < 384 -> "small-q8_0"
            ramGb < 12 || memoryClass < 512 -> "small-q8_0"
            longAudio -> "small-q8_0"
            else -> "small-q8_0"
        }
    }

    private fun shouldUseGpu(
        device: DeviceProfile,
        preferGpu: Boolean,
        modelId: String,
        durationSec: Float,
    ): Boolean {
        if (!preferGpu) return false
        if (!device.isGpuStable) return false
        val longAudio = durationSec >= 60f
        val largeModel = modelId.startsWith("small") || modelId.startsWith("medium")
        if (!longAudio || !largeModel) return false
        return device.memoryClassMb >= 512
    }

    private fun decodeParams(mode: DecodeMode): DecodeParams {
        return if (mode == DecodeMode.ACCURATE) {
            DecodeParams(beamSize = 5, bestOf = 5, enableTimestamps = true)
        } else {
            DecodeParams(beamSize = 1, bestOf = 1, enableTimestamps = false)
        }
    }

    private data class DecodeParams(
        val beamSize: Int,
        val bestOf: Int,
        val enableTimestamps: Boolean,
    )
}
