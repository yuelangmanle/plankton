package com.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voiceassistant.audio.AudioImport
import com.voiceassistant.audio.SpeechTranscriber
import com.voiceassistant.audio.TranscriptionRequest
import com.voiceassistant.bridge.VoiceAssistantContract
import com.voiceassistant.data.AuthStore
import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine
import com.voiceassistant.data.readSignatureSha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.ArrayDeque

class TranscribeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = ArrayDeque<TranscribeWork>()
    private var running = false
    private var currentWork: TranscribeWork? = null
    private var currentJob: Job? = null
    private val authStore by lazy { AuthStore(this) }
    private val deviceProfile by lazy { DeviceProfile.from(this) }
    private val transcriber by lazy { SpeechTranscriber(this) }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFY_ID, buildNotification("等待任务…"))
    }

    override fun onDestroy() {
        transcriber.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == VoiceAssistantContract.ACTION_CANCEL_TRANSCRIBE_AUDIO) {
            val requestId = intent.getStringExtra(VoiceAssistantContract.EXTRA_REQUEST_ID)?.trim().orEmpty()
            val returnAction = intent.getStringExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION)?.trim().orEmpty()
            val returnPackage = intent.getStringExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE)?.trim().orEmpty()
            handleCancel(requestId, returnAction, returnPackage)
            if (!running && queue.isEmpty()) {
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }

        if (action != VoiceAssistantContract.ACTION_TRANSCRIBE_AUDIO) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val requestId = intent.getStringExtra(VoiceAssistantContract.EXTRA_REQUEST_ID)?.trim().orEmpty()
        val audioUri = intent.getStringExtra(VoiceAssistantContract.EXTRA_AUDIO_URI)?.trim().orEmpty()
        val returnAction = intent.getStringExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION)?.trim().orEmpty()
        val returnPackage = intent.getStringExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE)?.trim().orEmpty()
        val overrides = readOverrides(intent)

        if (requestId.isBlank() || audioUri.isBlank() || returnAction.isBlank() || returnPackage.isBlank()) {
            if (requestId.isNotBlank() && returnAction.isNotBlank()) {
                sendResult(
                    requestId = requestId,
                    returnAction = returnAction,
                    returnPackage = returnPackage,
                    status = VoiceAssistantContract.STATUS_ERROR,
                    errorMessage = "缺少请求参数",
                )
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        queue.add(
            TranscribeWork(
                startId = startId,
                requestId = requestId,
                audioUri = audioUri,
                returnAction = returnAction,
                returnPackage = returnPackage,
                overrides = overrides,
            ),
        )
        drainQueue()
        return START_NOT_STICKY
    }

    private fun drainQueue() {
        if (running) return
        val next = queue.removeFirstOrNull() ?: run {
            stopSelf()
            return
        }
        running = true
        currentWork = next
        currentJob = scope.launch {
            runWork(next)
            running = false
            currentWork = null
            currentJob = null
            stopSelfResult(next.startId)
            drainQueue()
        }
    }

    private fun handleCancel(requestId: String, returnAction: String, returnPackage: String) {
        if (requestId.isBlank()) return
        val current = currentWork
        if (current != null && current.requestId == requestId) {
            currentJob?.cancel()
            sendResult(
                requestId = requestId,
                returnAction = returnAction.ifBlank { current.returnAction },
                returnPackage = returnPackage.ifBlank { current.returnPackage },
                status = VoiceAssistantContract.STATUS_CANCEL,
                errorMessage = "已取消转写",
            )
            return
        }
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.requestId == requestId) {
                iterator.remove()
                sendResult(
                    requestId = requestId,
                    returnAction = returnAction.ifBlank { item.returnAction },
                    returnPackage = returnPackage.ifBlank { item.returnPackage },
                    status = VoiceAssistantContract.STATUS_CANCEL,
                    errorMessage = "已取消转写",
                )
                return
            }
        }
    }

    private suspend fun runWork(work: TranscribeWork) {
        val signature = readSignatureSha256(this, work.returnPackage)
        val allowedList = authStore.allowedAppsFlow.first()
        val allowed = authStore.isAllowed(work.returnPackage, signature, allowedList)
        if (!allowed) {
            sendResult(
                requestId = work.requestId,
                returnAction = work.returnAction,
                returnPackage = work.returnPackage,
                status = VoiceAssistantContract.STATUS_ERROR,
                errorMessage = "未授权的接入请求",
            )
            return
        }

        updateNotification("导入音频…")
        val uri = Uri.parse(work.audioUri)
        val importResult = AudioImport.importToWav(this, uri)
        val wavFile = importResult.file
        if (wavFile == null) {
            sendResult(
                requestId = work.requestId,
                returnAction = work.returnAction,
                returnPackage = work.returnPackage,
                status = VoiceAssistantContract.STATUS_ERROR,
                errorMessage = importResult.error ?: "音频导入失败",
            )
            return
        }

        try {
            val modelId = work.overrides.modelId ?: authStore.selectedModelFlow.first()
            val engine = work.overrides.engine ?: authStore.engineFlow.first()
            val sherpaProvider = work.overrides.sherpaProvider ?: authStore.sherpaProviderFlow.first()
            val sherpaStreamingModel = work.overrides.sherpaStreamingModel ?: authStore.sherpaStreamingModelFlow.first()
            val sherpaOfflineModel = work.overrides.sherpaOfflineModel ?: authStore.sherpaOfflineModelFlow.first()
            val useGpu = work.overrides.useGpu ?: authStore.useGpuFlow.first()
            val decodeMode = work.overrides.decodeMode ?: authStore.decodeModeFlow.first()
            val autoStrategy = work.overrides.autoStrategy ?: authStore.autoStrategyFlow.first()
            val useMultithread = work.overrides.useMultithread ?: authStore.multiThreadFlow.first()
            val threadCount = work.overrides.threadCount ?: authStore.threadCountFlow.first()
            val request = TranscriptionRequest(
                engine = engine,
                modelId = modelId,
                decodeMode = decodeMode,
                language = "auto",
                useGpuPreference = useGpu,
                autoStrategy = autoStrategy,
                useMultithread = useMultithread,
                threadCount = if (useMultithread) threadCount else 1,
                sherpaProvider = sherpaProvider,
                sherpaStreamingModel = sherpaStreamingModel,
                sherpaOfflineModel = sherpaOfflineModel,
            )

            val result = withContext(Dispatchers.IO) {
                transcriber.transcribe(
                    wavPath = wavFile.absolutePath,
                    request = request,
                    deviceProfile = deviceProfile,
                    onProgress = { progress -> updateNotification(progress) },
                )
            }

            if (result.error != null) {
                sendResult(
                    requestId = work.requestId,
                    returnAction = work.returnAction,
                    returnPackage = work.returnPackage,
                    status = VoiceAssistantContract.STATUS_ERROR,
                    errorMessage = result.error,
                    audioPath = work.audioUri,
                )
                return
            }

            val formatted = com.voiceassistant.text.TextConverters.formatTranscript(result.text.orEmpty())
            sendResult(
                requestId = work.requestId,
                returnAction = work.returnAction,
                returnPackage = work.returnPackage,
                status = VoiceAssistantContract.STATUS_OK,
                rawText = formatted,
                audioPath = work.audioUri,
                warnings = "后台转写完成",
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            sendResult(
                requestId = work.requestId,
                returnAction = work.returnAction,
                returnPackage = work.returnPackage,
                status = VoiceAssistantContract.STATUS_CANCEL,
                errorMessage = "已取消转写",
                audioPath = work.audioUri,
            )
        } finally {
            runCatching { wavFile.delete() }
        }
    }

    private fun sendResult(
        requestId: String,
        returnAction: String,
        returnPackage: String,
        status: String,
        rawText: String? = null,
        audioPath: String? = null,
        warnings: String? = null,
        errorMessage: String? = null,
    ) {
        val intent = Intent(returnAction).apply {
            if (returnPackage.isNotBlank()) {
                setPackage(returnPackage)
            }
            putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, requestId)
            putExtra(VoiceAssistantContract.EXTRA_STATUS, status)
            putExtra(VoiceAssistantContract.EXTRA_RAW_TEXT, rawText)
            putExtra(VoiceAssistantContract.EXTRA_AUDIO_PATH, audioPath)
            putExtra(VoiceAssistantContract.EXTRA_WARNINGS, warnings)
            putExtra(VoiceAssistantContract.EXTRA_ERROR_MESSAGE, errorMessage)
        }
        val audioUri = audioPath?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (audioUri != null && audioUri.scheme == "content" && returnPackage.isNotBlank()) {
            grantUriPermission(
                returnPackage,
                audioUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(message: String): Notification {
        val channelId = ensureChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("语音识别助手")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_ID, buildNotification(message))
    }

    private fun ensureChannel(): String {
        val channelId = "voice_assistant_transcribe"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "语音转写",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    private data class TranscribeWork(
        val startId: Int,
        val requestId: String,
        val audioUri: String,
        val returnAction: String,
        val returnPackage: String,
        val overrides: TranscribeOverrides,
    )

    private data class TranscribeOverrides(
        val engine: TranscriptionEngine? = null,
        val modelId: String? = null,
        val decodeMode: DecodeMode? = null,
        val useGpu: Boolean? = null,
        val autoStrategy: Boolean? = null,
        val useMultithread: Boolean? = null,
        val threadCount: Int? = null,
        val sherpaProvider: SherpaProvider? = null,
        val sherpaStreamingModel: SherpaStreamingModel? = null,
        val sherpaOfflineModel: SherpaOfflineModel? = null,
    )

    private fun readOverrides(intent: Intent): TranscribeOverrides {
        val engine = intent.getStringExtra(VoiceAssistantContract.EXTRA_ENGINE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { TranscriptionEngine.fromId(it) }
        val modelId = intent.getStringExtra(VoiceAssistantContract.EXTRA_MODEL_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val decodeMode = intent.getStringExtra(VoiceAssistantContract.EXTRA_DECODE_MODE)
            ?.trim()
            ?.lowercase()
            ?.let {
                when (it) {
                    "accurate" -> DecodeMode.ACCURATE
                    "fast" -> DecodeMode.FAST
                    else -> null
                }
            }
        val useGpu = if (intent.hasExtra(VoiceAssistantContract.EXTRA_USE_GPU)) {
            intent.getBooleanExtra(VoiceAssistantContract.EXTRA_USE_GPU, false)
        } else {
            null
        }
        val autoStrategy = if (intent.hasExtra(VoiceAssistantContract.EXTRA_AUTO_STRATEGY)) {
            intent.getBooleanExtra(VoiceAssistantContract.EXTRA_AUTO_STRATEGY, false)
        } else {
            null
        }
        val useMultithread = if (intent.hasExtra(VoiceAssistantContract.EXTRA_USE_MULTITHREAD)) {
            intent.getBooleanExtra(VoiceAssistantContract.EXTRA_USE_MULTITHREAD, false)
        } else {
            null
        }
        val threadCount = if (intent.hasExtra(VoiceAssistantContract.EXTRA_THREAD_COUNT)) {
            intent.getIntExtra(VoiceAssistantContract.EXTRA_THREAD_COUNT, 0)
        } else {
            null
        }
        val sherpaProvider = intent.getStringExtra(VoiceAssistantContract.EXTRA_SHERPA_PROVIDER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { SherpaProvider.fromId(it) }
        val sherpaStreamingModel = intent.getStringExtra(VoiceAssistantContract.EXTRA_SHERPA_STREAMING_MODEL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { SherpaStreamingModel.fromId(it) }
        val sherpaOfflineModel = intent.getStringExtra(VoiceAssistantContract.EXTRA_SHERPA_OFFLINE_MODEL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { SherpaOfflineModel.fromId(it) }
        return TranscribeOverrides(
            engine = engine,
            modelId = modelId,
            decodeMode = decodeMode,
            useGpu = useGpu,
            autoStrategy = autoStrategy,
            useMultithread = useMultithread,
            threadCount = threadCount,
            sherpaProvider = sherpaProvider,
            sherpaStreamingModel = sherpaStreamingModel,
            sherpaOfflineModel = sherpaOfflineModel,
        )
    }

    companion object {
        private const val NOTIFY_ID = 1201
    }
}
