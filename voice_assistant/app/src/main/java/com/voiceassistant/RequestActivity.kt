package com.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voiceassistant.audio.AudioCacheCleaner
import com.voiceassistant.audio.AudioImport
import com.voiceassistant.audio.AudioShare
import com.voiceassistant.audio.ModelCatalog
import com.voiceassistant.audio.RecordFormat
import com.voiceassistant.audio.SpeechTranscriber
import com.voiceassistant.audio.TranscriptionRequest
import com.voiceassistant.audio.VoiceRecorder
import com.voiceassistant.bridge.BridgeRequest
import com.voiceassistant.bridge.BridgeResult
import com.voiceassistant.bridge.VoiceAssistantContract
import com.voiceassistant.bridge.buildResultIntent
import com.voiceassistant.bridge.readBridgeRequest
import com.voiceassistant.data.AuthStore
import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.ProcessingMode
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine
import com.voiceassistant.data.maxParallel
import com.voiceassistant.data.readSignatureSha256
import com.voiceassistant.data.labelZh
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

class RequestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableHighRefreshRate(window)
        val request = intent.readBridgeRequest()
        if (request == null) {
            finish()
            return
        }
        setContent {
            VoiceAssistantTheme {
                RequestScreen(request = request)
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

private enum class RequestStage { Confirm, Process }

private data class RequestTranscriptionTask(
    val id: String,
    val file: File,
    val label: String,
    val applyText: Boolean,
)

private enum class RequestTaskStatus(val label: String) {
    Pending("待处理"),
    Running("分析中"),
    Completed("完成"),
    Failed("失败"),
    Cancelled("已取消"),
}

private data class RequestTaskResult(
    val id: String,
    val label: String,
    val status: RequestTaskStatus,
    val text: String = "",
    val message: String? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val durationMs: Long? = null,
)

private data class RequestTaskHealthSummary(
    val sampleSize: Int,
    val successCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val successRate: Double,
    val avgDurationSec: Double,
)

private fun buildTaskHealthSummary(results: List<RequestTaskResult>, window: Int = 20): RequestTaskHealthSummary {
    val finished = results
        .filter { it.status == RequestTaskStatus.Completed || it.status == RequestTaskStatus.Failed || it.status == RequestTaskStatus.Cancelled }
        .take(window)
    if (finished.isEmpty()) {
        return RequestTaskHealthSummary(
            sampleSize = 0,
            successCount = 0,
            failedCount = 0,
            cancelledCount = 0,
            successRate = 0.0,
            avgDurationSec = 0.0,
        )
    }
    val success = finished.count { it.status == RequestTaskStatus.Completed }
    val failed = finished.count { it.status == RequestTaskStatus.Failed }
    val cancelled = finished.count { it.status == RequestTaskStatus.Cancelled }
    val durations = finished.mapNotNull { it.durationMs }.filter { it > 0 }
    val avgSec = if (durations.isEmpty()) 0.0 else durations.average() / 1000.0
    return RequestTaskHealthSummary(
        sampleSize = finished.size,
        successCount = success,
        failedCount = failed,
        cancelledCount = cancelled,
        successRate = success.toDouble() / finished.size.toDouble(),
        avgDurationSec = avgSec,
    )
}

@Composable
private fun RequestScreen(request: BridgeRequest) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context) }
    val allowedApps by authStore.allowedAppsFlow.collectAsState(initial = emptyList())
    val model by authStore.selectedModelFlow.collectAsState(initial = "small-q8_0")
    val engine by authStore.engineFlow.collectAsState(initial = TranscriptionEngine.WHISPER)
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
    val deviceProfile = remember { DeviceProfile.from(context) }
    val recorder = remember { VoiceRecorder(context) }
    val transcriber = remember { SpeechTranscriber(context) }

    DisposableEffect(Unit) {
        onDispose { transcriber.release() }
    }

    val returnPackage = request.returnPackage
    val signature = remember(returnPackage) { returnPackage?.let { readSignatureSha256(context, it) } }
    val allowed = authStore.isAllowed(returnPackage, signature, allowedApps)
    val processingMode by authStore.processingModeFlow.collectAsState(initial = ProcessingMode.QUEUE)
    val normalizedModel = remember(model) { ModelCatalog.normalizeId(model) }
    val recordFormat = remember(recordFormatId) { RecordFormat.fromId(recordFormatId) }

    var stage by remember { mutableStateOf(RequestStage.Confirm) }
    var rawText by remember { mutableStateOf(request.inputText.orEmpty()) }
    var bulkJson by remember { mutableStateOf(buildDefaultBulkJson(rawText)) }
    var confirmOnce by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var recordElapsedMs by remember { mutableStateOf(0L) }
    var recordPaused by remember { mutableStateOf(false) }
    var pauseStartMs by remember { mutableStateOf(0L) }
    var pausedDurationMs by remember { mutableStateOf(0L) }
    var recordMessage by remember { mutableStateOf<String?>(null) }
    var recordPath by remember { mutableStateOf<String?>(null) }
    var recordUri by remember { mutableStateOf<String?>(null) }
    val pendingTasks = remember { mutableStateListOf<RequestTranscriptionTask>() }
    val runningTasks = remember { mutableStateListOf<RequestTranscriptionTask>() }
    val taskResults = remember { mutableStateListOf<RequestTaskResult>() }
    val taskJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }
    var lastTranscript by remember { mutableStateOf("") }
    var transcribeMessage by remember { mutableStateOf<String?>(null) }
    var useTranscript by remember(request.requestId) {
        mutableStateOf(request.inputType == VoiceAssistantContract.INPUT_TYPE_VOICE)
    }
    var engineMenuOpen by remember { mutableStateOf(false) }
    var sherpaStreamingMenuOpen by remember { mutableStateOf(false) }
    var sherpaOfflineMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("auto") }
    var threadMenuOpen by remember { mutableStateOf(false) }
    var recordFormatMenuOpen by remember { mutableStateOf(false) }
    var clearAudioDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(allowed, request.requireConfirm) {
        if (allowed && !request.requireConfirm) {
            stage = RequestStage.Process
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (recording) {
                val now = android.os.SystemClock.elapsedRealtime()
                val pausedExtra = if (recordPaused) (now - pauseStartMs).coerceAtLeast(0) else 0L
                recordElapsedMs = (now - recordStartMs - pausedDurationMs - pausedExtra).coerceAtLeast(0)
                delay(200)
            }
        } else {
            recordElapsedMs = 0
        }
    }

    val recordGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    fun updateTaskResult(id: String, updater: (RequestTaskResult) -> RequestTaskResult) {
        val index = taskResults.indexOfFirst { it.id == id }
        if (index >= 0) {
            taskResults[index] = updater(taskResults[index])
        }
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
            val startedAtMs = android.os.SystemClock.elapsedRealtime()
            updateTaskResult(task.id) {
                it.copy(
                    status = RequestTaskStatus.Running,
                    message = "分析中…",
                    startedAtMs = startedAtMs,
                    finishedAtMs = null,
                    durationMs = null,
                )
            }
            val job = scope.launch {
                transcribeMessage = "分析中：${task.label}"
                val manager = if (modeAtStart == ProcessingMode.PARALLEL) {
                    SpeechTranscriber(context)
                } else {
                    transcriber
                }
                try {
                    val requestPlan = TranscriptionRequest(
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
                    val result = manager.transcribe(
                        wavPath = task.file.absolutePath,
                        request = requestPlan,
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
                    if (result.error != null) {
                        transcribeMessage = "分析失败：${result.error}"
                        val finishedAtMs = android.os.SystemClock.elapsedRealtime()
                        updateTaskResult(task.id) {
                            it.copy(
                                status = RequestTaskStatus.Failed,
                                message = result.error,
                                finishedAtMs = finishedAtMs,
                                durationMs = (finishedAtMs - (it.startedAtMs ?: startedAtMs)).coerceAtLeast(0L),
                            )
                        }
                    } else {
                        val formatted = TextConverters.formatTranscript(result.text.orEmpty())
                        lastTranscript = formatted
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
                        val doneMessage = "分析完成（$accelTag$segmentTag$modelTag$langTag）"
                        transcribeMessage = doneMessage
                        val finishedAtMs = android.os.SystemClock.elapsedRealtime()
                        updateTaskResult(task.id) {
                            it.copy(
                                status = RequestTaskStatus.Completed,
                                text = formatted,
                                message = doneMessage,
                                finishedAtMs = finishedAtMs,
                                durationMs = (finishedAtMs - (it.startedAtMs ?: startedAtMs)).coerceAtLeast(0L),
                            )
                        }
                        if (task.applyText) {
                            rawText = formatted
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    transcribeMessage = "已取消：${task.label}"
                    val finishedAtMs = android.os.SystemClock.elapsedRealtime()
                    updateTaskResult(task.id) {
                        it.copy(
                            status = RequestTaskStatus.Cancelled,
                            message = "已取消",
                            finishedAtMs = finishedAtMs,
                            durationMs = (finishedAtMs - (it.startedAtMs ?: startedAtMs)).coerceAtLeast(0L),
                        )
                    }
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

    fun enqueueTranscription(file: File, label: String, applyText: Boolean) {
        val id = UUID.randomUUID().toString()
        pendingTasks.add(RequestTranscriptionTask(id, file, label, applyText))
        taskResults.add(0, RequestTaskResult(id = id, label = label, status = RequestTaskStatus.Pending))
        startNextTasks()
    }

    fun cancelAllTranscriptions() {
        if (pendingTasks.isEmpty() && runningTasks.isEmpty()) return
        pendingTasks.forEach { task ->
            updateTaskResult(task.id) { it.copy(status = RequestTaskStatus.Cancelled, message = "已取消") }
        }
        runningTasks.forEach { task ->
            updateTaskResult(task.id) { it.copy(status = RequestTaskStatus.Cancelled, message = "已取消") }
        }
        pendingTasks.clear()
        runningTasks.clear()
        taskJobs.values.forEach { it.cancel() }
        taskJobs.clear()
        transcribeMessage = "已取消全部转写任务"
    }

    fun clearTranscriptionResults() {
        taskResults.clear()
        lastTranscript = ""
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

    fun startRecording(): Boolean {
        if (!recordGranted) {
            Toast.makeText(context, "录音权限未授权", Toast.LENGTH_SHORT).show()
            return false
        }
        if (recording) return false
        val started = recorder.start(recordFormat)
        if (started) {
            recordStartMs = android.os.SystemClock.elapsedRealtime()
            recording = true
            recordPaused = false
            pausedDurationMs = 0L
            recordMessage = "录音中…"
            recordPath = null
            recordUri = null
            lastTranscript = ""
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
            pauseStartMs = android.os.SystemClock.elapsedRealtime()
            recordMessage = "录音暂停"
        }
    }

    fun resumeRecording() {
        if (!recording || !recordPaused) return
        if (recorder.resume()) {
            val now = android.os.SystemClock.elapsedRealtime()
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

    fun sendResult(result: BridgeResult) {
        if (request.returnAction.isNullOrBlank()) {
            Toast.makeText(context, "缺少回调 Action，无法返回结果", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = buildResultIntent(request, result)
        val audioUri = result.audioPath?.let { Uri.parse(it) }
        if (audioUri != null && audioUri.scheme == "content") {
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            returnPackage?.let { pkg ->
                context.grantUriPermission(pkg, audioUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.sendBroadcast(intent)
        (context as? ComponentActivity)?.finish()
    }

    CompositionLocalProvider(LocalGlassPrefs provides GlassPrefs(glassEnabled, blurEnabled, glassOpacity)) {
    val dialogAlpha = (if (blurEnabled) 0.4f else 0.8f) * glassOpacity.coerceIn(0.5f, 1.5f)
    val dialogColor = if (glassEnabled) GlassWhite.copy(alpha = dialogAlpha.coerceIn(0.2f, 0.95f)) else MaterialTheme.colorScheme.surface
        val dialogShape = RoundedCornerShape(24.dp)

        GlassBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("接入请求", style = MaterialTheme.typography.titleLarge)

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("请求 ID：${request.requestId}")
                        Text("返回包名：${returnPackage ?: "未提供"}")
                        Text("签名摘要：${signature ?: "未知"}")
                        Text("模板：${request.template ?: "未提供"}")
                        Text("输入类型：${request.inputType ?: "未提供"}")
                        Text("需确认：${if (request.requireConfirm) "是" else "否"}")
                        Text("Context URI：${request.contextUri ?: "无"}")
                    }
                }

                if (stage == RequestStage.Confirm) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("授权确认", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (allowed) "已在白名单中，可直接继续。" else "首次接入，需要确认授权。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    sendResult(
                                        BridgeResult(
                                            status = VoiceAssistantContract.STATUS_CANCEL,
                                            errorMessage = "用户拒绝授权",
                                        ),
                                    )
                                }) { Text("拒绝") }
                                Button(onClick = {
                                    confirmOnce = true
                                    stage = RequestStage.Process
                                }) { Text("仅一次") }
                                Button(onClick = {
                                    if (!returnPackage.isNullOrBlank() && !signature.isNullOrBlank()) {
                                        scope.launch { authStore.allow(returnPackage, signature) }
                                    }
                                    confirmOnce = false
                                    stage = RequestStage.Process
                                }) { Text("总是允许") }
                            }
                        }
                    }
                }

                if (stage == RequestStage.Process) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("识别与解析", style = MaterialTheme.typography.titleMedium)
                    val sherpaLabel = when (engine) {
                        TranscriptionEngine.SHERPA_STREAMING -> sherpaStreamingModel.label
                        TranscriptionEngine.SHERPA_SENSEVOICE -> sherpaOfflineModel.label
                        else -> ""
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    }
                    Text(
                        if (engine == TranscriptionEngine.WHISPER) {
                            "当前模型：$normalizedModel"
                        } else {
                            "当前模型：$sherpaLabel"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    if (engine == TranscriptionEngine.WHISPER && autoStrategy) {
                        Text("自动策略开启时，实际模型/语言会按设备与音频长度调整", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (decodeMode == DecodeMode.FAST && selectedLanguage == "auto") {
                        val fixedLang = if (deviceProfile.localeLanguage.startsWith("zh")) "中文" else "英文"
                        Text("快速模式将固定语言：$fixedLang", style = MaterialTheme.typography.bodySmall)
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = useGpu,
                                onCheckedChange = { enabled ->
                                    scope.launch { authStore.setUseGpu(enabled) }
                                },
                            )
                            Text("GPU 加速（自动策略下仅在长音频/稳定机型启用）", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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

                    if (!recordGranted) {
                        Text("录音权限未授权，请先在主页面授权。", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = recordGranted,
                            onClick = {
                                if (recording) stopRecording() else startRecording()
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
                    val canClearAudio = !recording && pendingTasks.isEmpty() && runningTasks.isEmpty()
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
                            enqueueTranscription(file, label, useTranscript)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开始分析")
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

                    Text("待处理：${pendingTasks.size} 个", style = MaterialTheme.typography.bodySmall)
                    Text("运行中：${runningTasks.size} 个", style = MaterialTheme.typography.bodySmall)
                    val health = buildTaskHealthSummary(taskResults)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("任务健康度（最近20次）", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "成功 ${health.successCount} · 失败 ${health.failedCount} · 取消 ${health.cancelledCount}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val successRatePct = (health.successRate * 100.0).toInt()
                            val avgSecText = if (health.avgDurationSec > 0.0) "%.1f秒".format(health.avgDurationSec) else "—"
                            Text(
                                "成功率 ${successRatePct}% · 平均耗时 $avgSecText",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (health.sampleSize >= 5 && health.successRate < 0.8) {
                                Text("提示：最近成功率偏低，建议切到“排队+准确”或关闭加速重试。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
                                if (!result.message.isNullOrBlank()) {
                                    Text(result.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.status == RequestTaskStatus.Running && result.text.isBlank()) {
                                    Text("实时转写输出中…", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.text.isNotBlank()) {
                                    OutlinedTextField(
                                        value = result.text,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = {
                                            Text(if (result.status == RequestTaskStatus.Running) "实时转写" else "转写文本")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 6,
                                    )
                                    TextButton(onClick = {
                                        rawText = result.text
                                    }) { Text("应用到文本") }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = useTranscript, onCheckedChange = {
                            useTranscript = it
                            if (it && lastTranscript.isNotBlank()) {
                                rawText = lastTranscript
                            }
                        })
                        Text("回传转写文本（可关闭）", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("ASR 文本（可手动修改）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                    )

                    OutlinedTextField(
                        value = bulkJson,
                        onValueChange = { bulkJson = it },
                        label = { Text("bulk_json（结构化指令）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val payload = bulkJson.ifBlank { buildDefaultBulkJson(rawText) }
                            sendResult(
                                BridgeResult(
                                    status = VoiceAssistantContract.STATUS_OK,
                                    bulkJson = payload,
                                    rawText = if (useTranscript) rawText else rawText.ifBlank { null },
                                    audioPath = recordUri,
                                    warnings = if (confirmOnce) "仅一次授权" else null,
                                ),
                            )
                        }) { Text("发送结果") }
                        OutlinedButton(onClick = {
                            sendResult(
                                BridgeResult(
                                    status = VoiceAssistantContract.STATUS_ERROR,
                                    rawText = rawText,
                                    audioPath = recordUri,
                                    errorMessage = "语音解析失败",
                                ),
                            )
                        }) { Text("返回错误") }
                    }
                }
            }
        }
    }

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
    }
}
}

private fun buildDefaultBulkJson(rawText: String): String {
    val trimmed = rawText.trim()
    return if (trimmed.isBlank()) {
        """{"actions":[],"unparsed":[],"notes":[],"warnings":[]}"""
    } else {
        val escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"")
        """{"actions":[],"unparsed":["$escaped"],"notes":[],"warnings":[]}"""
    }
}
