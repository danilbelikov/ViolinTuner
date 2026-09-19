package com.example.violintuner.core.audio.fx

import com.example.violintuner.core.domain.sound.SoundConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * The last thing in the chain: whatever the knobs were turned to, nothing above the ceiling gets
 * into the speaker or the file (spec 3.17). It looks ahead: the gain each sample needs is
 * min-filtered over the look-ahead window and then averaged over the same window, so the gain
 * glides down *before* a peak and is exactly low enough when the peak arrives — the ceiling
 * holds to the sample, without the crunch of a gain that jumps. The price is a delay of
 * [latencySamples].
 */
internal class Limiter(sampleRate: Int, config: SoundConfig) {
    private val ceiling = 10.0.pow(config.limiterCeilingDb / 20)
    private val window = (config.limiterLookAheadMs / MS_PER_SECOND * sampleRate).toInt().coerceAtLeast(2)
    private val release = exp(-1.0 / (config.limiterReleaseMs / MS_PER_SECOND * sampleRate))
    private val limitingBelow = 10.0.pow(-config.limitingFromDb / 20)

    val latencySamples: Int = window - 1

    private val delay = DoubleArray(window)
    private val wanted = DoubleArray(window) { 1.0 }
    private val minima = DoubleArray(window) { 1.0 }
    private var position = 0
    private var minimaSum = window.toDouble()
    private var gain = 1.0
    private var lowest = 1.0

    /** The lowest gain since this was last read: below [SoundConfig.limitingFromDb] the mark on the meter lights up. */
    private var lowestGain = 1.0

    fun reset() {
        delay.fill(0.0)
        wanted.fill(1.0)
        minima.fill(1.0)
        minimaSum = window.toDouble()
        gain = 1.0
        lowest = 1.0
        lowestGain = 1.0
    }

    /** True when the limiter held the sound back noticeably since the last call. */
    fun takeLimiting(): Boolean {
        val limiting = lowestGain < limitingBelow
        lowestGain = 1.0
        return limiting
    }

    fun process(sample: Double): Double {
        val level = abs(sample)
        val leaving = wanted[position]
        val entering = if (level > ceiling) ceiling / level else 1.0
        wanted[position] = entering

        // The minimum of the window, kept from sample to sample: an hour of sound is rendered
        // through here, a scan of the window per sample would take a minute of its own. It is
        // looked for anew only when the value that was the minimum leaves the window.
        if (entering <= lowest) {
            lowest = entering
        } else if (leaving <= lowest) {
            lowest = 1.0
            for (value in wanted) if (value < lowest) lowest = value
        }
        minimaSum += lowest - minima[position]
        minima[position] = lowest
        val averaged = minimaSum / window

        // Down at once (the average has already glided), up by the release.
        gain = if (averaged < gain) averaged else averaged + (gain - averaged) * release
        if (gain < lowestGain) lowestGain = gain

        val next = if (position + 1 == window) 0 else position + 1
        val out = delay[next] * gain
        delay[position] = sample
        position = next
        return out
    }

    companion object {
        private const val MS_PER_SECOND = 1_000.0

        fun toDb(linear: Double): Double = 20 * log10(linear.coerceAtLeast(1e-9))
    }
}
