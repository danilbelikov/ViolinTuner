package com.violinjourney.app.core.audio.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * A decoder's output as it comes: 16-bit from most codecs, but float or 24- and 32-bit from a WAV or FLAC above 16 bits
 * (Android 10+ hands those out as they are). Read as 16-bit, such a file was loud noise. Pure.
 *
 * The encodings carry `AudioFormat`'s numbers as plain constants: 24- and 32-bit are API 31 fields, which must not be
 * inlined into code that runs below it.
 */
object PcmSamples {
    const val PCM_16BIT = 2
    const val PCM_8BIT = 3
    const val PCM_FLOAT = 4
    const val PCM_24BIT_PACKED = 21
    const val PCM_32BIT = 22

    /** Bytes of one sample of [encoding]; an encoding not known here is refused, rather than played as noise. */
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        PCM_8BIT -> 1
        PCM_16BIT -> 2
        PCM_24BIT_PACKED -> 3
        PCM_FLOAT, PCM_32BIT -> 4
        else -> throw IllegalStateException("unsupported pcm encoding $encoding")
    }

    /** The next sample of [buffer], in -1…1, read in [encoding] and in the buffer's own byte order. */
    fun next(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        PCM_16BIT -> buffer.getShort() / SHORT_SCALE
        PCM_FLOAT -> buffer.getFloat()
        PCM_8BIT -> ((buffer.get().toInt() and BYTE) - UNSIGNED_8BIT_ZERO) / BYTE_SCALE
        PCM_24BIT_PACKED -> {
            val first = buffer.get().toInt()
            val second = buffer.get().toInt() and BYTE
            val third = buffer.get().toInt()
            // the most significant byte keeps its sign, the other two are plain
            val value = if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
                (third shl TWO_BYTES) or (second shl ONE_BYTE) or (first and BYTE)
            } else {
                (first shl TWO_BYTES) or (second shl ONE_BYTE) or (third and BYTE)
            }
            value / INT24_SCALE
        }
        PCM_32BIT -> buffer.getInt() / INT_SCALE
        else -> throw IllegalStateException("unsupported pcm encoding $encoding")
    }

    /** -1…1 as a 16-bit sample, clipped at full scale. */
    fun toShort(value: Float): Short = (value * SHORT_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private const val BYTE = 0xFF
    private const val ONE_BYTE = 8
    private const val TWO_BYTES = 16
    private const val UNSIGNED_8BIT_ZERO = 128
    private const val BYTE_SCALE = 128f
    private const val SHORT_SCALE = 32_768f
    private const val INT24_SCALE = 8_388_608f
    private const val INT_SCALE = 2_147_483_648f
}
