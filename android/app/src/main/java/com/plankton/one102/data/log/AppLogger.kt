package com.plankton.one102.data.log

import android.content.Context
import android.net.Uri
import com.plankton.one102.domain.nowIso
import com.plankton.one102.export.writeFileToSafAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppLogger {
    private const val LOG_DIR = "logs"
    private const val MAIN_LOG = "app.log"
    private const val MAX_LOG_BYTES = 2 * 1024 * 1024
    private const val MAX_LOG_FILES = 5
    private val lock = Any()

    fun logInfo(context: Context, tag: String, message: String) {
        log(context, "INFO", tag, message, null)
    }

    fun logError(context: Context, tag: String, message: String, error: Throwable? = null) {
        log(context, "ERROR", tag, message, error)
    }

    fun clear(context: Context) {
        synchronized(lock) {
            logDir(context).listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun exportLogs(context: Context, uri: Uri) {
        val logFiles = getLogFiles(context)
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val tempFile = File.createTempFile("plankton-logs-", ".zip", dir)
        try {
            withContext(Dispatchers.IO) {
                ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                    if (logFiles.isEmpty()) {
                        zos.putNextEntry(ZipEntry("app.log"))
                        zos.write("no logs".toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    } else {
                        for (file in logFiles) {
                            zos.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            writeFileToSafAtomic(context, uri, tempFile)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    fun getLogFiles(context: Context): List<File> {
        val dir = logDir(context)
        return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun log(context: Context, level: String, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(nowIso())
            append(" [")
            append(level)
            append("] ")
            append(tag)
            append(": ")
            append(message.trim())
            if (error != null) {
                appendLine()
                append(error.stackTraceToString())
            }
            appendLine()
        }
        synchronized(lock) {
            rotateIfNeeded(context)
            val file = mainLogFile(context)
            file.appendText(line, Charsets.UTF_8)
        }
    }

    private fun logDir(context: Context): File = File(context.filesDir, LOG_DIR).apply { mkdirs() }

    private fun mainLogFile(context: Context): File = File(logDir(context), MAIN_LOG)

    private fun rotateIfNeeded(context: Context) {
        val file = mainLogFile(context)
        if (!file.exists()) return
        if (file.length() < MAX_LOG_BYTES) return

        val stamp = nowIso().replace(':', '-')
        val rotated = File(logDir(context), "app-$stamp.log")
        file.renameTo(rotated)

        val all = logDir(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        if (all.size <= MAX_LOG_FILES) return
        for (f in all.drop(MAX_LOG_FILES)) {
            runCatching { f.delete() }
        }
    }
}
