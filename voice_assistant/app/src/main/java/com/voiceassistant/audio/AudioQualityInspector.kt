package com.voiceassistant.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object AudioQualityInspector {
    internal data class Report(
        val rmsDb: Float,
        val peakNorm: Float,
        val clipRatio: Float,
        val recommendation: String,
        val shouldRetry: Boolean,
    )

    @SuppressLint("MissingPermission")
    fun inspect(context: Context, durationMs: Int = 700): Report? {
        val sampleRate = 16_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (minBuffer <= 0) return null

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(encoding)
            .setChannelMask(channel)
            .build()

        val candidates = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        var recorder: AudioRecord? = null
        for (src in candidates) {
            val built = AudioRecord.Builder()
                .setAudioSource(src)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuffer * 2)
                .build()
            if (built.state == AudioRecord.STATE_INITIALIZED) {
                recorder = built
                break
            } else {
                built.release()
            }
        }
        val record = recorder ?: return null

        val bytesTarget = sampleRate * 2L * durationMs / 1000L
        val bytesReadTarget = max(bytesTarget, 4096L).toInt()
        val byteBuffer = ByteArray(minBuffer)
        var totalBytes = 0
        var clipCount = 0
        var sampleCount = 0
        var peak = 0
        var sumSq = 0.0

        return runCatching {
            record.startRecording()
            while (totalBytes < bytesReadTarget) {
                val read = record.read(byteBuffer, 0, byteBuffer.size)
                if (read <= 0) continue
                totalBytes += read
                var i = 0
                while (i + 1 < read) {
                    val lo = byteBuffer[i].toInt() and 0xFF
                    val hi = byteBuffer[i + 1].toInt()
                    val value = (hi shl 8) or lo
                    val s = value.toShort().toInt()
                    val abs = kotlin.math.abs(s)
                    peak = max(peak, abs)
                    if (abs >= 32000) clipCount++
                    val norm = s / 32768.0
                    sumSq += norm * norm
                    sampleCount++
                    i += 2
                }
            }
            val rms = if (sampleCount > 0) sqrt(sumSq / sampleCount) else 0.0
            val rmsDb = (20.0 * log10(max(rms, 1e-6))).toFloat()
            val peakNorm = min(1f, peak / 32768f)
            val clipRatio = if (sampleCount > 0) clipCount.toFloat() / sampleCount else 0f

            val (recommendation, retry) = when {
                clipRatio >= 0.08f -> "检测到削波偏高，建议拉远麦克风或降低音量后重录。" to true
                rmsDb <= -42f -> "输入音量偏低，建议靠近麦克风或提高说话音量。" to true
                rmsDb >= -14f -> "环境噪声偏高，建议切到准确模式或换安静环境。" to false
                else -> "录音前质检通过，可直接开始录音。" to false
            }

            Report(
                rmsDb = rmsDb,
                peakNorm = peakNorm,
                clipRatio = clipRatio,
                recommendation = recommendation,
                shouldRetry = retry,
            )
        }.getOrNull().also {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }
}

