package com.voiceassistant.bridge

import android.content.Intent
import android.net.Uri

object VoiceAssistantContract {
    const val ACTION_REQUEST_BULK_COMMAND = "com.voiceassistant.action.REQUEST_BULK_COMMAND"
    const val ACTION_TRANSCRIBE_AUDIO = "com.voiceassistant.action.TRANSCRIBE_AUDIO"
    const val ACTION_CANCEL_TRANSCRIBE_AUDIO = "com.voiceassistant.action.CANCEL_TRANSCRIBE_AUDIO"

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

data class BridgeRequest(
    val requestId: String,
    val inputType: String?,
    val inputText: String?,
    val template: String?,
    val contextUri: Uri?,
    val returnAction: String?,
    val returnPackage: String?,
    val requireConfirm: Boolean,
)

data class BridgeResult(
    val status: String,
    val bulkJson: String? = null,
    val rawText: String? = null,
    val rawApi: String? = null,
    val audioPath: String? = null,
    val warnings: String? = null,
    val errorMessage: String? = null,
)

fun Intent.readBridgeRequest(): BridgeRequest? {
    val requestId = getStringExtra(VoiceAssistantContract.EXTRA_REQUEST_ID)?.trim().orEmpty()
    if (requestId.isBlank()) return null
    val inputType = getStringExtra(VoiceAssistantContract.EXTRA_INPUT_TYPE)
    val inputText = getStringExtra(VoiceAssistantContract.EXTRA_INPUT_TEXT)
    val template = getStringExtra(VoiceAssistantContract.EXTRA_TEMPLATE)
    val contextUri = getStringExtra(VoiceAssistantContract.EXTRA_CONTEXT_URI)?.let { Uri.parse(it) }
    val returnAction = getStringExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION)
    val returnPackage = getStringExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE)
    val requireConfirm = getBooleanExtra(VoiceAssistantContract.EXTRA_REQUIRE_CONFIRM, false)
    return BridgeRequest(
        requestId = requestId,
        inputType = inputType,
        inputText = inputText,
        template = template,
        contextUri = contextUri,
        returnAction = returnAction,
        returnPackage = returnPackage,
        requireConfirm = requireConfirm,
    )
}

fun buildRequestIntent(request: BridgeRequest): Intent {
    val intent = Intent(VoiceAssistantContract.ACTION_REQUEST_BULK_COMMAND)
    intent.putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, request.requestId)
    intent.putExtra(VoiceAssistantContract.EXTRA_INPUT_TYPE, request.inputType)
    intent.putExtra(VoiceAssistantContract.EXTRA_INPUT_TEXT, request.inputText)
    intent.putExtra(VoiceAssistantContract.EXTRA_TEMPLATE, request.template)
    intent.putExtra(VoiceAssistantContract.EXTRA_CONTEXT_URI, request.contextUri?.toString())
    intent.putExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION, request.returnAction)
    intent.putExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE, request.returnPackage)
    intent.putExtra(VoiceAssistantContract.EXTRA_REQUIRE_CONFIRM, request.requireConfirm)
    return intent
}

fun buildResultIntent(request: BridgeRequest, result: BridgeResult): Intent {
    val intent = Intent(request.returnAction)
    request.returnPackage?.let { intent.setPackage(it) }
    intent.putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, request.requestId)
    intent.putExtra(VoiceAssistantContract.EXTRA_STATUS, result.status)
    intent.putExtra(VoiceAssistantContract.EXTRA_BULK_JSON, result.bulkJson)
    intent.putExtra(VoiceAssistantContract.EXTRA_RAW_TEXT, result.rawText)
    intent.putExtra(VoiceAssistantContract.EXTRA_RAW_API, result.rawApi)
    intent.putExtra(VoiceAssistantContract.EXTRA_AUDIO_PATH, result.audioPath)
    intent.putExtra(VoiceAssistantContract.EXTRA_WARNINGS, result.warnings)
    intent.putExtra(VoiceAssistantContract.EXTRA_ERROR_MESSAGE, result.errorMessage)
    return intent
}
