package com.voiceassistant.audio

import android.content.Context
import com.voiceassistant.data.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal data class ModelEnsureResult(
    val path: String? = null,
    val error: String? = null,
)

internal class WhisperManager(private val context: Context) {
    private val mutex = Mutex()
    private var handle: Long = 0
    private var modelPath: String? = null
    private var activeUseGpu: Boolean? = null

    suspend fun transcribe(
        wavPath: String,
        request: TranscriptionRequest,
        deviceProfile: DeviceProfile,
        onProgress: ((String) -> Unit)? = null,
        onPartial: ((String) -> Unit)? = null,
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                onProgress?.invoke("准备音频…")
                val prep = AudioPreprocessor.prepare(context, File(wavPath))
                val prepared = prep.audio ?: return@withLock TranscriptionResult(error = prep.error ?: "音频预处理失败")

                val plan = TranscriptionStrategy.resolve(deviceProfile, request, prepared.durationSec)

                val ensureResult = ensureModelInstalled(plan.modelId)
                val resolvedModel = ensureResult.path
                    ?: return@withLock TranscriptionResult(error = ensureResult.error ?: "模型文件不存在：${plan.modelId}")

                var actualUseGpu = plan.useGpu
                var ctx = initContext(resolvedModel, plan.useGpu)
                if (ctx == 0L && plan.useGpu) {
                    onProgress?.invoke("GPU 初始化失败，改用 CPU…")
                    actualUseGpu = false
                    ctx = initContext(resolvedModel, false)
                }

                if (ctx == 0L) {
                    return@withLock TranscriptionResult(error = WhisperBridge.nativeGetLastError() ?: "模型加载失败")
                }

                val segments = if (plan.enableVad) {
                    AudioSegmenter.split(
                        samples = prepared.samples,
                        sampleRate = prepared.sampleRate,
                        minSegmentSec = plan.minSegmentSec,
                        maxSegmentSec = plan.maxSegmentSec,
                    )
                } else {
                    listOf(AudioSegment(index = 0, startSample = 0, endSample = prepared.samples.size))
                }

                val usePreparedFileDirectly = segments.size == 1 &&
                    segments.first().startSample == 0 &&
                    segments.first().endSample >= prepared.samples.size

                val segmentFiles = if (usePreparedFileDirectly) {
                    listOf(prepared.file)
                } else {
                    AudioSegmenter.writeSegments(context, prepared.samples, prepared.sampleRate, segments)
                }

                val output = StringBuilder()
                try {
                    segmentFiles.forEachIndexed { index, segment ->
                        currentCoroutineContext().ensureActive()
                        onProgress?.invoke("分析中（${index + 1}/${segmentFiles.size}）")
                        val text = WhisperBridge.nativeTranscribe(
                            handle = ctx,
                            wavPath = segment.absolutePath,
                            language = plan.language,
                            mode = if (request.decodeMode == com.voiceassistant.data.DecodeMode.ACCURATE) 1 else 0,
                            beamSize = plan.beamSize,
                            bestOf = plan.bestOf,
                            enableTimestamps = plan.enableTimestamps,
                            useMultithread = request.useMultithread,
                            threadCount = request.threadCount,
                        )
                        if (text.isNullOrBlank()) {
                            return@withLock TranscriptionResult(
                                error = WhisperBridge.nativeGetLastError() ?: "转写失败",
                                usedGpu = actualUseGpu,
                                segmentCount = segmentFiles.size,
                                modelId = plan.modelId,
                                language = plan.language,
                            )
                        }
                        output.append(text.trim()).append(' ')
                        onPartial?.invoke(output.toString().trim())
                    }
                } finally {
                    if (!usePreparedFileDirectly) {
                        segmentFiles.forEach { runCatching { it.delete() } }
                    }
                    if (prepared.tempFile) {
                        runCatching { prepared.file.delete() }
                    }
                }

                if (output.isEmpty()) {
                    return@withLock TranscriptionResult(
                        error = WhisperBridge.nativeGetLastError() ?: "未识别到文本",
                        usedGpu = actualUseGpu,
                        segmentCount = segmentFiles.size,
                        modelId = plan.modelId,
                        language = plan.language,
                    )
                }

                return@withLock TranscriptionResult(
                    text = output.toString().trim(),
                    usedGpu = actualUseGpu,
                    segmentCount = segmentFiles.size,
                    modelId = plan.modelId,
                    language = plan.language,
                )
            }
        }
    }

    fun release() {
        if (handle != 0L) {
            WhisperBridge.nativeFree(handle)
            handle = 0L
            modelPath = null
            activeUseGpu = null
        }
    }

    fun resolveModelPath(model: String): String? {
        val spec = ModelCatalog.findById(model)
        val internal = File(context.filesDir, "models").resolve(spec.assetFile)
        if (internal.exists()) return internal.absolutePath

        val external = context.getExternalFilesDir("models")?.resolve(spec.assetFile)
        if (external?.exists() == true) return external.absolutePath

        return null
    }

    private fun ensureModelInstalled(model: String): ModelEnsureResult {
        val spec = ModelCatalog.findById(model)
        val internalDir = File(context.filesDir, "models").apply { mkdirs() }
        val internal = File(internalDir, spec.assetFile)
        if (internal.exists()) return ModelEnsureResult(path = internal.absolutePath)

        val external = context.getExternalFilesDir("models")?.resolve(spec.assetFile)
        if (external?.exists() == true) return ModelEnsureResult(path = external.absolutePath)

        val assetPath = "models/${spec.assetFile}"
        return runCatching {
            context.assets.open(assetPath).use { input ->
                internal.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            ModelEnsureResult(path = internal.absolutePath)
        }.getOrElse { ex ->
            ModelEnsureResult(error = "模型复制失败：${ex.message}")
        }
    }

    private fun initContext(model: String, useGpu: Boolean): Long {
        if (handle != 0L && modelPath == model && activeUseGpu == useGpu) {
            return handle
        }
        if (handle != 0L) {
            WhisperBridge.nativeFree(handle)
            handle = 0L
        }
        val newHandle = WhisperBridge.nativeInit(model, useGpu)
        if (newHandle != 0L) {
            handle = newHandle
            modelPath = model
            activeUseGpu = useGpu
        }
        return newHandle
    }
}
