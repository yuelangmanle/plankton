package com.plankton.one102.voiceassistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class VoiceAssistantRecording(
    val file: File? = null,
    val durationMs: Long = 0,
    val error: String? = null,
)

class VoiceAssistantRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recording = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val recordedBytes = AtomicLong(0)
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0
    private var pauseStartMs: Long = 0
    private val pausedDurationMs = AtomicLong(0)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!recording.compareAndSet(false, true)) return false
        paused.set(false)
        recordedBytes.set(0)
        pausedDurationMs.set(0)
        startTimeMs = SystemClock.elapsedRealtime()

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize <= 0) {
            recording.set(false)
            return false
        }

        val format = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .build()

        val candidates = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        var record: AudioRecord? = null
        for (candidate in candidates) {
            val built = AudioRecord.Builder()
                .setAudioSource(candidate)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize * 2)
                .build()
            if (built.state == AudioRecord.STATE_INITIALIZED) {
                record = built
                break
            } else {
                built.release()
            }
        }

        if (record == null) {
            recording.set(false)
            return false
        }

        val dir = File(context.cacheDir, "voice_assistant").apply { mkdirs() }
        val file = File(dir, "va_${System.currentTimeMillis()}.wav")
        outputFile = file
        audioRecord = record

        job = scope.launch {
            RandomAccessFile(file, "rw").use { raf ->
                WavUtils.writeHeader(raf, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, 0)
                val buffer = ByteArray(bufferSize)
                record.startRecording()
                var warmupRemaining = (SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) * WARMUP_MS / 1000).toLong()
                while (recording.get()) {
                    if (paused.get()) {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            runCatching { record.stop() }
                        }
                        Thread.sleep(80)
                        continue
                    } else if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        runCatching { record.startRecording() }
                    }
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        if (warmupRemaining > 0) {
                            warmupRemaining -= read.toLong()
                            continue
                        }
                        raf.write(buffer, 0, read)
                        recordedBytes.addAndGet(read.toLong())
                    } else if (read < 0) {
                        break
                    }
                }
                WavUtils.updateHeader(raf, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, recordedBytes.get())
            }
        }

        return true
    }

    suspend fun stop(): VoiceAssistantRecording {
        if (!recording.get()) {
            return VoiceAssistantRecording(file = outputFile, durationMs = 0, error = "未在录音")
        }
        recording.set(false)
        paused.set(false)
        runCatching { audioRecord?.stop() }
        val currentJob = job
        if (currentJob != null) {
            runCatching { currentJob.join() }
        }
        runCatching { audioRecord?.release() }
        audioRecord = null
        job = null

        val duration = (SystemClock.elapsedRealtime() - startTimeMs - pausedDurationMs.get()).coerceAtLeast(0)
        val file = outputFile
        if (file == null || !file.exists() || recordedBytes.get() <= 0) {
            return VoiceAssistantRecording(file = file, durationMs = duration, error = "录音失败")
        }
        appendTailPadding(file, TAIL_PADDING_MS)
        return VoiceAssistantRecording(file = file, durationMs = duration)
    }

    fun pause(): Boolean {
        if (!recording.get()) return false
        if (paused.compareAndSet(false, true)) {
            pauseStartMs = SystemClock.elapsedRealtime()
            runCatching { audioRecord?.stop() }
            return true
        }
        return false
    }

    fun resume(): Boolean {
        if (!recording.get()) return false
        if (paused.compareAndSet(true, false)) {
            val now = SystemClock.elapsedRealtime()
            val pausedMs = (now - pauseStartMs).coerceAtLeast(0)
            pausedDurationMs.addAndGet(pausedMs)
            runCatching { audioRecord?.startRecording() }
            return true
        }
        return false
    }

    private fun appendTailPadding(file: File, paddingMs: Long) {
        if (paddingMs <= 0) return
        val bytesPerMs = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) / 1000
        val paddingBytes = paddingMs * bytesPerMs
        if (paddingBytes <= 0) return
        val buffer = ByteArray(2048)
        var remaining = paddingBytes
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            while (remaining > 0) {
                val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                raf.write(buffer, 0, chunk)
                remaining -= chunk
            }
            val total = recordedBytes.addAndGet(paddingBytes)
            WavUtils.updateHeader(raf, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, total)
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val TAIL_PADDING_MS = 200L
        private const val WARMUP_MS = 120L
    }
}
