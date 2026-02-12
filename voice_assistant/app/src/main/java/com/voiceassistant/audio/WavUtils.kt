package com.voiceassistant.audio

import java.io.RandomAccessFile

internal object WavUtils {
    fun writeHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataLength: Long,
    ) {
        val totalDataLen = 36 + dataLength
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE(totalDataLen.toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1)
        raf.writeShortLE(channels.toShort())
        raf.writeIntLE(sampleRate)
        raf.writeIntLE(byteRate)
        raf.writeShortLE((channels * (bitsPerSample / 8)).toShort())
        raf.writeShortLE(bitsPerSample.toShort())
        raf.writeBytes("data")
        raf.writeIntLE(dataLength.toInt())
    }

    fun updateHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataLength: Long,
    ) {
        writeHeader(raf, sampleRate, channels, bitsPerSample, dataLength)
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte(value shr 8 and 0xFF)
        writeByte(value shr 16 and 0xFF)
        writeByte(value shr 24 and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Short) {
        val v = value.toInt()
        writeByte(v and 0xFF)
        writeByte(v shr 8 and 0xFF)
    }
}
