package com.example.violintuner.core.audio.fx

import com.example.violintuner.core.audio.fx.FxSignals.RATE
import com.example.violintuner.core.domain.sound.SoundConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LimiterTest {
    private val config = SoundConfig()
    private val ceiling = 10.0.pow(config.limiterCeilingDb / 20)

    private fun run(limiter: Limiter, input: FloatArray) = FloatArray(input.size) { limiter.process(input[it].toDouble()).toFloat() }

    @Test
    fun `nothing gets above the ceiling, whatever comes in`() {
        for (input in listOf(FxSignals.noise(4.0, 2.0), FxSignals.sine(440.0, 3.0, 1.0), FxSignals.impulse(0.2).also { it[0] = 20f })) {
            val output = run(Limiter(RATE, config), input)
            assertTrue("peak ${FxSignals.peak(output)}", FxSignals.peak(output) <= ceiling + 1e-6)
        }
    }

    @Test
    fun `a loud burst after silence is caught too - that is what looking ahead is for`() {
        val input = FloatArray(RATE / 2).also { quiet -> for (index in quiet.size / 2 until quiet.size) quiet[index] = if (index % 2 == 0) 2.5f else -2.5f }
        assertTrue(FxSignals.peak(run(Limiter(RATE, config), input)) <= ceiling + 1e-6)
    }

    @Test
    fun `sound under the ceiling comes out as it went in, only later`() {
        val limiter = Limiter(RATE, config)
        val input = FxSignals.sine(440.0, 0.5, 0.2)
        val output = run(limiter, input)
        val delay = limiter.latencySamples
        assertEquals(239, delay) // 5 ms at 48 kHz, less the sample at hand
        for (index in delay until output.size) assertEquals(input[index - delay], output[index], 0f)
        assertFalse(limiter.takeLimiting())
    }

    @Test
    fun `it says when it has been holding the sound back, once per asking`() {
        val limiter = Limiter(RATE, config)
        run(limiter, FxSignals.sine(440.0, 2.0, 0.2))
        assertTrue(limiter.takeLimiting())
        run(limiter, FxSignals.sine(440.0, 0.1, 0.5))
        limiter.takeLimiting() // the release of the loud part
        run(limiter, FxSignals.sine(440.0, 0.1, 0.2))
        assertFalse(limiter.takeLimiting())
    }

    @Test
    fun `a peak just over the ceiling is not yet called limiting`() {
        val limiter = Limiter(RATE, config)
        run(limiter, FxSignals.sine(440.0, ceiling * 1.03, 0.2)) // a quarter of a decibel over
        assertFalse(limiter.takeLimiting())
    }

    @Test
    fun `the loud part is turned down as a whole, not shaved off at the top`() {
        val output = run(Limiter(RATE, config), FxSignals.sine(440.0, 1.8, 1.0))
        // A shaved sine would sit at the ceiling for long stretches; a turned-down one only touches it at its crests.
        val atCeiling = (RATE / 2 until output.size).count { output[it] >= ceiling * 0.999 }
        assertTrue("$atCeiling samples", atCeiling < (output.size / 2) / 20)
    }
}
