package com.example.violintuner.core.audio.dsp

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour every detector has to show on synthetic signals (spec 5.1, step 4 of section 8). */
abstract class PitchDetectorContractTest {
    protected val config = IntonationConfig()
    private val window = config.windowSizeSamples

    protected abstract fun newDetector(): PitchDetector

    private fun errorCents(estimate: PitchEstimate, expectedHz: Double): Double {
        val freq = estimate.freqHz
        assertNotNull("no pitch for $expectedHz Hz", freq)
        return PitchMath.centsBetween(freq!!, expectedHz)
    }

    @Test
    fun `meets the accuracy targets over the whole range`() {
        val detector = newDetector()
        val spectra = listOf(SignalSynth.SINE, SignalSynth.VIOLIN, SignalSynth.SAW)
        for (rate in RATES) for (partials in spectra) for (midi in 55..100) for (cents in OFFSETS) {
            val hz = SignalSynth.hz(midi, cents)
            val estimate = detector.detect(SignalSynth.tone(hz, rate, window, partials), rate)
            val limit = if (midi <= E6) 1.5 else 3.0
            val error = abs(errorCents(estimate, hz))
            assertTrue("midi $midi ${cents}c @ $rate: off by $error cents", error <= limit)
            assertTrue("midi $midi @ $rate: clarity ${estimate.clarity}", estimate.clarity >= config.clarityThreshold)
        }
    }

    @Test
    fun `G string octave traps resolve to the fundamental`() {
        val detector = newDetector()
        val traps = listOf(
            SignalSynth.WEAK_FUNDAMENTAL,
            SignalSynth.MISSING_FUNDAMENTAL,
            SignalSynth.STRONG_EVEN_HARMONICS,
        )
        for (rate in RATES) for (partials in traps) for (cents in listOf(-60.0, 0.0, 25.0)) {
            val hz = SignalSynth.hz(G3, cents)
            val estimate = detector.detect(SignalSynth.tone(hz, rate, window, partials), rate)
            assertEquals(0.0, errorCents(estimate, hz), 2.0)
        }
    }

    @Test
    fun `notes with an in-range sub-octave are not read an octave down`() {
        val detector = newDetector()
        for (rate in RATES) for (midi in listOf(62, 69, 76, 88, 95, 100)) for (partials in listOf(SignalSynth.SINE, SignalSynth.SAW)) {
            val hz = SignalSynth.hz(midi)
            val estimate = detector.detect(SignalSynth.tone(hz, rate, window, partials), rate)
            assertEquals("midi $midi @ $rate", 0.0, errorCents(estimate, hz), 3.0)
        }
    }

    @Test
    fun `vibrato is followed frame by frame`() {
        val detector = newDetector()
        val rate = 44_100
        val depth = 20.0
        val vibrato = { t: Double -> depth * sin(2 * PI * 6.0 * t) }
        val center = SignalSynth.hz(A4)
        val signal = SignalSynth.tone(center, rate, rate, SignalSynth.VIOLIN, centsAt = vibrato)
        val deviations = mutableListOf<Double>()
        var start = 0
        while (start + window <= signal.size) {
            val estimate = detector.detect(signal.copyOfRange(start, start + window), rate)
            val measured = errorCents(estimate, center)
            val expected = vibrato((start + window / 2.0) / rate)
            assertEquals("frame at $start", expected, measured, 5.0)
            deviations += measured
            start += config.hopSizeSamples
        }
        assertTrue("vibrato is visible", deviations.max() > 12 && deviations.min() < -12)
    }

    @Test
    fun `white noise is not a confident pitch`() {
        val detector = newDetector()
        for (rate in RATES) for (seed in 1..10) {
            val estimate = detector.detect(SignalSynth.whiteNoise(window, seed), rate)
            val confident = estimate.freqHz != null && estimate.clarity >= config.clarityThreshold
            assertFalse("seed $seed @ $rate: $estimate", confident)
        }
    }

    @Test
    fun `digital silence gives no pitch and zero clarity`() {
        val estimate = newDetector().detect(FloatArray(window), 48_000)
        assertNull(estimate.freqHz)
        assertEquals(0.0, estimate.clarity, 0.0)
    }

    @Test
    fun `DC offset does not matter`() {
        val detector = newDetector()
        val hz = SignalSynth.hz(D4)
        val shifted = SignalSynth.tone(hz, 48_000, window, SignalSynth.VIOLIN).map { it * 0.5f + 0.4f }
        assertEquals(0.0, errorCents(detector.detect(shifted.toFloatArray(), 48_000), hz), 1.5)
    }

    @Test
    fun `moderate noise barely moves the estimate`() {
        val detector = newDetector()
        for (midi in listOf(G3, D4, A4, 76, 88)) for (seed in 1..3) {
            val hz = SignalSynth.hz(midi, 7.0)
            val clean = SignalSynth.tone(hz, 44_100, window, SignalSynth.VIOLIN)
            val estimate = detector.detect(SignalSynth.withNoise(clean, snrDb = 20.0, seed = seed), 44_100)
            assertEquals("midi $midi seed $seed", 0.0, errorCents(estimate, hz), 3.0)
            assertTrue(estimate.clarity >= config.clarityThreshold)
        }
    }

    @Test
    fun `heavy noise is never confidently wrong`() {
        val detector = newDetector()
        for (midi in listOf(G3, A4, 88)) for (seed in 1..10) {
            val hz = SignalSynth.hz(midi)
            val clean = SignalSynth.tone(hz, 44_100, window, SignalSynth.VIOLIN)
            val estimate = detector.detect(SignalSynth.withNoise(clean, snrDb = 0.0, seed = seed), 44_100)
            val freq = estimate.freqHz
            if (freq != null && estimate.clarity >= config.clarityThreshold) {
                assertEquals("midi $midi seed $seed", 0.0, PitchMath.centsBetween(freq, hz), 50.0)
            }
        }
    }

    @Test
    fun `one instance handles both sample rates in turn`() {
        val detector = newDetector()
        val hz = SignalSynth.hz(A4)
        for (rate in listOf(44_100, 48_000, 44_100)) {
            assertEquals(0.0, errorCents(detector.detect(SignalSynth.tone(hz, rate, window), rate), hz), 1.5)
        }
    }

    protected companion object {
        val RATES = listOf(44_100, 48_000)
        val OFFSETS = listOf(-30.0, 0.0, 17.0)
        const val G3 = 55
        const val D4 = 62
        const val A4 = 69
        const val E6 = 88
    }
}

class YinDetectorTest : PitchDetectorContractTest() {
    override fun newDetector(): PitchDetector = YinDetector(config)
}

class MpmDetectorTest : PitchDetectorContractTest() {
    override fun newDetector(): PitchDetector = MpmDetector(config)
}
