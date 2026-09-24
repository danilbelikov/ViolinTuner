package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.audio.fx.FxSignals.RATE
import com.violinjourney.app.core.domain.sound.Bell
import com.violinjourney.app.core.domain.sound.LowCut
import com.violinjourney.app.core.domain.sound.Shelf
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class EqualizerTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config).eq

    /** Level of a sine after the equalizer against before it, measured once the filters have settled. */
    private fun measuredDb(eq: com.violinjourney.app.core.domain.sound.EqSettings, hz: Double): Double {
        val equalizer = Equalizer(RATE, config, rampSamples = 1)
        equalizer.set(eq, immediate = true)
        val input = FxSignals.sine(hz, peak = 0.25, seconds = 1.0)
        val output = FloatArray(input.size) { equalizer.process(input[it].toDouble()).toFloat() }
        val from = input.size / 2
        return FxSignals.db(FxSignals.rms(output, from) / FxSignals.rms(input, from))
    }

    @Test
    fun `a switched-off equalizer and bands at zero draw a flat line`() {
        val frequencies = EqCurve.logFrequencies(64)
        assertTrue(EqCurve.responseDb(off, RATE, frequencies, config).all { it == 0.0 })
        assertTrue(EqCurve.responseDb(off.copy(enabled = true), RATE, frequencies, config).all { it == 0.0 })
        assertTrue(EqCurve.responseDb(off.copy(low = Shelf(200.0, 9.0)), RATE, frequencies, config).all { it == 0.0 }, "gains of a switched-off equalizer are not drawn")
    }

    @Test
    fun `the curve runs from 20 Hz to 20 kHz evenly in octaves`() {
        val frequencies = EqCurve.logFrequencies(4)
        assertEquals(20.0, frequencies.first(), 1e-9)
        assertEquals(200.0, frequencies[1], 1e-6)
        assertEquals(2_000.0, frequencies[2], 1e-5)
        assertEquals(20_000.0, frequencies.last(), 1e-6)
    }

    @Test
    fun `a bell lifts its own frequency by its gain and leaves far ones alone`() {
        val eq = off.copy(enabled = true, presence = Bell(3_200.0, 6.0, 1.0))
        val response = EqCurve.responseDb(eq, RATE, doubleArrayOf(100.0, 3_200.0, 16_000.0), config)
        assertEquals(0.0, response[0], 0.1)
        assertEquals(6.0, response[1], 0.01)
        assertEquals(0.0, response[2], 0.6)
    }

    @Test
    fun `a narrower bell reaches less far`() {
        val wide = EqCurve.responseDb(off.copy(enabled = true, body = Bell(800.0, 9.0, 0.4)), RATE, doubleArrayOf(1_600.0), config)[0]
        val narrow = EqCurve.responseDb(off.copy(enabled = true, body = Bell(800.0, 9.0, 4.0)), RATE, doubleArrayOf(1_600.0), config)[0]
        assertTrue(wide > narrow + 3, "$wide vs $narrow")
    }

    @Test
    fun `shelves move everything beyond their corner — the low cut takes the rumble away`() {
        val low = EqCurve.responseDb(off.copy(enabled = true, low = Shelf(200.0, 6.0)), RATE, doubleArrayOf(30.0, 200.0, 5_000.0), config)
        assertEquals(6.0, low[0], 0.3)
        assertEquals(3.0, low[1], 0.3)
        assertEquals(0.0, low[2], 0.1)
        val air = EqCurve.responseDb(off.copy(enabled = true, air = Shelf(8_000.0, -4.0)), RATE, doubleArrayOf(500.0, 19_000.0), config)
        assertEquals(0.0, air[0], 0.1)
        assertEquals(-4.0, air[1], 0.6)
        val cut = EqCurve.responseDb(off.copy(enabled = true, lowCut = LowCut(true, 80.0)), RATE, doubleArrayOf(40.0, 80.0, 196.0), config)
        assertEquals(-12.3, cut[0], 0.5) // 12 dB per octave
        assertEquals(-3.0, cut[1], 0.1)
        assertTrue(cut[2] > -0.3, "the open G string is left alone: ${cut[2]}")
    }

    @Test
    fun `the curve on the screen is what the sound goes through`() {
        val eq = off.copy(
            enabled = true, lowCut = LowCut(true, 120.0), low = Shelf(250.0, 4.0),
            body = Bell(900.0, -5.0, 1.5), presence = Bell(3_000.0, 7.0, 2.0), air = Shelf(9_000.0, -3.0),
        )
        for (hz in doubleArrayOf(60.0, 250.0, 900.0, 3_000.0, 12_000.0)) {
            val drawn = EqCurve.responseDb(eq, RATE, doubleArrayOf(hz), config)[0]
            assertEquals(drawn, measuredDb(eq, hz), 0.1, "at $hz Hz")
        }
    }

    @Test
    fun `a band dragged while the sound plays does not click`() {
        val ramp = (config.smoothingMs / 1_000 * RATE).toInt()
        val equalizer = Equalizer(RATE, config, ramp)
        equalizer.set(off.copy(enabled = true), immediate = true)
        val input = FxSignals.sine(800.0, peak = 0.2, seconds = 0.4)
        val output = FloatArray(input.size)
        val change = input.size / 2
        for (index in input.indices) {
            if (index == change) equalizer.set(off.copy(enabled = true, body = Bell(800.0, 12.0, 1.0)), immediate = false)
            output[index] = equalizer.process(input[index].toDouble()).toFloat()
        }
        assertEquals(12.0, FxSignals.db(FxSignals.peak(output, output.size - 2_000) / 0.2), 0.2)
        // A sine of this pitch at its final loudness never steps further than this between two samples.
        val honestStep = 0.2 * 3.98 * 2 * PI * 800 / RATE
        assertTrue(FxSignals.largestStep(output, change - 10, output.size) <= honestStep * 1.05)
    }
}
