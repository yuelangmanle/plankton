package com.voiceassistant.audio

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

internal data class RecordingResult(
    val file: File?,
    val durationMs: Long,
    val error: String? = null,
)

internal class VoiceRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recording = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val recordedBytes = AtomicLong(0)
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0
    private var pauseStartMs: Long = 0
    private val pausedDurationMs = AtomicLong(0)
    private var recordFormat: RecordFormat = RecordFormat.M4A

    fun start(format: RecordFormat = RecordFormat.M4A): Boolean {
        if (!recording.compareAndSet(false, true)) return false
        paused.set(false)
        recordedBytes.set(0)
        pausedDurationMs.set(0)
        startTimeMs = SystemClock.elapsedRealtime()
        recordFormat = format

        return if (recordFormat == RecordFormat.WAV) {
            startWavRecording()
        } else {
            startM4aRecording()
        }
    }

    suspend fun stop(): RecordingResult {
        if (!recording.get()) {
            return RecordingResult(file = outputFile, durationMs = 0, error = "未在录音")
        }
        recording.set(false)
        paused.set(false)
        var stopError: String? = null

        if (recordFormat == RecordFormat.WAV) {
            runCatching { audioRecord?.stop() }
            val currentJob = job
            if (currentJob != null) {
                runCatching { currentJob.join() }
            }
            runCatching { audioRecord?.release() }
            audioRecord = null
            job = null
            val file = outputFile
            if (file != null && file.exists() && recordedBytes.get() > 0) {
                appendTailPadding(file, TAIL_PADDING_MS)
            }
        } else {
            val recorder = mediaRecorder
            mediaRecorder = null
            stopError = runCatching { recorder?.stop() }.exceptionOrNull()?.message
            runCatching { recorder?.release() }
        }

        val duration = (SystemClock.elapsedRealtime() - startTimeMs - pausedDurationMs.get()).coerceAtLeast(0)
        val file = outputFile
        val hasData = if (recordFormat == RecordFormat.WAV) {
            recordedBytes.get() > 0
        } else {
            (file?.length() ?: 0L) > 0L
        }
        if (file == null || !file.exists() || !hasData) {
            return RecordingResult(file = file, durationMs = duration, error = stopError ?: "录音失败")
        }
        return RecordingResult(file = file, durationMs = duration)
    }

    fun isRecording(): Boolean = recording.get()

    fun isPaused(): Boolean = paused.get()

    fun pause(): Boolean {
        if (!recording.get()) return false
        if (paused.compareAndSet(false, true)) {
            pauseStartMs = SystemClock.elapsedRealtime()
            if (recordFormat == RecordFormat.WAV) {
                runCatching { audioRecord?.stop() }
            } else {
                runCatching { mediaRecorder?.pause() }
            }
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
            if (recordFormat == RecordFormat.WAV) {
                runCatching { audioRecord?.startRecording() }
            } else {
                runCatching { mediaRecorder?.resume() }
            }
            return true
        }
        return false
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val AAC_BITRATE = 64000
        private const val TAIL_PADDING_MS = 200L
        private const val WARMUP_MS = 120L
    }

    private fun startWavRecording(): Boolean {
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

        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
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

    private fun startM4aRecording(): Boolean {
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "va_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder(context)
        return runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(SAMPLE_RATE)
            recorder.setAudioEncodingBitRate(AAC_BITRATE)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            outputFile = file
            mediaRecorder = recorder
            true
        }.getOrElse {
            runCatching { recorder.release() }
            recording.set(false)
            false
        }
    }
}
