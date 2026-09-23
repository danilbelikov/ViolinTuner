package com.violinjourney.app.core.audio.playback

import kotlin.math.sqrt

/**
 * The static waveform of the mini player (spec 3.17): [bars] columns, each the RMS of its share
 * of the recording, scaled so that the loudest is 1. It is there for a reason — to see where
 * the loud places are, or a compressor cannot be told from the music. Fed in pieces, as the
 * decoder hands them out; nothing of the sound is kept.
 */
class WaveformBuilder(totalSamples: Long, private val bars: Int) {
    private val perBar = (totalSamples.coerceAtLeast(1).toDouble() / bars).coerceAtLeast(1.0)
    private val sums = DoubleArray(bars)
    private val counts = LongArray(bars)
    private var position = 0L

    fun add(samples: ShortArray, count: Int) {
        for (i in 0 until count) {
            // a file a little longer than its header promised still ends in the last bar
            val bar = (position / perBar).toInt().coerceAtMost(bars - 1)
            val value = samples[i] / FULL_SCALE
            sums[bar] += value * value
            counts[bar]++
            position++
        }
    }

    fun build(): FloatArray {
        val rms = FloatArray(bars) { if (counts[it] == 0L) 0f else sqrt(sums[it] / counts[it]).toFloat() }
        val loudest = rms.max()
        return if (loudest <= 0f) rms else FloatArray(bars) { rms[it] / loudest }
    }

    companion object {
        private const val FULL_SCALE = 32_768.0

        /** One byte a bar is plenty for columns a few dp tall. */
        fun encode(waveform: FloatArray): ByteArray = ByteArray(waveform.size) { (waveform[it].coerceIn(0f, 1f) * BYTE_MAX).toInt().toByte() }

        fun decode(bytes: ByteArray): FloatArray = FloatArray(bytes.size) { (bytes[it].toInt() and BYTE_MAX) / BYTE_MAX.toFloat() }

        private const val BYTE_MAX = 255
    }
}
