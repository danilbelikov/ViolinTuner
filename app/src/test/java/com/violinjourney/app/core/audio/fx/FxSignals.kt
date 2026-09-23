package com.violinjourney.app.core.audio.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Synthetic sound for the tests of the effects. */
internal object FxSignals {
    const val RATE = 48_000

    fun sine(hz: Double, peak: Double, seconds: Double, rate: Int = RATE): FloatArray =
        FloatArray((seconds * rate).toInt()) { (peak * sin(2 * PI * hz * it / rate)).toFloat() }

    fun noise(peak: Double, seconds: Double, seed: Int = 7, rate: Int = RATE): FloatArray {
        val random = Random(seed)
        return FloatArray((seconds * rate).toInt()) { ((random.nextDouble() * 2 - 1) * peak).toFloat() }
    }

    fun impulse(seconds: Double, rate: Int = RATE): FloatArray = FloatArray((seconds * rate).toInt()).also { it[0] = 1f }

    fun db(linear: Double): Double = 20 * log10(linear.coerceAtLeast(1e-12))

    fun peak(samples: FloatArray, from: Int = 0, to: Int = samples.size): Double {
        var peak = 0.0
        for (index in from until to) peak = maxOf(peak, abs(samples[index].toDouble()))
        return peak
    }

    fun rms(samples: FloatArray, from: Int = 0, to: Int = samples.size): Double {
        var sum = 0.0
        for (index in from until to) sum += samples[index].toDouble() * samples[index]
        return sqrt(sum / (to - from).coerceAtLeast(1))
    }

    /** The largest jump between two neighbouring samples: what a click is made of. */
    fun largestStep(samples: FloatArray, from: Int, to: Int): Double {
        var step = 0.0
        for (index in from + 1 until to) step = maxOf(step, abs(samples[index].toDouble() - samples[index - 1]))
        return step
    }
}
