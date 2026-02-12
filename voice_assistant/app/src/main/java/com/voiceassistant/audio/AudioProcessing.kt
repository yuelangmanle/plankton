package com.voiceassistant.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PreparedAudio(
    val file: File,
    val samples: FloatArray,
    val sampleRate: Int,
    val tempFile: Boolean,
) {
    val durationSec: Float = if (sampleRate > 0) samples.size.toFloat() / sampleRate.toFloat() else 0f
}

internal data class AudioSegment(
    val index: Int,
    val startSample: Int,
    val endSample: Int,
) {
    val lengthSamples: Int = max(0, endSample - startSample)
}

internal data class AudioPrepResult(
    val audio: PreparedAudio? = null,
    val error: String? = null,
)

internal object AudioPreprocessor {
    private const val TargetSampleRate = 16000

    suspend fun prepare(context: Context, input: File): AudioPrepResult {
        return withContext(Dispatchers.IO) {
            if (!input.exists()) {
                return@withContext AudioPrepResult(error = "音频文件不存在")
            }
            val decoded = AudioImport.convertFileToWav(context, input)
            val sourceFile = decoded.file
                ?: return@withContext AudioPrepResult(error = decoded.error ?: "音频解码失败")
            val convertedTemp = sourceFile.absolutePath != input.absolutePath

            val read = WavReader.readMono(sourceFile)
            if (read.error != null || read.samples == null) {
                return@withContext AudioPrepResult(error = read.error ?: "读取音频失败")
            }

            val mono = read.samples
            val resampled = if (read.sampleRate != TargetSampleRate) {
                resampleLinear(mono, read.sampleRate, TargetSampleRate)
            } else {
                mono
            }

            if (resampled.isEmpty()) {
                return@withContext AudioPrepResult(error = "音频数据为空")
            }

            val needsRewrite = read.sampleRate != TargetSampleRate || read.bitsPerSample != 16 || read.channels != 1
            if (!needsRewrite) {
                return@withContext AudioPrepResult(
                    audio = PreparedAudio(
                        file = sourceFile,
                        samples = resampled,
                        sampleRate = TargetSampleRate,
                        tempFile = convertedTemp,
                    ),
                )
            }

            val dir = File(context.cacheDir, "preprocessed").apply { mkdirs() }
            val outFile = File(dir, "prep_${System.currentTimeMillis()}.wav")
            writePcm16Mono(outFile, resampled, TargetSampleRate)
            if (convertedTemp) {
                runCatching { sourceFile.delete() }
            }
            return@withContext AudioPrepResult(
                audio = PreparedAudio(
                    file = outFile,
                    samples = resampled,
                    sampleRate = TargetSampleRate,
                    tempFile = true,
                ),
            )
        }
    }

    private fun resampleLinear(input: FloatArray, inRate: Int, outRate: Int): FloatArray {
        if (input.isEmpty() || inRate <= 0 || outRate <= 0 || inRate == outRate) return input
        val ratio = outRate.toDouble() / inRate.toDouble()
        val outLen = max(1, (input.size * ratio).roundToInt())
        val output = FloatArray(outLen)
        for (i in output.indices) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = srcIndex - idx
            val v0 = input[min(idx, input.size - 1)]
            val v1 = input[min(idx + 1, input.size - 1)]
            output[i] = v0 + ((v1 - v0) * frac).toFloat()
        }
        return output
    }

    private fun writePcm16Mono(outFile: File, samples: FloatArray, sampleRate: Int) {
        val dataLen = samples.size * 2L
        RandomAccessFile(outFile, "rw").use { raf ->
            WavUtils.writeHeader(raf, sampleRate, 1, 16, dataLen)
            val buffer = ByteArray(2)
            for (sample in samples) {
                val clamped = max(-1f, min(1f, sample))
                val value = (clamped * 32767f).roundToInt().toShort()
                buffer[0] = (value.toInt() and 0xFF).toByte()
                buffer[1] = (value.toInt() shr 8 and 0xFF).toByte()
                raf.write(buffer)
            }
            WavUtils.updateHeader(raf, sampleRate, 1, 16, dataLen)
        }
    }
}

internal object AudioSegmenter {
    fun split(
        samples: FloatArray,
        sampleRate: Int,
        minSegmentSec: Int,
        maxSegmentSec: Int,
    ): List<AudioSegment> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        val durationSec = samples.size.toFloat() / sampleRate.toFloat()
        if (durationSec <= maxSegmentSec) {
            return listOf(AudioSegment(index = 0, startSample = 0, endSample = samples.size))
        }

