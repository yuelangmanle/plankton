package com.plankton.one102.voiceassistant

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object VoiceAssistantAudioShare {
    fun toShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
