package com.voiceassistant.audio

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal object AudioShare {
    fun toShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
