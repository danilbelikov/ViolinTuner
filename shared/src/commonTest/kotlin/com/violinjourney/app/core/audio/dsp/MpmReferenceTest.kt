package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cheaper MPM (one refinement per peak, an early exit on frames far below the clarity threshold, the running
 * energy of the NSDF, three sines per Lanczos point) against the detector as it was before, kept here verbatim:
 * every window is judged the same — a confident pitch or not — and a confident one within a hundredth of a cent.
 */
class MpmReferenceTest {
    private val config = IntonationConfig()
    private val window = config.windowSizeSamples

    private fun confident(estimate: PitchEstimate) = estimate.freqHz != null && estimate.clarity >= config.clarityThreshold

    private fun check(detector: MpmDetector, reference: ReferenceMpm, signal: FloatArray, rate: Int, what: String) {
        val old = reference.detect(signal, rate)
        val new = detector.detect(signal, rate)
        assertEquals(confident(old), confident(new), "$what: before $old, now $new")
        if (confident(old)) {
            assertEquals(0.0, PitchMath.centsBetween(new.freqHz!!, old.freqHz!!), 0.01, "$what: frequency")
            assertEquals(old.clarity, new.clarity, 1e-6, "$what: clarity")
        }
    }

    @Test
    fun `clean tones over the whole range are read as before`() {
        val detector = MpmDetector(config)
        val reference = ReferenceMpm(config)
        for (rate in RATES) for ((name, partials) in SPECTRA) for (midi in (55..100 step 3) + 100) for (cents in OFFSETS) {
            val tone = SignalSynth.tone(SignalSynth.hz(midi, cents), rate, window, partials)
            check(detector, reference, tone, rate, "$name midi $midi $cents c @ $rate")
        }
    }

    @Test
    fun `noisy tones are judged as before up to the top of the range`() {
        val detector = MpmDetector(config)
        val reference = ReferenceMpm(config)
        for (rate in RATES) for ((name, partials) in SPECTRA.drop(1)) for (midi in listOf(55, 69, 88, 96, 100)) {
            for (snr in listOf(20.0, 10.0, 8.0, 7.0, 5.0, 0.0)) for (seed in 1..3) {
                val tone = SignalSynth.tone(SignalSynth.hz(midi, 9.0), rate, window, partials)
                check(detector, reference, SignalSynth.withNoise(tone, snr, seed), rate, "$name midi $midi at $snr dB seed $seed @ $rate")
            }
        }
    }

    @Test
    fun `noise and silence are judged as before`() {
        val detector = MpmDetector(config)
        val reference = ReferenceMpm(config)
        for (rate in RATES) {
            for (seed in 1..5) for (amplitude in listOf(0.3, 0.002)) {
                check(detector, reference, SignalSynth.whiteNoise(window, seed, amplitude), rate, "noise $amplitude seed $seed @ $rate")
            }
            check(detector, reference, FloatArray(window), rate, "digital zero @ $rate")
        }
    }

    @Test
    fun `a frame far below the threshold is given up with its rough clarity`() {
        val estimate = MpmDetector(config).detect(SignalSynth.whiteNoise(window, seed = 3), 48_000)
        val before = ReferenceMpm(config).detect(SignalSynth.whiteNoise(window, seed = 3), 48_000)
        assertEquals(null, estimate.freqHz)
        assertTrue(estimate.clarity > 0.0 && estimate.clarity < config.clarityThreshold, "clarity ${estimate.clarity}")
        // the height at the integer lag is at most the refined one, and not far below it
        assertTrue(estimate.clarity <= before.clarity + 1e-9 && before.clarity - estimate.clarity < 0.1, "now ${estimate.clarity}, before ${before.clarity}")
    }

    private companion object {
        val RATES = listOf(44_100, 48_000)
        val OFFSETS = listOf(-41.0, 0.0, 23.0, 50.0)

        /** A sawtooth up to the Nyquist frequency: the sharpest crest of the NSDF, the worst case of the early exit. */
        val FULL_SAW = DoubleArray(32) { 1.0 / (it + 1) }
        val SPECTRA = listOf("sine" to SignalSynth.SINE, "violin" to SignalSynth.VIOLIN, "saw" to SignalSynth.SAW, "full saw" to FULL_SAW)
    }
}