        val frameSize = (0.02f * sampleRate).roundToInt().coerceAtLeast(160)
        val hopSize = (0.01f * sampleRate).roundToInt().coerceAtLeast(80)
        val frameCount = max(1, (samples.size - frameSize) / hopSize + 1)

        val rms = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            val start = i * hopSize
            val end = min(samples.size, start + frameSize)
            var sum = 0f
            for (j in start until end) {
                val v = samples[j]
                sum += v * v
            }
            val mean = if (end > start) sum / (end - start) else 0f
            rms[i] = kotlin.math.sqrt(mean)
        }

        val sorted = rms.clone()
        sorted.sort()
        val noiseFloor = sorted[(sorted.size * 0.1f).roundToInt().coerceIn(0, sorted.size - 1)]
        val threshold = max(0.01f, noiseFloor * 3f)

        val minSilenceFrames = (0.3f * sampleRate / hopSize).roundToInt().coerceAtLeast(10)
        val minVoiceFrames = (0.2f * sampleRate / hopSize).roundToInt().coerceAtLeast(5)

        val segments = mutableListOf<AudioSegment>()
        var inSpeech = false
        var speechStart = 0
        var voiceFrames = 0
        var silenceFrames = 0
        var lastVoiceFrame = 0

        for (i in 0 until frameCount) {
            val speech = rms[i] >= threshold
            if (speech) {
                voiceFrames++
                silenceFrames = 0
                if (!inSpeech && voiceFrames >= minVoiceFrames) {
                    inSpeech = true
                    speechStart = max(0, i - minVoiceFrames + 1)
                }
                if (inSpeech) {
                    lastVoiceFrame = i
                }
            } else {
                if (inSpeech) {
                    silenceFrames++
                    if (silenceFrames >= minSilenceFrames) {
                        val endFrame = lastVoiceFrame
                        addSegment(segments, hopSize, speechStart, endFrame, samples.size)
                        inSpeech = false
                        voiceFrames = 0
                    }
                } else {
                    voiceFrames = 0
                }
            }
        }

        if (inSpeech) {
            addSegment(segments, hopSize, speechStart, lastVoiceFrame, samples.size)
        }

        if (segments.isEmpty()) {
            return listOf(AudioSegment(index = 0, startSample = 0, endSample = samples.size))
        }

        val padded = segments.map { segment ->
            val pad = (0.2f * sampleRate).roundToInt()
            val start = max(0, segment.startSample - pad)
            val end = min(samples.size, segment.endSample + pad)
            segment.copy(startSample = start, endSample = end)
        }

        val merged = mergeSegments(padded)
        val capped = splitByMaxLength(merged, sampleRate, minSegmentSec, maxSegmentSec)
        return capped.mapIndexed { idx, seg -> seg.copy(index = idx) }
    }

    private fun addSegment(
        segments: MutableList<AudioSegment>,
        hopSize: Int,
        startFrame: Int,
        endFrame: Int,
        totalSamples: Int,
    ) {
        val startSample = max(0, startFrame * hopSize)
        val endSample = min(endFrame * hopSize + hopSize, totalSamples)
        val safeEnd = max(startSample + 1, endSample)
        segments.add(AudioSegment(index = segments.size, startSample = startSample, endSample = safeEnd))
    }

    private fun mergeSegments(segments: List<AudioSegment>): List<AudioSegment> {
        if (segments.isEmpty()) return segments
        val sorted = segments.sortedBy { it.startSample }
        val merged = mutableListOf<AudioSegment>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (next.startSample <= current.endSample + 3200) {
                current = current.copy(endSample = max(current.endSample, next.endSample))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private fun splitByMaxLength(
        segments: List<AudioSegment>,
        sampleRate: Int,
        minSegmentSec: Int,
        maxSegmentSec: Int,
    ): List<AudioSegment> {
        val maxSamples = maxSegmentSec * sampleRate
        val minSamples = minSegmentSec * sampleRate
        val result = mutableListOf<AudioSegment>()
        for (segment in segments) {
            var start = segment.startSample
            val end = segment.endSample
            while (end - start > maxSamples) {
                result.add(AudioSegment(index = result.size, startSample = start, endSample = start + maxSamples))
                start += maxSamples
            }
            val remaining = end - start
            if (remaining > 0) {
                if (remaining < minSamples && result.isNotEmpty()) {
                    val previous = result.removeLast()
                    result.add(previous.copy(endSample = end))
                } else {
                    result.add(AudioSegment(index = result.size, startSample = start, endSample = end))
                }
            }
        }
        return result
    }

    fun writeSegments(context: Context, samples: FloatArray, sampleRate: Int, segments: List<AudioSegment>): List<File> {
        if (segments.isEmpty()) return emptyList()
        val dir = File(context.cacheDir, "segments").apply { mkdirs() }
        val files = mutableListOf<File>()
        for (segment in segments) {
            val outFile = File(dir, "seg_${System.currentTimeMillis()}_${segment.index}.wav")
            val length = segment.lengthSamples
            val dataLen = length * 2L
            RandomAccessFile(outFile, "rw").use { raf ->
                WavUtils.writeHeader(raf, sampleRate, 1, 16, dataLen)
                val buffer = ByteArray(2)
                for (i in 0 until length) {
                    val sample = samples[segment.startSample + i]
                    val clamped = max(-1f, min(1f, sample))
                    val value = (clamped * 32767f).roundToInt().toShort()
                    buffer[0] = (value.toInt() and 0xFF).toByte()
                    buffer[1] = (value.toInt() shr 8 and 0xFF).toByte()
                    raf.write(buffer)
                }
                WavUtils.updateHeader(raf, sampleRate, 1, 16, dataLen)
            }
            files.add(outFile)
        }
        return files
    }
}

internal object WavReader {
    data class Result(
        val sampleRate: Int = 0,
        val channels: Int = 0,
        val bitsPerSample: Int = 0,
        val samples: FloatArray? = null,
        val error: String? = null,
    )

    fun readMono(file: File): Result {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val riff = readString(raf, 4)
                if (riff != "RIFF") return Result(error = "不是有效的 RIFF/WAV 文件")
                raf.skipBytes(4)
                val wave = readString(raf, 4)
                if (wave != "WAVE") return Result(error = "不是有效的 WAV 文件")

                var channels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var audioFormat = 0
                var data: ByteArray? = null

                while (raf.filePointer < raf.length()) {
                    val chunkId = readString(raf, 4)
                    val chunkSize = readIntLE(raf)
                    when (chunkId) {
                        "fmt " -> {
                            audioFormat = readShortLE(raf).toInt()
                            channels = readShortLE(raf).toInt()
                            sampleRate = readIntLE(raf)
                            raf.skipBytes(6)
                            bitsPerSample = readShortLE(raf).toInt()
                            val remaining = chunkSize - 16
                            if (remaining > 0) raf.skipBytes(remaining)
                        }
                        "data" -> {
                            data = ByteArray(chunkSize)
                            raf.readFully(data)
                        }
                        else -> {
                            raf.skipBytes(chunkSize)
                        }
                    }
                    if (channels > 0 && sampleRate > 0 && data != null) break
                }

                if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0 || data == null) {
                    return Result(error = "WAV 文件头信息不完整")
                }
                if (audioFormat != 1 && audioFormat != 3) {
                    return Result(error = "暂不支持该 WAV 编码格式")
                }
                if (bitsPerSample != 16 && bitsPerSample != 32) {
                    return Result(error = "仅支持 16-bit PCM 或 32-bit float WAV")
                }

                val bytesPerSample = bitsPerSample / 8
                val frameCount = data.size / (bytesPerSample * channels)
                if (frameCount <= 0) return Result(error = "WAV 文件无有效音频数据")

                val mono = FloatArray(frameCount)
                if (bitsPerSample == 16) {
                    var offset = 0
                    for (i in 0 until frameCount) {
                        var sum = 0
                        for (c in 0 until channels) {
                            val lo = data[offset].toInt() and 0xFF
                            val hi = data[offset + 1].toInt()
                            val value = (hi shl 8) or lo
                            val sample = value.toShort()
                            sum += sample.toInt()
                            offset += 2
                        }
                        mono[i] = sum.toFloat() / (channels * 32768f)
                    }
                } else {
                    var offset = 0
                    for (i in 0 until frameCount) {
                        var sum = 0f
                        for (c in 0 until channels) {
                            val bits = (data[offset].toInt() and 0xFF) or
                                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                                ((data[offset + 3].toInt() and 0xFF) shl 24)
                            sum += Float.fromBits(bits)
                            offset += 4
                        }
                        mono[i] = sum / channels.toFloat()
                    }
                }

                Result(sampleRate = sampleRate, channels = channels, bitsPerSample = bitsPerSample, samples = mono)
            }
        }.getOrElse { ex ->
            Result(error = "读取 WAV 失败：${ex.message}")
        }
    }

    private fun readString(raf: RandomAccessFile, length: Int): String {
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return String(bytes)
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        val b4 = raf.read()
        return (b1 and 0xFF) or ((b2 and 0xFF) shl 8) or ((b3 and 0xFF) shl 16) or ((b4 and 0xFF) shl 24)
    }

    private fun readShortLE(raf: RandomAccessFile): Short {
        val b1 = raf.read()
        val b2 = raf.read()
        return ((b2 shl 8) or (b1 and 0xFF)).toShort()
    }
}
