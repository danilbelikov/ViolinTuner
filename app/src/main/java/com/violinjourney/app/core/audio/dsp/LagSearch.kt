package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Lag bounds, in samples, that cover the frequency range of interest. */
internal class LagRange(config: IntonationConfig, sampleRateHz: Int) {
    val min: Int = maxOf(MIN_LAG, floor(sampleRateHz / config.maxFrequencyHz).toInt() - 1)
    val max: Int = ceil(sampleRateHz / config.minFrequencyHz).toInt() + 1

    /** Highest lag a lag function must be computed for so that [max] can still be refined. */
    val top: Int = max + LagSearch.SUPPORT + 1

    private companion object {
        const val MIN_LAG = 2
    }
}

/**
 * Sub-sample refinement (spec 5.1). Lag functions are band-limited like the signal itself, so
 * windowed-sinc interpolation recovers them between integer lags far better than a parabola,
 * which is off by several cents where a period is only ~17 samples (top of the violin range).
 */
internal object LagSearch {
    /** Lanczos kernel half-width in samples. */
    const val SUPPORT = 6
    private const val REFINE_ITERATIONS = 30
    private val GOLDEN = (sqrt(5.0) - 1) / 2
    private const val EPSILON = 1e-12

    /** Lanczos-interpolated value of [values] (first [size] entries valid) at fractional [t]. */
    fun interpolate(values: DoubleArray, size: Int, t: Double): Double {
        val base = floor(t).toInt()
        var sum = 0.0
        var weights = 0.0
        for (k in maxOf(0, base - SUPPORT + 1)..minOf(size - 1, base + SUPPORT)) {
            val w = lanczos(t - k)
            sum += w * values[k]
            weights += w
        }
        return if (abs(weights) < EPSILON) values[base.coerceIn(0, size - 1)] else sum / weights
    }

    /**
     * Position of the local extremum within one sample of [index]: a minimum for [sign] = 1,
     * a maximum for [sign] = -1. Golden-section search over the interpolant.
     */
    fun refine(values: DoubleArray, size: Int, index: Int, sign: Int): Double {
        var lo = index - 1.0
        var hi = index + 1.0
        var a = hi - GOLDEN * (hi - lo)
        var b = lo + GOLDEN * (hi - lo)
        var fa = sign * interpolate(values, size, a)
        var fb = sign * interpolate(values, size, b)
        repeat(REFINE_ITERATIONS) {
            if (fa < fb) {
                hi = b
                b = a
                fb = fa
                a = hi - GOLDEN * (hi - lo)
                fa = sign * interpolate(values, size, a)
            } else {
                lo = a
                a = b
                fa = fb
                b = lo + GOLDEN * (hi - lo)
                fb = sign * interpolate(values, size, b)
            }
        }
        return (lo + hi) / 2
    }

    private fun lanczos(x: Double): Double {
        if (abs(x) < EPSILON) return 1.0
        if (abs(x) >= SUPPORT) return 0.0
        val px = PI * x
        return SUPPORT * sin(px) * sin(px / SUPPORT) / (px * px)
    }
}

/** Copies [window] into [target] with the mean (DC offset) removed. */
internal fun removeDc(window: FloatArray, target: DoubleArray) {
    var mean = 0.0
    for (sample in window) mean += sample
    mean /= window.size
    for (i in window.indices) target[i] = window[i] - mean
}
