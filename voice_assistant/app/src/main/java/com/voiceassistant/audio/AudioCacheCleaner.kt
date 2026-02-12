package com.voiceassistant.audio

import android.content.Context
import java.io.File

internal data class AudioCacheClearResult(
    val removedDirs: Int,
    val errors: List<String>,
)

internal object AudioCacheCleaner {
    private val cacheDirs = listOf("recordings", "imports", "decoded", "preprocessed", "segments")

    fun clear(context: Context): AudioCacheClearResult {
        var removed = 0
        val errors = mutableListOf<String>()
        cacheDirs.forEach { name ->
            val dir = File(context.cacheDir, name)
            if (!dir.exists()) return@forEach
            val result = runCatching { dir.deleteRecursively() }
            val ok = result.getOrNull()
            if (ok == true) {
                removed += 1
            } else {
                val message = result.exceptionOrNull()?.message
                errors += if (message.isNullOrBlank()) "删除${name}失败" else "删除${name}失败：$message"
            }
        }
        return AudioCacheClearResult(removed, errors)
    }
}
