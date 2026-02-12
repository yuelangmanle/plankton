package com.voiceassistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class VoiceDocEntry(val id: String, val title: String, val assetPath: String)

private val VOICE_DOCS = listOf(
    VoiceDocEntry(id = "project", title = "语音助手项目书", assetPath = "docs/语音助手项目书.enc"),
)

internal fun listVoiceDocs(): List<VoiceDocEntry> = VOICE_DOCS

internal suspend fun readVoiceDoc(context: Context, id: String): String? = withContext(Dispatchers.IO) {
    val entry = VOICE_DOCS.firstOrNull { it.id == id } ?: return@withContext null
    val password = DocAccessCache.password ?: return@withContext null
    readEncryptedAsset(context, entry.assetPath, password)
}
