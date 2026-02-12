package com.voiceassistant.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.CoroutineContext

internal class SherpaManager(private val context: Context) {
    private val mutex = Mutex()
    private var onlineRecognizer: OnlineRecognizer? = null
    private var offlineRecognizer: OfflineRecognizer? = null
    private var onlineKey: OnlineKey? = null
    private var offlineKey: OfflineKey? = null

    suspend fun transcribe(
        wavPath: String,
        request: TranscriptionRequest,
        deviceProfile: DeviceProfile,
        onProgress: ((String) -> Unit)? = null,
        onPartial: ((String) -> Unit)? = null,
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            val jobContext = currentCoroutineContext()
            mutex.withLock {
                onProgress?.invoke("准备音频…")
                val prep = AudioPreprocessor.prepare(context, File(wavPath))
                val prepared = prep.audio ?: return@withLock TranscriptionResult(error = prep.error ?: "音频预处理失败")

                val threads = request.threadCount.coerceAtLeast(1)
                return@withLock try {
                    when (request.engine) {
                        TranscriptionEngine.SHERPA_STREAMING -> {
                            runStreaming(prepared, request, deviceProfile, threads, jobContext, onProgress, onPartial)
                        }
                        TranscriptionEngine.SHERPA_SENSEVOICE -> {
                            runOffline(prepared, request, deviceProfile, threads, onProgress)
                        }
                        else -> TranscriptionResult(error = "未选择 Sherpa 引擎")
                    }
                } finally {
                    if (prepared.tempFile) {
                        runCatching { prepared.file.delete() }
                    }
                }
            }
        }
    }

    fun release() {
        onlineRecognizer?.release()
        offlineRecognizer?.release()
        onlineRecognizer = null
        offlineRecognizer = null
        onlineKey = null
        offlineKey = null
    }

    private fun runStreaming(
        prepared: PreparedAudio,
        request: TranscriptionRequest,
        deviceProfile: DeviceProfile,
        threads: Int,
        jobContext: CoroutineContext,
        onProgress: ((String) -> Unit)?,
        onPartial: ((String) -> Unit)?,
    ): TranscriptionResult {
        onProgress?.invoke("加载流式模型…")
        val streamingModel = request.sherpaStreamingModel
        val assets = SherpaAssets.ensureStreamingModel(context, streamingModel)
        val paths = assets.value ?: return TranscriptionResult(error = assets.error ?: "模型文件不存在")

        val providerPlan = resolveProvider(request.sherpaProvider, deviceProfile)
        val recognizerResult = getOnlineRecognizer(paths, streamingModel, providerPlan, threads, request.decodeMode)
            ?: if (providerPlan == SherpaProvider.NNAPI) {
                onProgress?.invoke("NNAPI 初始化失败，改用 CPU…")
                getOnlineRecognizer(paths, streamingModel, SherpaProvider.CPU, threads, request.decodeMode)
            } else {
                null
            }

        val recognizer = recognizerResult?.recognizer
            ?: return TranscriptionResult(error = "模型初始化失败")
        val providerUsed = recognizerResult.provider

        val stream = recognizer.createStream()
        try {
            val samples = prepared.samples
            val total = samples.size
            val chunkSize = (prepared.sampleRate * 0.2f).roundToInt().coerceAtLeast(800)
            var offset = 0
            var lastProgress = -1

            while (offset < total) {
                jobContext.ensureActive()
                val end = min(total, offset + chunkSize)
                val chunk = samples.copyOfRange(offset, end)
                stream.acceptWaveform(chunk, prepared.sampleRate)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
                val partial = recognizer.getResult(stream).text
                if (!partial.isNullOrBlank()) {
                    onPartial?.invoke(partial.trim())
                }
                offset = end
                val progress = ((offset.toFloat() / total.toFloat()) * 100f).roundToInt()
                if (progress != lastProgress && progress % 5 == 0) {
                    onProgress?.invoke("分析中（$progress%）")
                    lastProgress = progress
                }
            }

            stream.inputFinished()
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            val finalText = recognizer.getResult(stream).text.trim()
            if (finalText.isBlank()) {
                return TranscriptionResult(
                    error = "未识别到文本",
                    usedGpu = providerUsed == SherpaProvider.NNAPI,
                    segmentCount = 1,
                    modelId = streamingModel.id,
                    language = request.language,
                )
            }
            return TranscriptionResult(
                text = finalText,
                usedGpu = providerUsed == SherpaProvider.NNAPI,
                segmentCount = 1,
                modelId = streamingModel.id,
                language = request.language,
            )
        } finally {
            stream.release()
        }
    }

    private fun runOffline(
        prepared: PreparedAudio,
        request: TranscriptionRequest,
        deviceProfile: DeviceProfile,
        threads: Int,
        onProgress: ((String) -> Unit)?,
    ): TranscriptionResult {
        onProgress?.invoke("加载高精度模型…")
        val offlineModel = request.sherpaOfflineModel
        val assets = SherpaAssets.ensureOfflineModel(context, offlineModel)
        val paths = assets.value ?: return TranscriptionResult(error = assets.error ?: "模型文件不存在")

        val safeMode = if (offlineModel == SherpaOfflineModel.SENSE_VOICE &&
            request.decodeMode == DecodeMode.ACCURATE
        ) {
            onProgress?.invoke("准确模式已降级以保证稳定…")
            DecodeMode.FAST
        } else {
            request.decodeMode
        }
        val providerPlan = resolveProvider(request.sherpaProvider, deviceProfile)
        val recognizerResult = getOfflineRecognizer(offlineModel, paths, providerPlan, threads, safeMode, request.language)
            ?: if (providerPlan == SherpaProvider.NNAPI) {
                onProgress?.invoke("NNAPI 初始化失败，改用 CPU…")
                getOfflineRecognizer(offlineModel, paths, SherpaProvider.CPU, threads, safeMode, request.language)
            } else {
                null
            }

        val recognizer = recognizerResult?.recognizer
            ?: return TranscriptionResult(error = "模型初始化失败")
        val providerUsed = recognizerResult.provider

        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(prepared.samples, prepared.sampleRate)
            onProgress?.invoke("分析中…")
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val finalText = result.text.trim()
            if (finalText.isBlank()) {
                return TranscriptionResult(
                    error = "未识别到文本",
                    usedGpu = providerUsed == SherpaProvider.NNAPI,
                    segmentCount = 1,
                    modelId = offlineModel.id,
                    language = result.lang.ifBlank { request.language },
                )
            }
            return TranscriptionResult(
                text = finalText,
                usedGpu = providerUsed == SherpaProvider.NNAPI,
                segmentCount = 1,
                modelId = offlineModel.id,
                language = result.lang.ifBlank { request.language },
            )
        } finally {
            stream.release()
        }
    }

    private fun resolveProvider(provider: SherpaProvider, deviceProfile: DeviceProfile): SherpaProvider {
        if (provider == SherpaProvider.NNAPI && !deviceProfile.isGpuStable) {
            return SherpaProvider.CPU
        }
        return provider
    }

    private fun getOnlineRecognizer(
        paths: SherpaTransducerPaths,
        model: SherpaStreamingModel,
        provider: SherpaProvider,
        threads: Int,
        mode: DecodeMode,
    ): OnlineRecognizerHolder? {
        val key = OnlineKey(model.id, provider, threads, mode)
        val current = onlineRecognizer
        if (current != null && onlineKey == key) {
            return OnlineRecognizerHolder(current, provider)
        }
        onlineRecognizer?.release()
        onlineRecognizer = null
        onlineKey = null

        val transducerConfig = OnlineTransducerModelConfig().apply {
            encoder = paths.encoder
            decoder = paths.decoder
            joiner = paths.joiner
        }
        val modelConfig = OnlineModelConfig().apply {
            transducer = transducerConfig
            tokens = paths.tokens
            numThreads = threads
            this.provider = provider.providerName
        }
        val featureConfig = FeatureConfig().apply {
            sampleRate = 16000
            featureDim = 80
            dither = 0f
        }
        val config = OnlineRecognizerConfig().apply {
            featConfig = featureConfig
            this.modelConfig = modelConfig
            enableEndpoint = true
            decodingMethod = if (mode == DecodeMode.ACCURATE) "modified_beam_search" else "greedy_search"
            maxActivePaths = if (mode == DecodeMode.ACCURATE) 8 else 2
        }

        return runCatching {
            val recognizer = OnlineRecognizer(context.assets, config)
            onlineRecognizer = recognizer
            onlineKey = key
            OnlineRecognizerHolder(recognizer, provider)
        }.getOrNull()
    }

    private fun getOfflineRecognizer(
        model: SherpaOfflineModel,
        paths: SherpaOfflinePaths,
        provider: SherpaProvider,
        threads: Int,
        mode: DecodeMode,
        language: String,
    ): OfflineRecognizerHolder? {
        val key = OfflineKey(model.id, provider, threads, mode, language)
        val current = offlineRecognizer
        if (current != null && offlineKey == key) {
            return OfflineRecognizerHolder(current, provider)
        }
        offlineRecognizer?.release()
        offlineRecognizer = null
        offlineKey = null

        val modelConfig = OfflineModelConfig().apply {
            when (model) {
                SherpaOfflineModel.SENSE_VOICE -> {
                    val senseVoiceConfig = OfflineSenseVoiceModelConfig().apply {
                        this.model = paths.model
                        this.language = language
                        useInverseTextNormalization = true
                    }
                    senseVoice = senseVoiceConfig
                }
                SherpaOfflineModel.PARAFORMER_ZH -> {
                    val paraformerConfig = OfflineParaformerModelConfig().apply {
                        this.model = paths.model
                    }
                    paraformer = paraformerConfig
                }
            }
            tokens = paths.tokens
            numThreads = threads
            this.provider = provider.providerName
        }
        val featureConfig = FeatureConfig().apply {
            sampleRate = 16000
            featureDim = 80
            dither = 0f
        }
        val config = OfflineRecognizerConfig().apply {
            featConfig = featureConfig
            this.modelConfig = modelConfig
            when (model) {
                SherpaOfflineModel.SENSE_VOICE -> {
                    decodingMethod = "greedy_search"
                    maxActivePaths = if (mode == DecodeMode.ACCURATE) 2 else 1
                }
                SherpaOfflineModel.PARAFORMER_ZH -> {
                    decodingMethod = if (mode == DecodeMode.ACCURATE) "modified_beam_search" else "greedy_search"
                    maxActivePaths = if (mode == DecodeMode.ACCURATE) 4 else 1
                }
            }
        }

        return runCatching {
            val recognizer = OfflineRecognizer(context.assets, config)
            offlineRecognizer = recognizer
            offlineKey = key
            OfflineRecognizerHolder(recognizer, provider)
        }.getOrNull()
    }

    private data class OnlineKey(
        val modelId: String,
        val provider: SherpaProvider,
        val threads: Int,
        val decodeMode: DecodeMode,
    )

    private data class OfflineKey(
        val modelId: String,
        val provider: SherpaProvider,
        val threads: Int,
        val decodeMode: DecodeMode,
        val language: String,
    )

    private data class OnlineRecognizerHolder(
        val recognizer: OnlineRecognizer,
        val provider: SherpaProvider,
    )

    private data class OfflineRecognizerHolder(
        val recognizer: OfflineRecognizer,
        val provider: SherpaProvider,
    )
}
