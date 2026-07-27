package com.voiceassistant.tasks

import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.SherpaStreamingModel
import com.voiceassistant.data.TranscriptionEngine
import java.util.Base64

/** Stores only explicit per-task overrides so queued work remains stable across process recreation. */
internal data class TranscriptionOverrides(
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

internal object TranscriptionOverridesCodec {
    fun encode(overrides: TranscriptionOverrides): String? {
        val values = linkedMapOf<String, String>()
        overrides.engine?.let { values["engine"] = it.id }
        overrides.modelId?.let { values["model"] = it }
        overrides.decodeMode?.let { values["decode"] = it.name }
        overrides.useGpu?.let { values["gpu"] = it.toString() }
        overrides.autoStrategy?.let { values["auto"] = it.toString() }
        overrides.useMultithread?.let { values["multi"] = it.toString() }
        overrides.threadCount?.let { values["threads"] = it.toString() }
        overrides.sherpaProvider?.let { values["provider"] = it.id }
        overrides.sherpaStreamingModel?.let { values["streaming"] = it.id }
        overrides.sherpaOfflineModel?.let { values["offline"] = it.id }
        return values.takeIf { it.isNotEmpty() }?.entries?.joinToString("&") { (key, value) ->
            "$key=${Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))}"
        }
    }

    fun decode(payload: String?): TranscriptionOverrides {
        if (payload.isNullOrBlank()) return TranscriptionOverrides()
        val values = payload.split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0 || index == part.lastIndex) null else {
                val value = runCatching {
                    String(Base64.getUrlDecoder().decode(part.substring(index + 1)), Charsets.UTF_8)
                }.getOrNull()
                value?.let { part.substring(0, index) to it }
            }
        }.toMap()
        return TranscriptionOverrides(
            engine = values["engine"]?.let { raw -> TranscriptionEngine.entries.firstOrNull { it.id == raw } },
            modelId = values["model"],
            decodeMode = values["decode"]?.let { runCatching { DecodeMode.valueOf(it) }.getOrNull() },
            useGpu = values["gpu"]?.toBooleanStrictOrNull(),
            autoStrategy = values["auto"]?.toBooleanStrictOrNull(),
            useMultithread = values["multi"]?.toBooleanStrictOrNull(),
            threadCount = values["threads"]?.toIntOrNull()?.takeIf { it > 0 },
            sherpaProvider = values["provider"]?.let { raw -> SherpaProvider.entries.firstOrNull { it.id == raw } },
            sherpaStreamingModel = values["streaming"]?.let { raw -> SherpaStreamingModel.entries.firstOrNull { it.id == raw } },
            sherpaOfflineModel = values["offline"]?.let { raw -> SherpaOfflineModel.entries.firstOrNull { it.id == raw } },
        )
    }
}
