package com.example.violintuner.core.audio.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A second-order filter, normalised so that a0 = 1. The formulas are the ones of the RBJ
 * "Audio EQ Cookbook" — the same the handoff draws its curve with, which is what makes the curve
 * on the screen the truth and not a picture (spec 3.17).
 */
data class BiquadCoefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** |H(e^jω)| at [hz], in decibels. */
    fun magnitudeDb(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        val cos1 = cos(w)
        val cos2 = cos(2 * w)
        val sin1 = sin(w)
        val sin2 = sin(2 * w)
        val numerator = (b0 + b1 * cos1 + b2 * cos2).pow(2) + (b1 * sin1 + b2 * sin2).pow(2)
        val denominator = (1 + a1 * cos1 + a2 * cos2).pow(2) + (a1 * sin1 + a2 * sin2).pow(2)
        return 10 * log10((numerator / denominator).coerceAtLeast(MIN_POWER))
    }

    companion object {
        /** Lets the sound through untouched: a band that is off. */
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

        /** Filters lose their shape next to half the sample rate; nothing of a violin lives there anyway. */
        private const val MAX_FREQUENCY_SHARE = 0.45
        private const val MIN_POWER = 1e-12

        fun highPass(hz: Double, q: Double, sampleRate: Int): BiquadCoefficients {
            val (cosW, alpha) = prepare(hz, q, sampleRate)
            val a0 = 1 + alpha
            return BiquadCoefficients((1 + cosW) / 2 / a0, -(1 + cosW) / a0, (1 + cosW) / 2 / a0, -2 * cosW / a0, (1 - alpha) / a0)
        }

        fun bell(hz: Double, gainDb: Double, q: Double, sampleRate: Int): BiquadCoefficients {
            if (gainDb == 0.0) return IDENTITY
            val (cosW, alpha) = prepare(hz, q, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val a0 = 1 + alpha / a
            return BiquadCoefficients((1 + alpha * a) / a0, -2 * cosW / a0, (1 - alpha * a) / a0, -2 * cosW / a0, (1 - alpha / a) / a0)
        }

        fun lowShelf(hz: Double, gainDb: Double, q: Double, sampleRate: Int): BiquadCoefficients {
            if (gainDb == 0.0) return IDENTITY
            val (cosW, alpha) = prepare(hz, q, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val root = 2 * sqrt(a) * alpha
            val a0 = (a + 1) + (a - 1) * cosW + root
            return BiquadCoefficients(
                b0 = a * ((a + 1) - (a - 1) * cosW + root) / a0,
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0,
                b2 = a * ((a + 1) - (a - 1) * cosW - root) / a0,
                a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0,
                a2 = ((a + 1) + (a - 1) * cosW - root) / a0,
            )
        }

        fun highShelf(hz: Double, gainDb: Double, q: Double, sampleRate: Int): BiquadCoefficients {
            if (gainDb == 0.0) return IDENTITY
            val (cosW, alpha) = prepare(hz, q, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val root = 2 * sqrt(a) * alpha
            val a0 = (a + 1) - (a - 1) * cosW + root
            return BiquadCoefficients(
                b0 = a * ((a + 1) + (a - 1) * cosW + root) / a0,
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW) / a0,
                b2 = a * ((a + 1) + (a - 1) * cosW - root) / a0,
                a1 = 2 * ((a - 1) - (a + 1) * cosW) / a0,
                a2 = ((a + 1) - (a - 1) * cosW - root) / a0,
            )
        }

        /** cos ω₀ and α of the cookbook. */
        private fun prepare(hz: Double, q: Double, sampleRate: Int): Pair<Double, Double> {
            val w = 2 * PI * hz.coerceIn(1.0, sampleRate * MAX_FREQUENCY_SHARE) / sampleRate
            return cos(w) to sin(w) / (2 * q)
        }
    }
}

/**
 * One filter in the stream. New coefficients are not switched to but glided to, sample by
 * sample, over [rampSamples]: a band dragged while the sound plays must not click.
 * Direct form I — the one that forgives coefficients changing under it.
 */
internal class BiquadSection(private val rampSamples: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var target = BiquadCoefficients.IDENTITY
    private var rampLeft = 0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun set(coefficients: BiquadCoefficients, immediate: Boolean) {
        target = coefficients
        if (immediate) {
            rampLeft = 0
            snap()
        } else {
            rampLeft = rampSamples
        }
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    val idle: Boolean get() = rampLeft == 0 && target == BiquadCoefficients.IDENTITY

    fun process(sample: Double): Double {
        if (rampLeft > 0) {
            val step = 1.0 / rampLeft
            b0 += (target.b0 - b0) * step
            b1 += (target.b1 - b1) * step
            b2 += (target.b2 - b2) * step
            a1 += (target.a1 - a1) * step
            a2 += (target.a2 - a2) * step
            if (--rampLeft == 0) snap()
        }
        val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = if (out > -DENORMAL && out < DENORMAL) 0.0 else out
        return y1
    }

    private fun snap() {
        b0 = target.b0
        b1 = target.b1
        b2 = target.b2
        a1 = target.a1
        a2 = target.a2
    }

    private companion object {
        /** A filter ringing out into silence must not crawl through denormal numbers: they are slow. */
        const val DENORMAL = 1e-30
    }
}
