package com.voiceassistant.diagnostics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Stores only operational timing and fallback reasons; audio, transcripts and caller identity never enter this log. */
internal class VoiceDiagnostics(private val context: Context) {
    private val lock = ReentrantLock()
    private val file = File(context.filesDir, "diagnostics/voice-diagnostics.json")

    fun record(taskId: String, stage: String, elapsedMs: Long, engine: String, fallbackReason: String? = null) = lock.withLock {
        val entries = readEntries().apply {
            put(JSONObject().apply {
                put("task", taskId.take(64))
                put("stage", stage.take(64))
                put("elapsed_ms", elapsedMs.coerceAtLeast(0))
                put("engine", engine.take(64))
                put("fallback", fallbackReason?.take(160))
                put("recorded_at_ms", System.currentTimeMillis())
            })
            while (length() > MAX_ENTRIES) remove(0)
        }
        file.parentFile?.mkdirs()
        file.writeText(entries.toString())
    }

    fun clear(): Boolean = !file.exists() || file.delete()

    fun exportRedacted(): Uri? = lock.withLock {
        if (!file.exists()) return null
        val export = File(context.cacheDir, "voice-diagnostics-export.json")
        export.writeText(readEntries().toString())
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export)
    }

    private fun readEntries(): JSONArray = runCatching {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    }.getOrDefault(JSONArray())

    private companion object { const val MAX_ENTRIES = 200 }
}
