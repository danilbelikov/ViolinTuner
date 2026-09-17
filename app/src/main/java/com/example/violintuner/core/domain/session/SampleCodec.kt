package com.example.violintuner.core.domain.session

import kotlin.math.roundToInt

/**
 * Samples as a compact blob: 3 bytes per bucket, an hour of playing is about 220 KB.
 * Byte 0 is the MIDI note (0xFF = no note), bytes 1-2 are the deviation in hundredths of a
 * cent, big-endian signed, clamped to what 16 bits hold (±327 cents, far beyond any reading).
 */
object SampleCodec {
    private const val BYTES_PER_SAMPLE = 3
    private const val NO_NOTE = 0xFF
    private const val UNITS_PER_CENT = 100.0
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8

    fun encode(samples: List<SessionSample?>): ByteArray {
        val bytes = ByteArray(samples.size * BYTES_PER_SAMPLE)
        samples.forEachIndexed { index, sample ->
            val offset = index * BYTES_PER_SAMPLE
            if (sample == null) {
                bytes[offset] = NO_NOTE.toByte()
            } else {
                require(sample.midi in 0 until NO_NOTE) { "midi ${sample.midi} does not fit a byte" }
                val units = (sample.cents * UNITS_PER_CENT).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                bytes[offset] = sample.midi.toByte()
                bytes[offset + 1] = (units shr BYTE_BITS).toByte()
                bytes[offset + 2] = units.toByte()
            }
        }
        return bytes
    }

    fun decode(bytes: ByteArray): List<SessionSample?> {
        require(bytes.size % BYTES_PER_SAMPLE == 0) { "blob of ${bytes.size} bytes is not whole samples" }
        return List(bytes.size / BYTES_PER_SAMPLE) { index ->
            val offset = index * BYTES_PER_SAMPLE
            val midi = bytes[offset].toInt() and BYTE_MASK
            if (midi == NO_NOTE) {
                null
            } else {
                val units = (bytes[offset + 1].toInt() shl BYTE_BITS) or (bytes[offset + 2].toInt() and BYTE_MASK)
                SessionSample(midi, units.toShort() / UNITS_PER_CENT)
            }
        }
    }
}
