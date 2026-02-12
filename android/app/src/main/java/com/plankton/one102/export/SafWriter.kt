package com.plankton.one102.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun writeFileToSafAtomic(context: Context, uri: Uri, source: File) {
    require(source.exists()) { "临时文件不存在" }
    require(source.length() > 0L) { "临时文件为空" }
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("无法打开输出流")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
    }
}
