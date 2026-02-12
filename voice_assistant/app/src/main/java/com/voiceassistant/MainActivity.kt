package com.voiceassistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.voiceassistant.BuildConfig
import com.voiceassistant.audio.AudioQualityInspector
import com.voiceassistant.audio.AudioCacheCleaner
import com.voiceassistant.audio.AudioImport
import com.voiceassistant.audio.AudioShare
import com.voiceassistant.audio.ModelCatalog
import com.voiceassistant.audio.RecordFormat
import com.voiceassistant.audio.SherpaAssets
import com.voiceassistant.audio.SpeechTranscriber
import com.voiceassistant.audio.TranscriptionRequest
import com.voiceassistant.audio.VoiceRecorder
import com.voiceassistant.bridge.BridgeRequest
import com.voiceassistant.bridge.VoiceAssistantContract
import com.voiceassistant.bridge.buildRequestIntent
import com.voiceassistant.data.AuthStore
import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.ProcessingMode
import com.voiceassistant.data.ScenePreset
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine
import com.voiceassistant.data.maxParallel
import com.voiceassistant.data.labelZh
import com.voiceassistant.ui.AppInfo
import com.voiceassistant.ui.components.GlassBackground
import com.voiceassistant.ui.components.GlassCard
import com.voiceassistant.ui.components.GlassPrefs
import com.voiceassistant.ui.components.LocalGlassPrefs
import com.voiceassistant.ui.theme.VoiceAssistantTheme
import com.voiceassistant.ui.theme.GlassWhite
import com.voiceassistant.text.TextConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableHighRefreshRate(window)
        setContent {
            VoiceAssistantTheme {
                MainScreen()
            }
        }
    }
}

private fun enableHighRefreshRate(window: Window) {
    val display = window.decorView.display ?: return
    val mode = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
    val attrs = window.attributes
    if (attrs.preferredDisplayModeId != mode.modeId) {
        attrs.preferredDisplayModeId = mode.modeId
        window.attributes = attrs
    }
}

private data class MainTranscriptionTask(
    val id: String,
    val file: File,
    val label: String,
    val requestOverride: TranscriptionRequest? = null,
    val compareGroupId: String? = null,
    val compareMode: String? = null,
    val preflightReport: AudioQualityInspector.Report? = null,
)

private enum class TaskStatus(val label: String) {
    Pending("待处理"),
    Running("分析中"),
    Completed("完成"),
    Failed("失败"),
    Cancelled("已取消"),
}

private data class MainTaskResult(
    val id: String,
    val label: String,
    val status: TaskStatus,
    val text: String = "",
    val message: String? = null,
    val compareGroupId: String? = null,
    val compareMode: String? = null,
    val qualityScore: Int? = null,
    val qualityLabel: String? = null,
)

