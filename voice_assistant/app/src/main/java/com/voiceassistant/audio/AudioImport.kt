package com.voiceassistant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal data class AudioImportResult(
    val file: File? = null,
    val error: String? = null,
    val sourceName: String? = null,
)

internal object AudioImport {
    suspend fun importToWav(context: Context, uri: Uri): AudioImportResult {
        return withContext(Dispatchers.IO) {
            val name = queryDisplayName(context, uri)
            val cacheDir = File(context.cacheDir, "imports").apply { mkdirs() }
            val outFile = File(cacheDir, "import_${System.currentTimeMillis()}.wav")

            val isWav = isWavFile(context, uri)
            if (isWav) {
                return@withContext copyToFile(context, uri, outFile, name)
            }

            return@withContext decodeToWav(context, uri, outFile, name)
        }
    }

    suspend fun convertFileToWav(context: Context, input: File): AudioImportResult {
        return withContext(Dispatchers.IO) {
            if (!input.exists()) {
                return@withContext AudioImportResult(error = "音频文件不存在", sourceName = input.name)
            }
            if (isWavFile(input)) {
                return@withContext AudioImportResult(file = input, sourceName = input.name)
            }
            val cacheDir = File(context.cacheDir, "decoded").apply { mkdirs() }
            val outFile = File(cacheDir, "decode_${System.currentTimeMillis()}.wav")
            return@withContext decodeToWav(input, outFile, input.name)
        }
    }

    private fun copyToFile(context: Context, uri: Uri, outFile: File, name: String?): AudioImportResult {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return AudioImportResult(error = "无法读取音频文件", sourceName = name)
            AudioImportResult(file = outFile, sourceName = name)
        }.getOrElse {
            AudioImportResult(error = "音频导入失败：${it.message}", sourceName = name)
        }
    }

    private fun decodeToWav(context: Context, uri: Uri, outFile: File, name: String?): AudioImportResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@firstOrNull false
                mime.startsWith("audio/")
            } ?: return AudioImportResult(error = "未找到可解码的音频轨道", sourceName = name)

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return AudioImportResult(error = "音频格式未知", sourceName = name)

            if (!format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = safeGetInt(format, MediaFormat.KEY_SAMPLE_RATE, 16000)
            var channels = safeGetInt(format, MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = safeGetInt(format, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            val bufferInfo = MediaCodec.BufferInfo()
            var totalBytes = 0L
            var sawInputEos = false
            var sawOutputEos = false

            RandomAccessFile(outFile, "rw").use { raf ->
                WavUtils.writeHeader(raf, sampleRate, channels, 16, 0)

                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sampleRate = safeGetInt(outputFormat, MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = safeGetInt(outputFormat, MediaFormat.KEY_CHANNEL_COUNT, channels)
                            pcmEncoding = safeGetInt(outputFormat, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        }
                        outputIndex >= 0 -> {
                            if (bufferInfo.size > 0) {
                                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                                    return AudioImportResult(error = "暂不支持非 16-bit PCM 音频（$pcmEncoding）", sourceName = name)
                                }
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val chunk = ByteArray(bufferInfo.size)
                                    outputBuffer.get(chunk)
                                    raf.write(chunk)
                                    totalBytes += chunk.size
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }

                WavUtils.updateHeader(raf, sampleRate, channels, 16, totalBytes)
            }

            if (totalBytes <= 0L) {
                return AudioImportResult(error = "音频解码失败或为空", sourceName = name)
            }

            return AudioImportResult(file = outFile, sourceName = name)
        } catch (ex: Exception) {
            return AudioImportResult(error = "音频解码失败：${ex.message}", sourceName = name)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun decodeToWav(input: File, outFile: File, name: String?): AudioImportResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@firstOrNull false
                mime.startsWith("audio/")
            } ?: return AudioImportResult(error = "未找到可解码的音频轨道", sourceName = name)

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return AudioImportResult(error = "音频格式未知", sourceName = name)

            if (!format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = safeGetInt(format, MediaFormat.KEY_SAMPLE_RATE, 16000)
            var channels = safeGetInt(format, MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = safeGetInt(format, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            val bufferInfo = MediaCodec.BufferInfo()
            var totalBytes = 0L
            var sawInputEos = false
            var sawOutputEos = false

            RandomAccessFile(outFile, "rw").use { raf ->
                WavUtils.writeHeader(raf, sampleRate, channels, 16, 0)

                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sampleRate = safeGetInt(outputFormat, MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = safeGetInt(outputFormat, MediaFormat.KEY_CHANNEL_COUNT, channels)
                            pcmEncoding = safeGetInt(outputFormat, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        }
                        outputIndex >= 0 -> {
                            if (bufferInfo.size > 0) {
                                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                                    return AudioImportResult(error = "暂不支持非 16-bit PCM 音频（$pcmEncoding）", sourceName = name)
                                }
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val chunk = ByteArray(bufferInfo.size)
                                    outputBuffer.get(chunk)
                                    raf.write(chunk)
                                    totalBytes += chunk.size
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }

                WavUtils.updateHeader(raf, sampleRate, channels, 16, totalBytes)
            }

            if (totalBytes <= 0L) {
                return AudioImportResult(error = "音频解码失败或为空", sourceName = name)
            }

            return AudioImportResult(file = outFile, sourceName = name)
        } catch (ex: Exception) {
            return AudioImportResult(error = "音频解码失败：${ex.message}", sourceName = name)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun isWavFile(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@runCatching false
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                riff == "RIFF" && wave == "WAVE"
            } ?: false
        }.getOrDefault(false)
    }

    private fun isWavFile(file: File): Boolean {
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@runCatching false
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                riff == "RIFF" && wave == "WAVE"
            }
        }.getOrDefault(false)
    }

    private fun safeGetInt(format: MediaFormat, key: String, fallback: Int): Int {
        return runCatching {
            if (format.containsKey(key)) {
                format.getInteger(key)
            } else {
                fallback
            }
        }.getOrDefault(fallback)
    }
}