/** MpmDetector and LagSearch as they were before the early exit and the one-pass refinement: the yardstick. */
private class ReferenceMpm(private val config: IntonationConfig) {
    private var samples = DoubleArray(0)
    private var nsdf = DoubleArray(0)

    fun detect(window: FloatArray, sampleRateHz: Int): PitchEstimate {
        val lags = LagRange(config, sampleRateHz)
        val size = lags.top + 1
        if (samples.size != window.size) samples = DoubleArray(window.size)
        if (nsdf.size != size) nsdf = DoubleArray(size)
        removeDc(window, samples)
        if (!computeNsdf(size)) return PitchEstimate(freqHz = null, clarity = 0.0)

        var bestLag = 0.0
        var bestHeight = 0.0
        var highest = 0.0
        for (pass in 0..1) {
            var lag = 1
            while (lag < size && nsdf[lag] > 0) lag++
            while (lag <= lags.max) {
                if (nsdf[lag] <= 0) {
                    lag++
                    continue
                }
                var peak = lag
                while (lag <= lags.max && nsdf[lag] > 0) {
                    if (nsdf[lag] > nsdf[peak]) peak = lag
                    lag++
                }
                val isKeyMaximum = peak >= lags.min &&
                    nsdf[peak] >= nsdf[peak - 1] && nsdf[peak] >= nsdf[peak + 1]
                if (!isKeyMaximum) continue
                val refinedLag = refine(nsdf, size, peak)
                val height = interpolate(nsdf, size, refinedLag)
                if (pass == 0) {
                    if (height > highest) highest = height
                } else if (height >= config.mpmPeakRatio * highest) {
                    bestLag = refinedLag
                    bestHeight = height
                    break
                }
            }
            if (highest <= 0.0) break
        }
        if (bestLag == 0.0) return PitchEstimate(freqHz = null, clarity = 0.0)
        return PitchEstimate(freqHz = sampleRateHz / bestLag, clarity = bestHeight.coerceIn(0.0, 1.0))
    }

    private fun computeNsdf(size: Int): Boolean {
        var any = false
        for (lag in 0 until size) {
            var correlation = 0.0
            var energy = 0.0
            for (j in 0 until samples.size - lag) {
                val a = samples[j]
                val b = samples[j + lag]
                correlation += a * b
                energy += a * a + b * b
            }
            nsdf[lag] = if (energy > 0.0) 2 * correlation / energy else 0.0
            if (energy > 0.0) any = true
        }
        return any
    }
}

/** Lanczos interpolation with two sines per tap, as LagSearch had it. */
internal object ReferenceLanczos {
    private const val SUPPORT = LagSearch.SUPPORT
    private const val EPSILON = 1e-12

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

    /** LagSearch.refine over [interpolate]: a maximum for [sign] = -1. */
    fun refine(values: DoubleArray, size: Int, index: Int, sign: Int): Double {
        val golden = (sqrt(5.0) - 1) / 2
        var lo = index - 1.0
        var hi = index + 1.0
        var a = hi - golden * (hi - lo)
        var b = lo + golden * (hi - lo)
        var fa = sign * interpolate(values, size, a)
        var fb = sign * interpolate(values, size, b)
        repeat(REFINE_ITERATIONS) {
            if (fa < fb) {
                hi = b
                b = a
                fb = fa
                a = hi - golden * (hi - lo)
                fa = sign * interpolate(values, size, a)
            } else {
                lo = a
                a = b
                fa = fb
                b = lo + golden * (hi - lo)
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

    private const val REFINE_ITERATIONS = 30
}

private fun refine(values: DoubleArray, size: Int, index: Int): Double = ReferenceLanczos.refine(values, size, index, sign = -1)

private fun interpolate(values: DoubleArray, size: Int, t: Double): Double = ReferenceLanczos.interpolate(values, size, t)