private data class QualityAssessment(
    val score: Int,
    val label: String,
    val reason: String,
)

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context) }
    val allowedApps by authStore.allowedAppsFlow.collectAsState(initial = emptyList())
    val model by authStore.selectedModelFlow.collectAsState(initial = "small-q8_0")
    val engine by authStore.engineFlow.collectAsState(initial = TranscriptionEngine.WHISPER)
    val processingMode by authStore.processingModeFlow.collectAsState(initial = ProcessingMode.QUEUE)
    val useGpu by authStore.useGpuFlow.collectAsState(initial = true)
    val decodeMode by authStore.decodeModeFlow.collectAsState(initial = DecodeMode.FAST)
    val autoStrategy by authStore.autoStrategyFlow.collectAsState(initial = true)
    val useMultithread by authStore.multiThreadFlow.collectAsState(initial = true)
    val threadCount by authStore.threadCountFlow.collectAsState(initial = 0)
    val recordFormatId by authStore.recordFormatFlow.collectAsState(initial = "m4a")
    val sherpaProvider by authStore.sherpaProviderFlow.collectAsState(initial = SherpaProvider.CPU)
    val sherpaStreamingModel by authStore.sherpaStreamingModelFlow.collectAsState(initial = SherpaStreamingModel.ZIPFORMER_ZH)
    val sherpaOfflineModel by authStore.sherpaOfflineModelFlow.collectAsState(initial = SherpaOfflineModel.SENSE_VOICE)
    val glassEnabled by authStore.glassEnabledFlow.collectAsState(initial = true)
    val blurEnabled by authStore.blurEnabledFlow.collectAsState(initial = true)
    val glassOpacity by authStore.glassOpacityFlow.collectAsState(initial = 1f)
    val compactUiMode by authStore.compactUiModeFlow.collectAsState(initial = true)
    val scenePreset by authStore.scenePresetFlow.collectAsState(initial = ScenePreset.NONE)
    val deviceProfile = remember { DeviceProfile.from(context) }
    val recorder = remember { VoiceRecorder(context) }
    val transcriber = remember { SpeechTranscriber(context) }

    DisposableEffect(Unit) {
        onDispose { transcriber.release() }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recordGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    val notifyGranted = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true else
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recordGranted.value = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifyGranted.value = granted
    }

    var engineMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var sherpaStreamingMenuOpen by remember { mutableStateOf(false) }
    var sherpaOfflineMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var threadMenuOpen by remember { mutableStateOf(false) }
    var recordFormatMenuOpen by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("auto") }
    val normalizedModel = remember(model) { ModelCatalog.normalizeId(model) }
    val recordFormat = remember(recordFormatId) { RecordFormat.fromId(recordFormatId) }
    var pendingModel by remember(normalizedModel) { mutableStateOf(normalizedModel) }
    var overlayRunning by remember { mutableStateOf(false) }
    var clearDialogOpen by remember { mutableStateOf(false) }
    var clearAudioDialogOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var recordElapsedMs by remember { mutableStateOf(0L) }
    var recordPaused by remember { mutableStateOf(false) }
    var pauseStartMs by remember { mutableStateOf(0L) }
    var pausedDurationMs by remember { mutableStateOf(0L) }
    var recordMessage by remember { mutableStateOf<String?>(null) }
    var recordPath by remember { mutableStateOf<String?>(null) }
    var recordUri by remember { mutableStateOf<String?>(null) }
    val pendingTasks = remember { mutableStateListOf<MainTranscriptionTask>() }
    val runningTasks = remember { mutableStateListOf<MainTranscriptionTask>() }
    val taskResults = remember { mutableStateListOf<MainTaskResult>() }
    val taskJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }
    var transcribeMessage by remember { mutableStateOf<String?>(null) }
    var preflightRunning by remember { mutableStateOf(false) }
    var preflightReport by remember { mutableStateOf<AudioQualityInspector.Report?>(null) }
    var showAdvancedConfig by rememberSaveable(compactUiMode) { mutableStateOf(!compactUiMode) }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    fun updateTaskResult(id: String, updater: (MainTaskResult) -> MainTaskResult) {
        val index = taskResults.indexOfFirst { it.id == id }
        if (index >= 0) {
            taskResults[index] = updater(taskResults[index])
        }
    }

    fun buildRequest(
        decodeOverride: DecodeMode? = null,
        gpuOverride: Boolean? = null,
        providerOverride: SherpaProvider? = null,
        modelOverride: String? = null,
        threadOverride: Int? = null,
        engineOverride: TranscriptionEngine? = null,
    ): TranscriptionRequest {
        val activeEngine = engineOverride ?: engine
        return TranscriptionRequest(
            engine = activeEngine,
            modelId = modelOverride ?: normalizedModel,
            decodeMode = decodeOverride ?: decodeMode,
            language = selectedLanguage,
            useGpuPreference = gpuOverride ?: useGpu,
            autoStrategy = autoStrategy,
            useMultithread = useMultithread,
            threadCount = if (useMultithread) (threadOverride ?: threadCount) else 1,
            sherpaProvider = providerOverride ?: sherpaProvider,
            sherpaStreamingModel = sherpaStreamingModel,
            sherpaOfflineModel = sherpaOfflineModel,
        )
    }

    fun applyScenePreset(preset: ScenePreset) {
        scope.launch {
            when (preset) {
                ScenePreset.MEETING -> {
                    authStore.setEngine(TranscriptionEngine.WHISPER)
                    authStore.setDecodeMode(DecodeMode.FAST)
                    authStore.setProcessingMode(ProcessingMode.QUEUE)
                    authStore.setUseGpu(true)
                    authStore.setMultiThread(true)
                    authStore.setThreadCount(4)
                }
                ScenePreset.OUTDOOR -> {
                    authStore.setEngine(TranscriptionEngine.WHISPER)
                    authStore.setDecodeMode(DecodeMode.ACCURATE)
                    authStore.setProcessingMode(ProcessingMode.QUEUE)
                    authStore.setUseGpu(false)
                    authStore.setMultiThread(true)
                    authStore.setThreadCount(2)
                }
                ScenePreset.LAB -> {
                    authStore.setEngine(TranscriptionEngine.SHERPA_SENSEVOICE)
                    authStore.setDecodeMode(DecodeMode.ACCURATE)
                    authStore.setProcessingMode(ProcessingMode.QUEUE)
                    authStore.setSherpaProvider(if (deviceProfile.isGpuStable) SherpaProvider.NNAPI else SherpaProvider.CPU)
                    authStore.setMultiThread(true)
                    authStore.setThreadCount(4)
                }
                ScenePreset.NONE -> Unit
            }
            authStore.setScenePreset(preset)
            transcribeMessage = "已应用场景预设：${preset.label}"
        }
    }

    fun makeRetryRequest(base: TranscriptionRequest, error: String?): Pair<TranscriptionRequest, String>? {
        val err = error?.lowercase().orEmpty()
        if (base.engine == TranscriptionEngine.WHISPER) {
            if (base.useGpuPreference && (err.contains("gpu") || err.contains("vulkan") || err.contains("init") || err.contains("oom"))) {
                return base.copy(useGpuPreference = false) to "自动重试：GPU→CPU"
            }
            if (base.modelId != "small-q8_0") {
                return base.copy(modelId = "small-q8_0") to "自动重试：切换 small-q8_0"
            }
            if (base.decodeMode == DecodeMode.ACCURATE) {
                return base.copy(decodeMode = DecodeMode.FAST) to "自动重试：准确→快速"
            }
            return null
        }
        if (base.sherpaProvider == SherpaProvider.NNAPI) {
            return base.copy(sherpaProvider = SherpaProvider.CPU) to "自动重试：NNAPI→CPU"
        }
        if (base.decodeMode == DecodeMode.ACCURATE) {
            return base.copy(decodeMode = DecodeMode.FAST) to "自动重试：准确→快速"
        }
        return null
    }

    fun startNextTasks() {
        val limit = processingMode.maxParallel(deviceProfile, autoStrategy)
        while (runningTasks.size < limit && pendingTasks.isNotEmpty()) {
            val task = pendingTasks.removeAt(0)
            runningTasks.add(task)
            val modeAtStart = processingMode
            val engineAtStart = engine
            val sherpaProviderAtStart = sherpaProvider
            val sherpaStreamingAtStart = sherpaStreamingModel
            val sherpaOfflineAtStart = sherpaOfflineModel
            val modelAtStart = normalizedModel
            updateTaskResult(task.id) { it.copy(status = TaskStatus.Running, message = "分析中…") }
            val job = scope.launch {
                transcribeMessage = "分析中：${task.label}"
                val manager = if (modeAtStart == ProcessingMode.PARALLEL) {
                    SpeechTranscriber(context)
                } else {
                    transcriber
                }
                try {
                    var request = task.requestOverride ?: TranscriptionRequest(
                        engine = engineAtStart,
                        modelId = modelAtStart,
                        decodeMode = decodeMode,
                        language = selectedLanguage,
                        useGpuPreference = useGpu,
                        autoStrategy = autoStrategy,
                        useMultithread = useMultithread,
                        threadCount = if (useMultithread) threadCount else 1,
                        sherpaProvider = sherpaProviderAtStart,
                        sherpaStreamingModel = sherpaStreamingAtStart,
                        sherpaOfflineModel = sherpaOfflineAtStart,
                    )
                    var result = manager.transcribe(
                        wavPath = task.file.absolutePath,
                        request = request,
                        deviceProfile = deviceProfile,
                        onProgress = { progressText ->
                            transcribeMessage = "${task.label} · $progressText"
                            updateTaskResult(task.id) { it.copy(message = progressText) }
                        },
                        onPartial = { partialText ->
                            updateTaskResult(task.id) {
                                it.copy(text = TextConverters.formatTranscript(partialText, applyPunctuation = false, applySimplify = false))
                            }
                        },
                    )
                    var retryHint: String? = null
                    if (result.error != null) {
                        val retry = makeRetryRequest(request, result.error)
                        if (retry != null) {
                            request = retry.first
                            retryHint = retry.second
                            transcribeMessage = "${task.label} · $retryHint"
                            updateTaskResult(task.id) { it.copy(message = retryHint) }
                            result = manager.transcribe(
                                wavPath = task.file.absolutePath,
                                request = request,
                                deviceProfile = deviceProfile,
                                onProgress = { progressText ->
                                    transcribeMessage = "${task.label} · 重试中 · $progressText"
                                    updateTaskResult(task.id) { it.copy(message = "重试中：$progressText") }
                                },
                                onPartial = { partialText ->
                                    updateTaskResult(task.id) {
                                        it.copy(text = TextConverters.formatTranscript(partialText, applyPunctuation = false, applySimplify = false))
                                    }
                                },
                            )
                        }
                    }
                    if (result.error != null) {
                        val failMsg = if (retryHint == null) result.error else "${result.error}（$retryHint）"
                        transcribeMessage = "分析失败：$failMsg"
                        updateTaskResult(task.id) { it.copy(status = TaskStatus.Failed, message = failMsg) }
                    } else {
                        val accelTag = when (engineAtStart) {
                            TranscriptionEngine.WHISPER -> if (result.usedGpu) "GPU" else "CPU"
                            else -> if (result.usedGpu) "NNAPI" else "CPU"
                        }
                        val segmentTag = if (result.segmentCount > 1) " · 分段${result.segmentCount}" else ""
                        val modelTag = result.modelId?.let { " · 模型$it" } ?: ""
                        val langTag = result.language?.let {
                            when (it) {
                                "zh" -> " · 中文"
                                "en" -> " · 英文"
                                "auto" -> ""
                                else -> " · $it"
                            }
                        } ?: ""
                        val retryTag = retryHint?.let { " · $it" } ?: ""
                        val doneMessage = "分析完成（$accelTag$segmentTag$modelTag$langTag$retryTag）"
                        val qa = assessTranscriptionQuality(result.text.orEmpty(), result.segmentCount, task.preflightReport)
                        transcribeMessage = doneMessage
                        updateTaskResult(task.id) {
                            it.copy(
                                status = TaskStatus.Completed,
                                text = TextConverters.formatTranscript(result.text.orEmpty()),
                                message = doneMessage,
                                qualityScore = qa.score,
                                qualityLabel = qa.label,
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    transcribeMessage = "已取消：${task.label}"
                    updateTaskResult(task.id) { it.copy(status = TaskStatus.Cancelled, message = "已取消") }
                } finally {
                    if (modeAtStart == ProcessingMode.PARALLEL) {
                        manager.release()
                    }
                    taskJobs.remove(task.id)
                    runningTasks.remove(task)
                    startNextTasks()
                }
            }
            taskJobs[task.id] = job
        }
    }

    fun enqueueTranscription(
        file: File,
        label: String,
        requestOverride: TranscriptionRequest? = null,
        compareGroupId: String? = null,
        compareMode: String? = null,
    ) {
        val id = UUID.randomUUID().toString()
        pendingTasks.add(
            MainTranscriptionTask(
                id = id,
                file = file,
                label = label,
                requestOverride = requestOverride,
                compareGroupId = compareGroupId,
                compareMode = compareMode,
                preflightReport = preflightReport,
            ),
        )
        taskResults.add(
            0,
            MainTaskResult(
                id = id,
                label = label,
                status = TaskStatus.Pending,
                compareGroupId = compareGroupId,
                compareMode = compareMode,
            ),
        )
        startNextTasks()
    }

    fun enqueueQuickVsAccurate(file: File, label: String) {
        val groupId = UUID.randomUUID().toString()
        val quickReq = buildRequest(decodeOverride = DecodeMode.FAST)
        val accurateReq = buildRequest(decodeOverride = DecodeMode.ACCURATE)
        enqueueTranscription(
            file = file,
            label = "$label · 快速",
            requestOverride = quickReq,
            compareGroupId = groupId,
            compareMode = "快速",
        )
        enqueueTranscription(
            file = file,
            label = "$label · 准确",
            requestOverride = accurateReq,
            compareGroupId = groupId,
            compareMode = "准确",
        )
        transcribeMessage = "已创建对比任务：快速 vs 准确"
    }

    fun cancelAllTranscriptions() {
        if (pendingTasks.isEmpty() && runningTasks.isEmpty()) return
        pendingTasks.forEach { task ->
            updateTaskResult(task.id) { it.copy(status = TaskStatus.Cancelled, message = "已取消") }
        }
        runningTasks.forEach { task ->
            updateTaskResult(task.id) { it.copy(status = TaskStatus.Cancelled, message = "已取消") }
        }
        pendingTasks.clear()
        runningTasks.clear()
        taskJobs.values.forEach { it.cancel() }
        taskJobs.clear()
        transcribeMessage = "已取消全部转写任务"
    }

    fun clearTranscriptionResults() {
        taskResults.clear()
        transcribeMessage = null
    }

    fun clearAudioCache() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { AudioCacheCleaner.clear(context) }
            recordPath = null
            recordUri = null
            recordMessage = if (result.errors.isEmpty()) {
                "已清除音频缓存"
            } else {
                "音频缓存清理完成（${result.errors.size}处失败）"
            }
            transcribeMessage = null
        }
    }

    LaunchedEffect(processingMode) {
        startNextTasks()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            transcribeMessage = "导入中…"
            val result = AudioImport.importToWav(context, uri)
            if (result.file == null) {
                transcribeMessage = result.error ?: "导入失败"
                return@launch
            }
            recordPath = result.file.absolutePath
            recordUri = AudioShare.toShareUri(context, result.file).toString()
            recordMessage = "已导入：${result.sourceName ?: result.file.name}"
            transcribeMessage = "音频已就绪，点击“开始分析”"
        }
    }

    suspend fun runPreflightCheck(showToastWhenWarn: Boolean = false): AudioQualityInspector.Report? {
        if (!recordGranted.value) return null
        preflightRunning = true
        val report = withContext(Dispatchers.IO) { AudioQualityInspector.inspect(context) }
        preflightRunning = false
        preflightReport = report
        if (report != null) {
            val detail = "RMS ${"%.1f".format(report.rmsDb)} dB · 峰值 ${(report.peakNorm * 100).toInt()}% · 削波 ${(report.clipRatio * 100).toInt()}%"
            recordMessage = "录音前质检：$detail\n${report.recommendation}"
            if (showToastWhenWarn && report.shouldRetry) {
                Toast.makeText(context, "建议调整后再录音：${report.recommendation}", Toast.LENGTH_SHORT).show()
            }
        } else {
            recordMessage = "录音前质检不可用（设备不支持或权限不足）"
        }
        return report
    }

    fun startRecording(): Boolean {
        if (!recordGranted.value) {
            Toast.makeText(context, "请先授权录音权限", Toast.LENGTH_SHORT).show()
            return false
        }
        if (recording) return false
        val started = recorder.start(recordFormat)
        if (started) {
            recordStartMs = SystemClock.elapsedRealtime()
            recording = true
            recordPaused = false
            pausedDurationMs = 0L
            recordMessage = "录音中…"
            recordPath = null
            recordUri = null
            transcribeMessage = null
        } else {
            recordMessage = "录音启动失败"
        }
        return started
    }

    fun pauseRecording() {
        if (!recording || recordPaused) return
        if (recorder.pause()) {
            recordPaused = true
            pauseStartMs = SystemClock.elapsedRealtime()
            recordMessage = "录音暂停"
        }
    }

    fun resumeRecording() {
        if (!recording || !recordPaused) return
        if (recorder.resume()) {
            val now = SystemClock.elapsedRealtime()
            pausedDurationMs += (now - pauseStartMs).coerceAtLeast(0)
            recordPaused = false
            recordMessage = "录音中…"
        }
    }

    fun stopRecording() {
        if (!recording) return
        scope.launch {
            val result = recorder.stop()
            recording = false
            recordPaused = false
            pausedDurationMs = 0L
            recordPath = result.file?.absolutePath
            recordUri = result.file?.let { AudioShare.toShareUri(context, it).toString() }
            recordMessage = if (result.error == null) {
                "录音完成：${formatDuration(result.durationMs)}"
            } else {
                "录音失败：${result.error}"
            }
            if (result.error == null && result.file != null) {
                transcribeMessage = "音频已就绪，点击“开始分析”"
            }
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (recording) {
                val now = SystemClock.elapsedRealtime()
                val pausedExtra = if (recordPaused) (now - pauseStartMs).coerceAtLeast(0) else 0L
                recordElapsedMs = (now - recordStartMs - pausedDurationMs - pausedExtra).coerceAtLeast(0)
                delay(200)
            }
        } else {
            recordElapsedMs = 0
        }
    }

    val showAdvancedPanels = !compactUiMode || showAdvancedConfig
    val compactEffective = compactUiMode && !showAdvancedPanels
    val sectionGap = if (compactEffective) 8.dp else 12.dp
    val blockPadding = if (compactEffective) 10.dp else 12.dp

    CompositionLocalProvider(LocalGlassPrefs provides GlassPrefs(glassEnabled, blurEnabled, glassOpacity)) {
        if (showSettings) {
            VoiceSettingsScreen(onBack = { showSettings = false })
        } else {
            GlassBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(sectionGap),
                ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("语音识别助手", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("设置")
            }
        }
        Text(
            "Whisper + Sherpa 本地转写 + App 接入",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        if (compactEffective) {
            Text(
                "紧凑模式已启用：仅保留核心操作，高级参数已折叠。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(blockPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("场景预设", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScenePreset.entries.filter { it != ScenePreset.NONE }.forEach { preset ->
                        if (scenePreset == preset) {
                            Button(onClick = { applyScenePreset(preset) }) { Text(preset.label) }
                        } else {
                            OutlinedButton(onClick = { applyScenePreset(preset) }) { Text(preset.label) }
                        }
                    }
                }
                Text("当前：${scenePreset.label}（一键切换引擎/线程/模式）", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = compactUiMode,
                        onCheckedChange = { enabled ->
                            scope.launch { authStore.setCompactUiMode(enabled) }
                            showAdvancedConfig = !enabled
                        },
                    )
                    Text("紧凑 UI 模式（默认保留核心操作）", style = MaterialTheme.typography.bodySmall)
                }
                if (compactUiMode) {
                    OutlinedButton(onClick = { showAdvancedConfig = !showAdvancedConfig }) {
                        Text(if (showAdvancedConfig) "收起高级参数" else "展开高级参数")
                    }
                }
            }
        }

        if (showAdvancedPanels) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("引擎与语言", style = MaterialTheme.typography.titleMedium)
                val sherpaLabel = when (engine) {
                    TranscriptionEngine.SHERPA_STREAMING -> sherpaStreamingModel.label
                    TranscriptionEngine.SHERPA_SENSEVOICE -> sherpaOfflineModel.label
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { engineMenuOpen = true }) {
                        Text(engine.label)
                    }
                    DropdownMenu(expanded = engineMenuOpen, onDismissRequest = { engineMenuOpen = false }) {
                        TranscriptionEngine.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.label) }, onClick = {
                                scope.launch { authStore.setEngine(item) }
                                engineMenuOpen = false
                            })
                        }
                    }
                    if (engine != TranscriptionEngine.WHISPER) {
                        Text("当前模型：$sherpaLabel", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (engine == TranscriptionEngine.SHERPA_STREAMING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sherpaStreamingMenuOpen = true }) {
                            Text(sherpaStreamingModel.label)
                        }
                        DropdownMenu(expanded = sherpaStreamingMenuOpen, onDismissRequest = { sherpaStreamingMenuOpen = false }) {
                            SherpaStreamingModel.entries.forEach { model ->
                                DropdownMenuItem(text = { Text(model.label) }, onClick = {
                                    scope.launch { authStore.setSherpaStreamingModel(model) }
                                    sherpaStreamingMenuOpen = false
                                })
                            }
                        }
                    }
                }
                if (engine == TranscriptionEngine.SHERPA_SENSEVOICE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sherpaOfflineMenuOpen = true }) {
                            Text(sherpaOfflineModel.label)
                        }
                        DropdownMenu(expanded = sherpaOfflineMenuOpen, onDismissRequest = { sherpaOfflineMenuOpen = false }) {
                            SherpaOfflineModel.entries.forEach { model ->
                                DropdownMenuItem(text = { Text(model.label) }, onClick = {
                                    scope.launch { authStore.setSherpaOfflineModel(model) }
                                    sherpaOfflineMenuOpen = false
                                })
                            }
                        }
                    }
                }
                if (engine == TranscriptionEngine.WHISPER) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { modelMenuOpen = true }) {
                            Text(pendingModel)
                        }
                        DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                            ModelCatalog.models.forEach { item ->
                                DropdownMenuItem(text = { Text(item.label) }, onClick = {
                                    pendingModel = item.id
                                    modelMenuOpen = false
                                })
                            }
                        }

                        OutlinedButton(onClick = { languageMenuOpen = true }) {
                            Text(
                                when (selectedLanguage) {
                                    "zh" -> "中文"
                                    "en" -> "英文"
                                    else -> "自动"
                                },
                            )
                        }
                        DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            listOf("auto", "zh", "en").forEach { item ->
                                val label = when (item) {
                                    "zh" -> "中文"
                                    "en" -> "英文"
                                    else -> "自动"
                                }
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    selectedLanguage = item
                                    languageMenuOpen = false
                                })
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { languageMenuOpen = true }) {
                            Text(
                                when (selectedLanguage) {
                                    "zh" -> "中文"
                                    "en" -> "英文"
                                    else -> "自动"
                                },
                            )
                        }
                        DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            listOf("auto", "zh", "en").forEach { item ->
                                val label = when (item) {
                                    "zh" -> "中文"
                                    "en" -> "英文"
                                    else -> "自动"
                                }
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    selectedLanguage = item
                                    languageMenuOpen = false
                                })
                            }
                        }
                    }
                }
                if (decodeMode == DecodeMode.FAST && selectedLanguage == "auto") {
                    val fixedLang = if (deviceProfile.localeLanguage.startsWith("zh")) "中文" else "英文"
                    Text("快速模式将固定语言：$fixedLang", style = MaterialTheme.typography.bodySmall)
                }
                Text("识别模式", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (decodeMode == DecodeMode.FAST) {
                        Button(onClick = { scope.launch { authStore.setDecodeMode(DecodeMode.FAST) } }) {
                            Text(decodeMode.labelZh())
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { authStore.setDecodeMode(DecodeMode.FAST) } }) {
                            Text("快速")
                        }
                    }
                    if (decodeMode == DecodeMode.ACCURATE) {
                        Button(onClick = { scope.launch { authStore.setDecodeMode(DecodeMode.ACCURATE) } }) {
                            Text(decodeMode.labelZh())
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { authStore.setDecodeMode(DecodeMode.ACCURATE) } }) {
                            Text("准确")
                        }
                    }
                }
                if (engine == TranscriptionEngine.WHISPER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = autoStrategy,
                            onCheckedChange = { enabled -> scope.launch { authStore.setAutoStrategy(enabled) } },
                        )
                        Text("自动策略（按设备/音频自动选模型与 GPU）", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("自动策略仅对 Whisper 生效", style = MaterialTheme.typography.bodySmall)
                }
                Text("处理模式", style = MaterialTheme.typography.bodySmall)
                val parallelLimit = ProcessingMode.PARALLEL.maxParallel(deviceProfile, autoStrategy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (processingMode == ProcessingMode.QUEUE) {
                        Button(onClick = { scope.launch { authStore.setProcessingMode(ProcessingMode.QUEUE) } }) {
                            Text("排队")
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { authStore.setProcessingMode(ProcessingMode.QUEUE) } }) {
                            Text("排队")
                        }
                    }
                    if (processingMode == ProcessingMode.PARALLEL) {
                        Button(onClick = { scope.launch { authStore.setProcessingMode(ProcessingMode.PARALLEL) } }) {
                            Text("并发(最多$parallelLimit)")
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { authStore.setProcessingMode(ProcessingMode.PARALLEL) } }) {
                            Text("并发(最多$parallelLimit)")
                        }
                    }
                }
                if (engine == TranscriptionEngine.WHISPER) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(
                            checked = useGpu,
                            onCheckedChange = { enabled ->
                                scope.launch { authStore.setUseGpu(enabled) }
                            },
                        )
                        Text("GPU 加速（自动策略下仅在长音频/稳定机型启用）", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(
                            checked = sherpaProvider == SherpaProvider.NNAPI,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    authStore.setSherpaProvider(if (enabled) SherpaProvider.NNAPI else SherpaProvider.CPU)
                                }
                            },
                        )
                        Text("NNAPI 加速（失败自动回退 CPU）", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = useMultithread,
                        onCheckedChange = { enabled ->
                            scope.launch { authStore.setMultiThread(enabled) }
                        },
                    )
                    Text("多线程加速（关闭可降低资源占用）", style = MaterialTheme.typography.bodySmall)
                }
                Text("线程数", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = useMultithread, onClick = { threadMenuOpen = true }) {
                        Text(if (threadCount <= 0) "自动" else "${threadCount}线程")
                    }
                    DropdownMenu(expanded = threadMenuOpen, onDismissRequest = { threadMenuOpen = false }) {
                        listOf(0, 1, 2, 4, 6, 8).forEach { value ->
                            val label = if (value <= 0) "自动" else "${value}线程"
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                scope.launch { authStore.setThreadCount(value) }
                                threadMenuOpen = false
                            })
                        }
                    }
                    if (!useMultithread) {
                        Text("已固定单线程", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("录音格式", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { recordFormatMenuOpen = true }) {
                        Text(recordFormat.label)
                    }
                    DropdownMenu(expanded = recordFormatMenuOpen, onDismissRequest = { recordFormatMenuOpen = false }) {
                        RecordFormat.entries.forEach { format ->
                            DropdownMenuItem(text = { Text(format.label) }, onClick = {
                                scope.launch { authStore.setRecordFormat(format.id) }
                                recordFormatMenuOpen = false
                            })
                        }
                    }
                }
                Text("M4A 体积更小，分析前会自动解码为 WAV", style = MaterialTheme.typography.bodySmall)
                if (engine == TranscriptionEngine.WHISPER) {
                    Text("已保存模型：$normalizedModel", style = MaterialTheme.typography.bodySmall)
                    if (autoStrategy) {
                        Text("自动策略开启时，实际模型/语言会按设备与音频长度调整", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = pendingModel != normalizedModel,
                        onClick = {
                            scope.launch { authStore.setSelectedModel(pendingModel) }
                            Toast.makeText(context, "模型设置已保存", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("保存模型设置")
                    }
                    val modelPath = transcriber.resolveWhisperModelPath(normalizedModel)
                    Text(
                        if (modelPath == null) {
                            "模型已内置：首次使用会自动复制到本地（耗时较长），当前尚未解压。"
                        } else {
                            "模型路径：$modelPath"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    val sherpaPath = when (engine) {
                        TranscriptionEngine.SHERPA_STREAMING ->
                            if (SherpaAssets.resolveStreamingModel(context, sherpaStreamingModel) != null) {
                                SherpaAssets.streamingAssetDir(sherpaStreamingModel)
                            } else {
                                null
                            }
                        TranscriptionEngine.SHERPA_SENSEVOICE ->
                            if (SherpaAssets.resolveOfflineModel(context, sherpaOfflineModel) != null) {
                                SherpaAssets.offlineAssetDir(sherpaOfflineModel)
                            } else {
                                null
                            }
                        else -> null
                    }
                    Text(
                        if (sherpaPath == null) {
                            "模型资源缺失，请重新安装应用。"
                        } else {
                            "模型位置：assets/$sherpaPath"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("权限与悬浮窗", style = MaterialTheme.typography.titleMedium)
                Text("录音权限：${if (recordGranted.value) "已授权" else "未授权"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("请求录音权限")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("请求通知权限")
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text("通知权限：${if (notifyGranted.value) "已授权" else "未授权"}")
                }

                Text("悬浮窗权限：${if (overlayGranted) "已授权" else "未授权"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("打开悬浮窗设置")
                    }
                    Button(
                        enabled = overlayGranted,
                        onClick = {
                            overlayRunning = if (overlayRunning) {
                                stopOverlayService(context)
                                false
                            } else {
                                startOverlayService(context)
                                true
                            }
                        },
                    ) {
                        Text(if (overlayRunning) "关闭悬浮窗" else "开启悬浮窗")
                    }
                }
            }
        }

        if (showAdvancedPanels) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("授权白名单", style = MaterialTheme.typography.titleMedium)
                if (allowedApps.isEmpty()) {
                    Text("暂无授权", style = MaterialTheme.typography.bodySmall)
                } else {
                    allowedApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(app.packageName)
                                Text(app.signatureSha256, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { scope.launch { authStore.revoke(app.packageName, app.signatureSha256) } }) {
                                Text("移除")
                            }
                        }
                    }
                    TextButton(onClick = { clearDialogOpen = true }) { Text("清空全部") }
                }
            }
        }
        }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("录音入口", style = MaterialTheme.typography.titleMedium)
                val canClearAudio = !recording && pendingTasks.isEmpty() && runningTasks.isEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = !recording && recordGranted.value && !preflightRunning, onClick = {
                        scope.launch { runPreflightCheck(showToastWhenWarn = true) }
                    }) { Text(if (preflightRunning) "质检中…" else "录音前质检") }
                    preflightReport?.let { report ->
                        Text(
                            if (report.shouldRetry) "建议调整后重录" else "质检通过",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report.shouldRetry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = recordGranted.value,
                        onClick = {
                            if (recording) {
                                stopRecording()
                            } else {
                                scope.launch {
                                    runPreflightCheck(showToastWhenWarn = true)
                                    startRecording()
                                }
                            }
                        },
                    ) {
                        Text(if (recording) "停止录音" else "点击录音")
                    }
                    OutlinedButton(
                        enabled = recording,
                        onClick = {
                            if (recordPaused) resumeRecording() else pauseRecording()
                        },
                    ) {
                        Text(if (recordPaused) "继续录音" else "暂停录音")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                        Text("导入音频分析")
                    }
                }
                OutlinedButton(
                    enabled = canClearAudio,
                    onClick = { clearAudioDialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清除音频缓存")
                }
                Button(
                    onClick = {
                        val path = recordPath
                        val file = path?.let { File(it) }
                        if (file == null || !file.exists()) {
                            Toast.makeText(context, "请先录音或导入音频", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val label = recordMessage ?: file.name
                        enqueueTranscription(file, label)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开始分析")
                }
                OutlinedButton(
                    onClick = {
                        val path = recordPath
                        val file = path?.let { File(it) }
                        if (file == null || !file.exists()) {
                            Toast.makeText(context, "请先录音或导入音频", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val label = recordMessage ?: file.name
                        enqueueQuickVsAccurate(file, label)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("快速 vs 准确 对比")
                }
                if (recording) {
                    Text("时长：${formatDuration(recordElapsedMs)}", style = MaterialTheme.typography.bodySmall)
                }
                if (!recordMessage.isNullOrBlank()) {
                    Text(recordMessage!!, style = MaterialTheme.typography.bodySmall)
                }
                if (!recordPath.isNullOrBlank()) {
                    Text("音频文件：$recordPath", style = MaterialTheme.typography.bodySmall)
                }
                if (!recordUri.isNullOrBlank()) {
                    Text("共享 URI：$recordUri", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("转写结果", style = MaterialTheme.typography.titleMedium)
                Text("待处理：${pendingTasks.size} 个", style = MaterialTheme.typography.bodySmall)
                Text("运行中：${runningTasks.size} 个", style = MaterialTheme.typography.bodySmall)
                val canClearResults = taskResults.isNotEmpty() && pendingTasks.isEmpty() && runningTasks.isEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = pendingTasks.isNotEmpty() || runningTasks.isNotEmpty(),
                        onClick = { cancelAllTranscriptions() },
                    ) {
                        Text("取消转写")
                    }
                    OutlinedButton(
                        enabled = canClearResults,
                        onClick = { clearTranscriptionResults() },
                    ) {
                        Text("清除结果")
                    }
                }
                if (pendingTasks.isEmpty() && runningTasks.isEmpty()) {
                    Text("暂无任务，可先录音或导入音频后点击“开始分析”。", style = MaterialTheme.typography.bodySmall)
                }
                if (runningTasks.isNotEmpty()) {
                    runningTasks.take(2).forEach { task ->
                        Text("· ${task.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (runningTasks.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (!transcribeMessage.isNullOrBlank()) {
                    Text(transcribeMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
                taskResults.forEach { result ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${result.label} · ${result.status.label}", style = MaterialTheme.typography.bodySmall)
                            if (!result.compareMode.isNullOrBlank()) {
                                Text("对比模式：${result.compareMode}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!result.message.isNullOrBlank()) {
                                Text(result.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                            if (result.qualityScore != null && result.qualityLabel != null) {
                                Text(
                                    "质量评分：${result.qualityScore} (${result.qualityLabel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.qualityScore >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                            if (result.status == TaskStatus.Running && result.text.isBlank()) {
                                Text("实时转写输出中…", style = MaterialTheme.typography.bodySmall)
                            }
                            if (result.text.isNotBlank()) {
                                OutlinedTextField(
                                    value = result.text,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = {
                                        Text(if (result.status == TaskStatus.Running) "实时转写" else "转写文本")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 6,
                                )
                            }
                        }
                    }
                }
                val compareGroups = taskResults.filter { it.compareGroupId != null }.groupBy { it.compareGroupId!! }
                compareGroups.values.forEach { group ->
                    val quick = group.firstOrNull { it.compareMode == "快速" }
                    val accurate = group.firstOrNull { it.compareMode == "准确" }
                    if (quick != null && accurate != null && quick.text.isNotBlank() && accurate.text.isNotBlank()) {
                        val score = jaccardScore(quick.text, accurate.text)
                        val lenDiff = (quick.text.length - accurate.text.length).absoluteValue
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("结果对比视图（快速 vs 准确）", style = MaterialTheme.typography.bodySmall)
                                Text("文本相似度：$score% · 字数差：$lenDiff", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = quick.text,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("快速结果") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 4,
                                )
                                OutlinedTextField(
                                    value = accurate.text,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("准确结果") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 4,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAdvancedPanels) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("接入测试", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val req = BridgeRequest(
                        requestId = UUID.randomUUID().toString(),
                        inputType = VoiceAssistantContract.INPUT_TYPE_TEXT,
                        inputText = "新增点位 1-0.3; 无节幼体增加2",
                        template = VoiceAssistantContract.TEMPLATE_GENERAL,
                        contextUri = null,
                        returnAction = "com.plankton.one102.action.RECEIVE_BULK_COMMAND",
                        returnPackage = "com.plankton.one102",
                        requireConfirm = true,
                    )
                    val intent = buildRequestIntent(req)
                    context.startActivity(intent)
                }) {
                    Text("发起模拟请求")
                }
            }
        }
    }

        val dialogAlpha = (if (blurEnabled) 0.4f else 0.8f) * glassOpacity.coerceIn(0.5f, 1.5f)
        val dialogColor = if (glassEnabled) GlassWhite.copy(alpha = dialogAlpha.coerceIn(0.2f, 0.95f)) else MaterialTheme.colorScheme.surface
        val dialogShape = RoundedCornerShape(24.dp)

        if (clearAudioDialogOpen) {
            AlertDialog(
                onDismissRequest = { clearAudioDialogOpen = false },
                confirmButton = {
                    Button(onClick = {
                        clearAudioCache()
                        clearAudioDialogOpen = false
                    }) { Text("确认清除") }
                },
                dismissButton = { TextButton(onClick = { clearAudioDialogOpen = false }) { Text("取消") } },
                title = { Text("清除音频缓存") },
                text = { Text("将删除录音/导入/解码等缓存文件，确定吗？") },
                containerColor = dialogColor,
                shape = dialogShape,
            )
        }

        if (clearDialogOpen) {
            AlertDialog(
                onDismissRequest = { clearDialogOpen = false },
                confirmButton = {
                    Button(onClick = {
                        scope.launch { authStore.clear() }
                        clearDialogOpen = false
                    }) { Text("确认清空") }
                },
                dismissButton = { TextButton(onClick = { clearDialogOpen = false }) { Text("取消") } },
                title = { Text("清空授权") },
                text = { Text("将移除所有已授权应用，确定吗？") },
                containerColor = dialogColor,
                shape = dialogShape,
            )
        }
    }
}
}
}
}

private fun startOverlayService(context: android.content.Context) {
    val intent = Intent(context, OverlayService::class.java)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopOverlayService(context: android.content.Context) {
    val intent = Intent(context, OverlayService::class.java)
    context.stopService(intent)
}

private fun assessTranscriptionQuality(
    text: String,
    segmentCount: Int,
    preflight: AudioQualityInspector.Report?,
): QualityAssessment {
    var score = 100
    val reasons = mutableListOf<String>()
    val trimmed = text.trim()
    val effectiveLen = trimmed.replace("\\s+".toRegex(), "").length
    if (effectiveLen < 8) {
        score -= 28
        reasons += "文本过短"
    } else if (effectiveLen < 20) {
        score -= 12
        reasons += "文本较短"
    }
    if (segmentCount >= 8) {
        score -= 8
        reasons += "分段偏多"
    }
    val sameCharRatio = run {
        if (trimmed.isBlank()) 1f else {
            val chars = trimmed.filter { !it.isWhitespace() }
            if (chars.isEmpty()) 1f else {
                val top = chars.groupingBy { it }.eachCount().maxOf { it.value }
                top.toFloat() / chars.length.toFloat()
            }
        }
    }
    if (sameCharRatio >= 0.45f) {
        score -= 12
        reasons += "重复字符偏多"
    }
    if (preflight != null) {
        if (preflight.clipRatio >= 0.08f) {
            score -= 15
            reasons += "录音削波偏高"
        }
        if (preflight.rmsDb <= -42f) {
            score -= 10
            reasons += "输入音量偏低"
        }
    }
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 85 -> "A（高）"
        score >= 70 -> "B（中）"
        score >= 50 -> "C（一般）"
        else -> "D（低）"
    }
    return QualityAssessment(
        score = score,
        label = label,
        reason = if (reasons.isEmpty()) "文本稳定" else reasons.joinToString("、"),
    )
}

private fun jaccardScore(a: String, b: String): Int {
    val aTokens = a.split(Regex("[\\s，。！？；,.!?;:：]+")).filter { it.isNotBlank() }.toSet()
    val bTokens = b.split(Regex("[\\s，。！？；,.!?;:：]+")).filter { it.isNotBlank() }.toSet()
    if (aTokens.isEmpty() && bTokens.isEmpty()) return 100
    if (aTokens.isEmpty() || bTokens.isEmpty()) return 0
    val inter = aTokens.intersect(bTokens).size.toFloat()
    val union = aTokens.union(bTokens).size.toFloat().coerceAtLeast(1f)
    return ((inter / union) * 100f).toInt().coerceIn(0, 100)
}

@Composable
private fun VoiceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context) }
    val glassEnabled by authStore.glassEnabledFlow.collectAsState(initial = true)
    val blurEnabled by authStore.blurEnabledFlow.collectAsState(initial = true)
    val glassOpacity by authStore.glassOpacityFlow.collectAsState(initial = 1f)
    val docs = remember { listVoiceDocs() }

    var passwordDialogOpen by rememberSaveable { mutableStateOf(false) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var docDialogOpen by remember { mutableStateOf(false) }
    var docId by remember { mutableStateOf<String?>(null) }
    var docTitle by remember { mutableStateOf<String?>(null) }
    var docContent by remember { mutableStateOf<String?>(null) }
    var docError by remember { mutableStateOf<String?>(null) }
    var docLoading by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    fun openDoc(entry: VoiceDocEntry) {
        docId = entry.id
        docTitle = entry.title
        passwordError = null
        passwordDialogOpen = true
    }

    val dialogAlpha = (if (blurEnabled) 0.4f else 0.8f) * glassOpacity.coerceIn(0.5f, 1.5f)
    val dialogColor = if (glassEnabled) GlassWhite.copy(alpha = dialogAlpha.coerceIn(0.2f, 0.95f)) else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    CompositionLocalProvider(LocalGlassPrefs provides GlassPrefs(glassEnabled, blurEnabled, glassOpacity)) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("返回") }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("视觉效果", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用毛玻璃效果", modifier = Modifier.weight(1f))
                        Switch(
                            checked = glassEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { authStore.setGlassEnabled(enabled) }
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用高斯模糊", modifier = Modifier.weight(1f))
                        Switch(
                            checked = blurEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { authStore.setBlurEnabled(enabled) }
                            },
                            enabled = glassEnabled,
                        )
                    }
                    Text(
                        "透明度：${(glassOpacity.coerceIn(0.5f, 1.5f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Slider(
                        enabled = glassEnabled,
                        value = glassOpacity.coerceIn(0.5f, 1.5f),
                        onValueChange = { value ->
                            scope.launch { authStore.setGlassOpacity(value) }
                        },
                        valueRange = 0.5f..1.5f,
                    )
                    Text(
                        "关闭视觉效果可降低资源占用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("项目书", style = MaterialTheme.typography.titleMedium)
                    Text("访问需输入密码。", style = MaterialTheme.typography.bodySmall)
                    docs.forEach { entry ->
                        OutlinedButton(onClick = { openDoc(entry) }, modifier = Modifier.fillMaxWidth()) {
                            Text("查看${entry.title}")
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("更新日志", style = MaterialTheme.typography.titleMedium)
                    Text("当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                    AppInfo.releases.forEach { release ->
                        Text("v${release.versionName} (${release.date})", style = MaterialTheme.typography.bodySmall)
                        release.notes.forEach { note ->
                            Text("• $note", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (passwordDialogOpen) {
        AlertDialog(
            onDismissRequest = { passwordDialogOpen = false },
            confirmButton = {
                Button(onClick = {
                    if (isDocPasswordValid(passwordInput)) {
                        DocAccessCache.unlock(passwordInput)
                        passwordDialogOpen = false
                        passwordInput = ""
                        docDialogOpen = true
                        docLoading = true
                        docError = null
                        docContent = null
                        scope.launch {
                            docContent = readVoiceDoc(context, docId ?: "project")
                            if (docContent == null) {
                                docError = "未找到文档内容"
                            }
                            docLoading = false
                        }
                    } else {
                        passwordError = "密码错误"
                    }
                }) { Text("解锁") }
            },
            dismissButton = { TextButton(onClick = { passwordInput = "" }) { Text("清空") } },
            title = { Text("输入文档密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (!passwordError.isNullOrBlank()) {
                        Text(passwordError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (docDialogOpen) {
        AlertDialog(
            onDismissRequest = { docDialogOpen = false },
            confirmButton = { TextButton(onClick = { docDialogOpen = false }) { Text("关闭") } },
            title = { Text(docTitle ?: "项目书") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        docLoading -> Text("加载中…", style = MaterialTheme.typography.bodySmall)
                        docError != null -> Text(docError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        else -> Text(docContent.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}
}
