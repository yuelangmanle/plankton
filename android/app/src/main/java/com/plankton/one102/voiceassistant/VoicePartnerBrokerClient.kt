package com.plankton.one102.voiceassistant

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.voiceassistant.partner.IPartnerBroker
import com.voiceassistant.partner.IPartnerCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

class VoicePartnerBrokerClient(private val context: Context) {
    sealed interface BeginResult {
        data class Ready(val sessionId: String, val broker: IPartnerBroker) : BeginResult
        data class AuthorizationRequired(val intent: PendingIntent?) : BeginResult
        data class Failed(val message: String) : BeginResult
    }

    suspend fun begin(profileId: String = "plankton-v1"): BeginResult {
        val broker = connect() ?: return BeginResult.Failed("无法连接语音助手")
        val capabilities = runCatching { broker.capabilities }.getOrElse {
            close()
            return BeginResult.Failed("语音助手未返回能力信息")
        }
        if (capabilities.getInt("protocol_version") != 2 || !capabilities.getStringArrayList("profiles").orEmpty().contains(profileId)) {
            close()
            return BeginResult.Failed("语音助手版本不支持当前接入方式")
        }
        val result = broker.beginSession(Bundle().apply {
                putInt("partner_protocol_version", 2)
                putString("partner_profile_id", profileId)
                putString("partner_client_nonce", UUID.randomUUID().toString())
                putStringArrayList("partner_requested_scopes", arrayListOf("transcribe", "background_transcribe", "progress_callback", "domain_profile"))
            })
        return when {
                result.getString("status") == "ok" -> BeginResult.Ready(result.getString("partner_session_id").orEmpty(), broker)
                result.getString("partner_error_code") == "AUTHORIZATION_REQUIRED" -> BeginResult.AuthorizationRequired(result.getParcelable("authorization_intent", PendingIntent::class.java))
                else -> BeginResult.Failed(result.getString("error_message") ?: "语音助手拒绝请求")
            }.also { if (it !is BeginResult.Ready) close() }
    }

    fun submit(
        broker: IPartnerBroker,
        sessionId: String,
        requestId: String,
        audio: File,
        options: Bundle = Bundle(),
        onProgress: (String) -> Unit,
        onCompleted: (Bundle) -> Unit,
    ) {
        val descriptor = ParcelFileDescriptor.open(audio, ParcelFileDescriptor.MODE_READ_ONLY)
        broker.submitAudio(sessionId, requestId, descriptor, options, object : IPartnerCallback.Stub() {
            override fun onProgress(update: Bundle) { onProgress(update.getString("message").orEmpty()) }
            override fun onCompleted(result: Bundle) { onCompleted(result); close() }
        })
    }

    fun close() { runCatching { context.unbindService(connection) } }

    private lateinit var connection: ServiceConnection
    private suspend fun connect(): IPartnerBroker? = suspendCancellableCoroutine { continuation ->
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { continuation.resume(IPartnerBroker.Stub.asInterface(service)) }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val bound = context.bindService(Intent("com.voiceassistant.action.BIND_PARTNER_BROKER").setPackage("com.voiceassistant"), connection, Context.BIND_AUTO_CREATE)
        if (!bound) continuation.resume(null)
        continuation.invokeOnCancellation { runCatching { context.unbindService(connection) } }
    }
}
