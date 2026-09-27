package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.dsp.PitchDetector
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.math.sqrt

/**
 * Turns a stream of PCM16 hops into [PitchFrame]s: keeps the last
 * [IntonationConfig.windowSizeSamples] samples, measures RMS on every hop and runs the detector on
 * every hop loud enough to be heard. Frame time is the sample clock at the end of the window, so it
 * is exact and monotonic regardless of scheduling. Pure Kotlin; not thread-safe.
 */
class FrameAnalyzer(
    private val detector: PitchDetector,
    private val config: IntonationConfig,
    private val sampleRateHz: Int,
) {
    private val window = FloatArray(config.windowSizeSamples)
    private var samplesSeen = 0L

    /** Feeds the first [count] samples of [hop]; returns a frame once the window has filled. */
    fun push(hop: ShortArray, count: Int = hop.size): PitchFrame? {
        require(count in 0..minOf(hop.size, window.size)) { "bad hop length $count" }
        if (count == 0) return null
        window.copyInto(window, destinationOffset = 0, startIndex = count)
        val offset = window.size - count
        for (i in 0 until count) window[offset + i] = hop[i] / PCM16_FULL_SCALE
        samplesSeen += count
        if (samplesSeen < window.size) return null

        val tMs = samplesSeen * MS_PER_SECOND / sampleRateHz
        val rms = rms()
        // The engine reads nothing but the RMS of a frame quieter than the silence threshold (spec 5.1): the
        // costliest step of the chain sleeps through the pauses and the silence of an open Live.
        if (rms < config.silenceRms) return PitchFrame.unpitched(tMs, clarity = 0.0, rms = rms)
        val estimate = detector.detect(window, sampleRateHz)
        val freq = estimate.freqHz
        return if (freq == null) {
            PitchFrame.unpitched(tMs, clarity = estimate.clarity, rms = rms)
        } else {
            PitchFrame.pitched(tMs, freq, clarity = estimate.clarity, rms = rms, a4Hz = config.a4Hz)
        }
    }

    private fun rms(): Double {
        var sum = 0.0
        for (sample in window) sum += sample * sample
        return sqrt(sum / window.size)
    }

    private companion object {
        const val PCM16_FULL_SCALE = 32_768f
        const val MS_PER_SECOND = 1_000L
    }
}
