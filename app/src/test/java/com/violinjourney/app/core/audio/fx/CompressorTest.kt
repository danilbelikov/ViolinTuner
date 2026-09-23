package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.audio.fx.FxSignals.RATE
import com.violinjourney.app.core.domain.sound.CompressorSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressorTest {
    private val config = SoundConfig()
    private val settings = CompressorSettings(enabled = true, thresholdDb = -18.0, ratio = 3.0, attackMs = 5.0, releaseMs = 400.0, makeupDb = 0.0, amount = null)

    private fun run(settings: CompressorSettings, input: FloatArray): Pair<FloatArray, Compressor> {
        val compressor = Compressor(RATE, config)
        compressor.set(settings, immediate = true)
        return FloatArray(input.size) { compressor.process(input[it].toDouble()).toFloat() } to compressor
    }

    @Test
    fun `what lies under the threshold passes as it is`() {
        val input = FxSignals.sine(440.0, peak = 0.02, seconds = 0.5) // −34 dB
        val (output, compressor) = run(settings, input)
        assertEquals(0.0, FxSignals.db(FxSignals.peak(output, RATE / 4) / 0.02), 0.05)
        assertEquals(0.0, compressor.reductionDb, 0.05)
    }

    @Test
    fun `what lies over it is turned down by the ratio`() {
        val input = FxSignals.sine(440.0, peak = 0.5, seconds = 1.0) // −6 dB: 12 over the threshold
        val (output, compressor) = run(settings, input)
        // 12 dB over at 3:1 leave 4 dB over: −14 dB, that is 8 dB of reduction. A peak detector on a
        // sine breathes a little between the crests, hence the tolerance.
        assertEquals(-14.0, FxSignals.db(FxSignals.peak(output, RATE / 2)), 1.0)
        assertEquals(8.0, compressor.reductionDb, 1.0)
    }

    @Test
    fun `a higher ratio squeezes harder, and one to one does not squeeze`() {
        val input = FxSignals.sine(440.0, peak = 0.5, seconds = 1.0)
        val gentle = FxSignals.peak(run(settings.copy(ratio = 2.0), input).first, RATE / 2)
        val hard = FxSignals.peak(run(settings.copy(ratio = 10.0), input).first, RATE / 2)
        assertTrue(hard < gentle)
        assertEquals(0.5, FxSignals.peak(run(settings.copy(ratio = 1.0), input).first, RATE / 2), 0.005)
    }

    @Test
    fun `the make-up gain lifts everything, quiet places included`() {
        val input = FxSignals.sine(440.0, peak = 0.02, seconds = 0.5)
        val (output, _) = run(settings.copy(makeupDb = 6.0), input)
        assertEquals(6.0, FxSignals.db(FxSignals.peak(output, RATE / 4) / 0.02), 0.1)
    }

    @Test
    fun `a slow attack lets the first moment of a loud note through`() {
        val input = FxSignals.sine(440.0, peak = 0.5, seconds = 0.3)
        val early = RATE / 100 // the first 10 ms
        val fast = FxSignals.peak(run(settings.copy(attackMs = 1.0), input).first, 0, early)
        val slow = FxSignals.peak(run(settings.copy(attackMs = 100.0), input).first, 0, early)
        assertTrue("$slow vs $fast", slow > fast * 1.2)
    }

    @Test
    fun `switched off it lets go of the sound smoothly and then does nothing`() {
        val compressor = Compressor(RATE, config)
        compressor.set(settings.copy(makeupDb = 6.0), immediate = true)
        val input = FxSignals.sine(440.0, peak = 0.5, seconds = 0.6)
        val output = FloatArray(input.size)
        val change = input.size / 2
        for (index in input.indices) {
            if (index == change) compressor.set(settings.copy(makeupDb = 6.0, enabled = false), immediate = false)
            output[index] = compressor.process(input[index].toDouble()).toFloat()
        }
        assertEquals(0.5, FxSignals.peak(output, output.size - 2_000), 0.005)
        assertTrue(compressor.idle)
        val honestStep = 0.5 * 2 * Math.PI * 440 / RATE
        assertTrue(FxSignals.largestStep(output, change - 10, output.size) <= honestStep * 1.1)
    }
}
