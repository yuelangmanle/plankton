package com.plankton.one102.voiceassistant

import android.content.Intent
import android.net.Uri

object VoiceAssistantContract {
    const val ACTION_REQUEST_BULK_COMMAND = "com.voiceassistant.action.REQUEST_BULK_COMMAND"
    const val ACTION_TRANSCRIBE_AUDIO = "com.voiceassistant.action.TRANSCRIBE_AUDIO"
    const val ACTION_CANCEL_TRANSCRIBE_AUDIO = "com.voiceassistant.action.CANCEL_TRANSCRIBE_AUDIO"
    const val ACTION_RECEIVE_BULK_COMMAND = "com.plankton.one102.action.RECEIVE_BULK_COMMAND"

    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_INPUT_TYPE = "input_type"
    const val EXTRA_INPUT_TEXT = "input_text"
    const val EXTRA_TEMPLATE = "template"
    const val EXTRA_CONTEXT_URI = "context_uri"
    const val EXTRA_RETURN_ACTION = "return_action"
    const val EXTRA_RETURN_PACKAGE = "return_package"
    const val EXTRA_REQUIRE_CONFIRM = "require_confirm"
    const val EXTRA_AUDIO_URI = "audio_uri"
    const val EXTRA_ENGINE = "engine"
    const val EXTRA_MODEL_ID = "model_id"
    const val EXTRA_DECODE_MODE = "decode_mode"
    const val EXTRA_USE_GPU = "use_gpu"
    const val EXTRA_AUTO_STRATEGY = "auto_strategy"
    const val EXTRA_USE_MULTITHREAD = "use_multithread"
    const val EXTRA_THREAD_COUNT = "thread_count"
    const val EXTRA_SHERPA_PROVIDER = "sherpa_provider"
    const val EXTRA_SHERPA_STREAMING_MODEL = "sherpa_streaming_model"
    const val EXTRA_SHERPA_OFFLINE_MODEL = "sherpa_offline_model"

    const val EXTRA_STATUS = "status"
    const val EXTRA_BULK_JSON = "bulk_json"
    const val EXTRA_RAW_TEXT = "raw_text"
    const val EXTRA_RAW_API = "raw_api"
    const val EXTRA_AUDIO_PATH = "audio_path"
    const val EXTRA_WARNINGS = "warnings"
    const val EXTRA_ERROR_MESSAGE = "error_message"

    const val STATUS_OK = "ok"
    const val STATUS_ERROR = "error"
    const val STATUS_CANCEL = "cancel"

    const val INPUT_TYPE_VOICE = "voice"
    const val INPUT_TYPE_TEXT = "text"

    const val TEMPLATE_GENERAL = "general"
    const val TEMPLATE_COUNTS = "counts"
}

data class VoiceAssistantRequest(
    val requestId: String,
    val inputType: String? = null,
    val inputText: String? = null,
    val template: String? = null,
    val contextUri: Uri? = null,
    val returnAction: String? = null,
    val returnPackage: String? = null,
    val requireConfirm: Boolean = true,
)

data class VoiceAssistantResult(
    val requestId: String,
    val status: String,
    val bulkJson: String? = null,
    val rawText: String? = null,
    val rawApi: String? = null,
    val audioPath: String? = null,
    val warnings: String? = null,
    val errorMessage: String? = null,
    val requestMatched: Boolean = false,
)

fun buildRequestIntent(request: VoiceAssistantRequest): Intent {
    return Intent(VoiceAssistantContract.ACTION_REQUEST_BULK_COMMAND).apply {
        putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, request.requestId)
        putExtra(VoiceAssistantContract.EXTRA_INPUT_TYPE, request.inputType)
        putExtra(VoiceAssistantContract.EXTRA_INPUT_TEXT, request.inputText)
        putExtra(VoiceAssistantContract.EXTRA_TEMPLATE, request.template)
        putExtra(VoiceAssistantContract.EXTRA_CONTEXT_URI, request.contextUri?.toString())
        putExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION, request.returnAction)
        putExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE, request.returnPackage)
        putExtra(VoiceAssistantContract.EXTRA_REQUIRE_CONFIRM, request.requireConfirm)
    }
}

fun readVoiceAssistantResult(intent: Intent?): VoiceAssistantResult? {
    if (intent == null) return null
    val requestId = intent.getStringExtra(VoiceAssistantContract.EXTRA_REQUEST_ID)?.trim().orEmpty()
    if (requestId.isBlank()) return null
    val status = intent.getStringExtra(VoiceAssistantContract.EXTRA_STATUS)?.trim().orEmpty()
    if (status.isBlank()) return null
    return VoiceAssistantResult(
        requestId = requestId,
        status = status,
        bulkJson = intent.getStringExtra(VoiceAssistantContract.EXTRA_BULK_JSON),
        rawText = intent.getStringExtra(VoiceAssistantContract.EXTRA_RAW_TEXT),
        rawApi = intent.getStringExtra(VoiceAssistantContract.EXTRA_RAW_API),
        audioPath = intent.getStringExtra(VoiceAssistantContract.EXTRA_AUDIO_PATH),
        warnings = intent.getStringExtra(VoiceAssistantContract.EXTRA_WARNINGS),
        errorMessage = intent.getStringExtra(VoiceAssistantContract.EXTRA_ERROR_MESSAGE),
    )
}
