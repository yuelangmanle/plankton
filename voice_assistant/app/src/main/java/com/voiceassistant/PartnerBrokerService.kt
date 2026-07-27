package com.voiceassistant

import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.voiceassistant.audio.SpeechTranscriber
import com.voiceassistant.audio.TranscriptionRequest
import com.voiceassistant.bridge.PARTNER_PROTOCOL_VERSION
import com.voiceassistant.bridge.PartnerErrorCode
import com.voiceassistant.bridge.PartnerHelloValidation
import com.voiceassistant.bridge.PartnerProfile
import com.voiceassistant.bridge.PartnerProtocol
import com.voiceassistant.bridge.PartnerScope
import com.voiceassistant.bridge.VoiceAssistantContract
import com.voiceassistant.data.AuthorizedCallerStore
import com.voiceassistant.data.CallerAuthorization
import com.voiceassistant.data.CallerDecision
import com.voiceassistant.data.DeviceProfile
import com.voiceassistant.data.PartnerCallerIdentity
import com.voiceassistant.data.PartnerSessionRegistry
import com.voiceassistant.data.readSignatureSha256
import com.voiceassistant.partner.IPartnerBroker
import com.voiceassistant.partner.IPartnerCallback
import com.voiceassistant.text.DomainNormalizationContext
import com.voiceassistant.text.DomainTranscriptNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

class PartnerBrokerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = PartnerSessionRegistry()
    private val transcribeMutex = Mutex()
    private val activeRequests = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val authorizedCallers by lazy { AuthorizedCallerStore(this) }
    private val authStore by lazy { com.voiceassistant.data.AuthStore(this) }
    private val profile by lazy { DeviceProfile.from(this) }
    private val transcriber by lazy { SpeechTranscriber(this) }

    private val broker = object : IPartnerBroker.Stub() {
        override fun getCapabilities(): Bundle = Bundle().apply {
            putInt("protocol_version", PARTNER_PROTOCOL_VERSION)
            putStringArrayList("profiles", arrayListOf(PartnerProfile.GENERIC.wireValue, PartnerProfile.PLANKTON_V1.wireValue))
            putStringArrayList("scopes", ArrayList(PartnerScope.entries.map(PartnerScope::wireValue)))
            putLong("max_audio_bytes", MAX_AUDIO_BYTES)
            putStringArrayList("formats", arrayListOf("audio/wav"))
        }

        override fun beginSession(hello: Bundle): Bundle {
            val identity = resolveCaller() ?: return error(PartnerErrorCode.INTERNAL_ERROR, "无法识别调用方")
            val requestedScopes = PartnerProtocol.parseScopes(
                hello.getStringArrayList(VoiceAssistantContract.EXTRA_PARTNER_REQUESTED_SCOPES).orEmpty(),
            ) ?: return error(PartnerErrorCode.INTERNAL_ERROR, "请求范围无效")
            val validation = PartnerProtocol.validateHello(
                hello.getInt(VoiceAssistantContract.EXTRA_PARTNER_PROTOCOL_VERSION),
                hello.getString(VoiceAssistantContract.EXTRA_PARTNER_PROFILE_ID).orEmpty(),
                hello.getString(VoiceAssistantContract.EXTRA_PARTNER_CLIENT_NONCE).orEmpty(),
                requestedScopes,
            )
            if (validation != PartnerHelloValidation.Valid) return error(
                PartnerErrorCode.INTERNAL_ERROR,
                if (validation == PartnerHelloValidation.UnsupportedVersion) "不支持的协议版本" else "会话请求无效",
            )
            val callers = runBlocking { authorizedCallers.callersFlow.first() }
            when (CallerAuthorization.authorize(identity.packageName, identity.certificateSha256, requestedScopes, callers, System.currentTimeMillis())) {
                CallerDecision.Allowed -> Unit
                CallerDecision.NeedsUserApproval -> return authorizationRequired(identity, requestedScopes, hello.getString(VoiceAssistantContract.EXTRA_PARTNER_PROFILE_ID).orEmpty())
                CallerDecision.DeniedSignatureMismatch -> return error(PartnerErrorCode.AUTHORIZATION_REQUIRED, "应用签名与已有授权不一致")
                CallerDecision.DeniedScope -> return error(PartnerErrorCode.AUTHORIZATION_REQUIRED, "请求范围尚未获授权")
                else -> return error(PartnerErrorCode.INTERNAL_ERROR, "调用方不可用")
            }
            val session = sessions.create(
                caller = identity,
                profile = requireNotNull(PartnerProfile.fromWireValue(hello.getString(VoiceAssistantContract.EXTRA_PARTNER_PROFILE_ID).orEmpty())),
                scopes = requestedScopes,
                nowMs = System.currentTimeMillis(),
                ttlMs = SESSION_TTL_MS,
            )
            return Bundle().apply {
                putString("status", "ok")
                putString(VoiceAssistantContract.EXTRA_PARTNER_SESSION_ID, session.id)
                putLong("expires_at_ms", session.expiresAtMs)
            }
        }

        override fun submitAudio(sessionId: String, requestId: String, audio: ParcelFileDescriptor, options: Bundle, callback: IPartnerCallback) {
            val identity = resolveCaller() ?: run { callback.complete(error(PartnerErrorCode.INTERNAL_ERROR, "无法识别调用方")); return }
            val session = sessions.find(sessionId, identity, System.currentTimeMillis())
                ?: run { callback.complete(error(PartnerErrorCode.TOKEN_EXPIRED, "会话已过期或不属于当前 App")); return }
            if (!session.scopes.contains(PartnerScope.TRANSCRIBE) || requestId.isBlank()) {
                callback.complete(error(PartnerErrorCode.INTERNAL_ERROR, "转写请求无效")); return
            }
            val requestKey = "$sessionId:$requestId"
            if (activeRequests.containsKey(requestKey)) {
                callback.complete(error(PartnerErrorCode.INTERNAL_ERROR, "请求正在处理")); return
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val audioFile = runCatching { copyAudio(audio, requestId) }.getOrElse {
                    callback.complete(error(PartnerErrorCode.AUDIO_UNREADABLE, "无法读取音频：${it.message}")); return@launch
                }
                try {
                    transcribeMutex.withLock {
                        callback.progress("导入完成，开始识别")
                        val useMultithread = authStore.multiThreadFlow.first()
                        val result = transcriber.transcribe(
                            wavPath = audioFile.absolutePath,
                            request = TranscriptionRequest(
                                engine = authStore.engineFlow.first(),
                                modelId = authStore.selectedModelFlow.first(),
                                decodeMode = authStore.decodeModeFlow.first(),
                                language = "auto",
                                useGpuPreference = authStore.useGpuFlow.first(),
                                autoStrategy = authStore.autoStrategyFlow.first(),
                                useMultithread = useMultithread,
                                threadCount = if (useMultithread) authStore.threadCountFlow.first() else 1,
                                sherpaProvider = authStore.sherpaProviderFlow.first(),
                                sherpaStreamingModel = authStore.sherpaStreamingModelFlow.first(),
                                sherpaOfflineModel = authStore.sherpaOfflineModelFlow.first(),
                            ),
                            deviceProfile = profile,
                            onProgress = { callback.progress(it) },
                        )
                        if (result.error != null) callback.complete(error(PartnerErrorCode.MODEL_UNAVAILABLE, result.error)) else {
                            val formatted = com.voiceassistant.text.TextConverters.formatTranscript(result.text.orEmpty())
                            val species = if (session.profile == PartnerProfile.PLANKTON_V1 && session.scopes.contains(PartnerScope.DOMAIN_PROFILE)) {
                                options.getStringArrayList(VoiceAssistantContract.EXTRA_PARTNER_SPECIES)
                                    .orEmpty().map(String::trim).filter(String::isNotBlank).distinct().take(MAX_PROFILE_SPECIES)
                            } else emptyList()
                            val pointId = options.getString(VoiceAssistantContract.EXTRA_PARTNER_POINT_ID)?.trim()?.takeIf(String::isNotBlank)
                            val review = DomainTranscriptNormalizer().normalize(
                                formatted,
                                DomainNormalizationContext(session.profile, pointId, species),
                            )
                            callback.complete(Bundle().apply {
                                putString("status", "ok")
                                putString(VoiceAssistantContract.EXTRA_REQUEST_ID, requestId)
                                putString(VoiceAssistantContract.EXTRA_RAW_TEXT, formatted)
                                putString(VoiceAssistantContract.EXTRA_PARTNER_NORMALIZED_TEXT, review.normalizedText)
                                putStringArrayList(VoiceAssistantContract.EXTRA_PARTNER_UNCERTAIN_SPANS, ArrayList(review.uncertainSpans.map { it.text }))
                                putStringArrayList(VoiceAssistantContract.EXTRA_PARTNER_PROPOSED_ACTIONS, ArrayList(review.proposedActions.map { "${it.type}|${it.pointId}|${it.species}|${it.value}" }))
                            })
                        }
                    }
                } catch (_: CancellationException) {
                    callback.complete(error(PartnerErrorCode.CANCELLED, "已取消转写"))
                } finally {
                    audioFile.delete()
                    activeRequests.remove(requestKey)
                }
            }
            if (activeRequests.putIfAbsent(requestKey, job) == null) {
                job.start()
            } else {
                runCatching { audio.close() }
                callback.complete(error(PartnerErrorCode.INTERNAL_ERROR, "请求正在处理"))
            }
        }

        override fun cancel(sessionId: String, requestId: String) {
            val identity = resolveCaller() ?: return
            val session = sessions.find(sessionId, identity, System.currentTimeMillis()) ?: return
            activeRequests.remove("${session.id}:$requestId")?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == VoiceAssistantContract.ACTION_BIND_PARTNER_BROKER) broker else null
    override fun onDestroy() { transcriber.release(); super.onDestroy() }

    private fun resolveCaller(): PartnerCallerIdentity? {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid())?.distinct().orEmpty()
        if (packages.size != 1) return null
        val packageName = packages.single()
        return readSignatureSha256(this, packageName)?.let { PartnerCallerIdentity(packageName, it) }
    }

    private fun copyAudio(descriptor: ParcelFileDescriptor, requestId: String): File {
        val target = File(File(cacheDir, "partner_audio").apply { mkdirs() }, "${requestId.hashCode().toUInt()}.wav")
        FileInputStream(descriptor.fileDescriptor).use { input -> target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0L
            while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= MAX_AUDIO_BYTES); output.write(buffer, 0, count) }
        } }
        descriptor.close(); return target
    }

    private fun error(code: PartnerErrorCode, message: String) = Bundle().apply { putString("status", "error"); putString(VoiceAssistantContract.EXTRA_PARTNER_ERROR_CODE, code.wireValue); putString(VoiceAssistantContract.EXTRA_ERROR_MESSAGE, message) }
    private fun authorizationRequired(identity: PartnerCallerIdentity, scopes: Set<PartnerScope>, profileId: String) = error(PartnerErrorCode.AUTHORIZATION_REQUIRED, "需要在语音助手中授权此 App").apply {
        val intent = Intent(this@PartnerBrokerService, PartnerAuthorizationActivity::class.java).apply {
            putExtra(PartnerAuthorizationActivity.EXTRA_PACKAGE_NAME, identity.packageName)
            putExtra(PartnerAuthorizationActivity.EXTRA_CERTIFICATE, identity.certificateSha256)
            putStringArrayListExtra(PartnerAuthorizationActivity.EXTRA_SCOPES, ArrayList(scopes.map(PartnerScope::wireValue)))
            putExtra(PartnerAuthorizationActivity.EXTRA_PROFILE_ID, profileId)
        }
        putParcelable("authorization_intent", PendingIntent.getActivity(this@PartnerBrokerService, (identity.packageName + scopes).hashCode(), intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE))
    }
    private fun IPartnerCallback.progress(message: String) = runCatching { onProgress(Bundle().apply { putString("message", message) }) }
    private fun IPartnerCallback.complete(result: Bundle) = runCatching { onCompleted(result) }

    companion object {
        private const val SESSION_TTL_MS = 10 * 60 * 1000L
        private const val MAX_AUDIO_BYTES = 50L * 1024 * 1024
        private const val MAX_PROFILE_SPECIES = 1_000
    }
}
