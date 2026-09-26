package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.audio.fx.FxSignals.RATE
import com.violinjourney.app.core.domain.sound.ReverbSettings
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ReverbTest {
    private val config = SoundConfig()
    private val wetOnly = ReverbSettings(enabled = true, space = ReverbSpace.HALL, decaySec = 1.8, preDelayMs = 20.0, brightness = 1.0, mix = 0.6)

    private fun run(settings: ReverbSettings, input: FloatArray): FloatArray {
        val reverb = Reverb(RATE, config)
        reverb.set(settings, immediate = true)
        return FloatArray(input.size) { reverb.process(input[it].toDouble()).toFloat() }
    }

    /** Level of the tail around [atSec], in a window of 100 ms. */
    private fun tailDb(output: FloatArray, atSec: Double): Double {
        val centre = (atSec * RATE).toInt()
        return FxSignals.db(FxSignals.rms(output, centre - RATE / 20, centre + RATE / 20))
    }

    @Test
    fun `the tail is sixty decibels down after the length it was given`() {
        for ((space, decay) in listOf(ReverbSpace.ROOM to 0.6, ReverbSpace.HALL to 1.8, ReverbSpace.CATHEDRAL to 4.0)) {
            // RT60 is a matter of the middle of the spectrum — the top of a tail dies sooner, that is what
            // «яркость» is about. So: a short note of 500 Hz, then silence, and the fall of what rings on.
            val note = FxSignals.sine(500.0, 0.5, 0.1)
            val input = FloatArray(((decay + 0.6) * RATE).toInt()).also { note.copyInto(it) }
            val output = run(wetOnly.copy(space = space, decaySec = decay, preDelayMs = 0.0), input)
            val fall = tailDb(output, 0.3) - tailDb(output, 0.3 + decay / 2)
            // Half the length — half the way down: 30 dB. Combs of different lengths do not decay as one, hence the room.
            assertEquals(30.0, fall, 5.0, "$space")
        }
    }

    @Test
    fun `a longer tail is longer — not louder`() {
        val short = FxSignals.rms(run(wetOnly.copy(decaySec = 0.8), FxSignals.noise(0.2, 3.0)), RATE * 2)
        val long = FxSignals.rms(run(wetOnly.copy(decaySec = 3.0), FxSignals.noise(0.2, 3.0)), RATE * 2)
        assertEquals(0.0, FxSignals.db(long / short), 3.0)
    }

    @Test
    fun `nothing of the hall is heard before the pre-delay has passed`() {
        val output = run(wetOnly.copy(preDelayMs = 100.0), FxSignals.impulse(1.0))
        val dry = 1 - wetOnly.mix
        assertEquals(dry, output[0].toDouble(), 1e-6)
        assertEquals(0.0, FxSignals.peak(output, 1, (0.1 * RATE).toInt()), 0.0)
        assertTrue(FxSignals.peak(output, (0.1 * RATE).toInt(), output.size) > 0.0)
    }

    @Test
    fun `a dull tail has lost its top`() {
        val bright = run(wetOnly, FxSignals.sine(9_000.0, 0.3, 1.5))
        val dull = run(wetOnly.copy(brightness = 0.0), FxSignals.sine(9_000.0, 0.3, 1.5))
        // after the sine has long been only tail upon tail, the dull hall carries far less of it
        assertTrue(FxSignals.rms(dull, RATE) < FxSignals.rms(bright, RATE))
    }

    @Test
    fun `with no share of it the sound is the dry sound — and the block then rests`() {
        val input = FxSignals.noise(0.3, 0.5)
        val reverb = Reverb(RATE, config)
        reverb.set(wetOnly.copy(mix = 0.0), immediate = true)
        assertTrue(reverb.idle)
        val output = FloatArray(input.size) { reverb.process(input[it].toDouble()).toFloat() }
        assertTrue(input.contentEquals(output))
    }

    @Test
    fun `the longest tail in the largest space stays put`() {
        val settings = wetOnly.copy(space = ReverbSpace.CATHEDRAL, decaySec = 6.0, brightness = 1.0)
        val output = run(settings, FxSignals.noise(0.9, 12.0))
        assertTrue(output.all { it.isFinite() })
        // Once the hall is full it stays as full as it is: the level of the twelfth second is that of the seventh.
        val settled = FxSignals.rms(output, RATE * 6, RATE * 7)
        val later = FxSignals.rms(output, RATE * 11, RATE * 12)
        assertEquals(0.0, FxSignals.db(later / settled), 1.0)
    }

    @Test
    fun `another space is built anew and starts from silence`() {
        val reverb = Reverb(RATE, config)
        reverb.set(wetOnly, immediate = true)
        FxSignals.noise(0.5, 0.5).forEach { reverb.process(it.toDouble()) }
        reverb.set(wetOnly.copy(space = ReverbSpace.ROOM, decaySec = 0.6), immediate = true)
        assertEquals(0.0, reverb.process(0.0), 0.0, "no tail of the hall left in the room")
    }

    /** As [SoundChain] runs it: not at all while it rests — the sound then passes as it is. */
    private fun Reverb.play(input: FloatArray): FloatArray = FloatArray(input.size) { if (idle) input[it] else process(input[it].toDouble()).toFloat() }

    private val rampSamples = (config.smoothingMs / 1_000 * RATE).roundToInt()

    @Test
    fun `a hall switched back on starts from silence - not from the tail it had`() {
        val reverb = Reverb(RATE, config)
        reverb.set(wetOnly, immediate = true)
        reverb.play(FxSignals.noise(0.5, 0.5))
        reverb.set(wetOnly.copy(enabled = false), immediate = false)
        reverb.play(FloatArray(RATE)) // it goes quiet, and then rests with what its lines held at that moment
        assertTrue(reverb.idle)
        reverb.set(wetOnly, immediate = false)
        val after = reverb.play(FloatArray(RATE / 2))
        assertEquals(0.0, FxSignals.peak(after), 0.0, "the tail of the place where the hall went quiet does not come back")
    }

    @Test
    fun `a pre-delay turned while it plays glides in`() {
        val input = FxSignals.sine(200.0, 0.5, 1.0)
        val change = RATE / 2
        fun run(turned: Boolean): FloatArray {
            val reverb = Reverb(RATE, config)
            reverb.set(wetOnly, immediate = true)
            return FloatArray(input.size) {
                if (turned && it == change) reverb.set(wetOnly.copy(preDelayMs = 21.0), immediate = false)
                reverb.process(input[it].toDouble()).toFloat()
            }
        }
        val steady = run(turned = false)
        val turned = run(turned = true)
        // what the turn changed: a jump of the tap would come out of the lines as a step of it
        val difference = FloatArray(RATE / 5) { turned[change + it] - steady[change + it] }
        val largest = FxSignals.peak(difference)
        assertTrue(largest > 0.0)
        val step = FxSignals.largestStep(difference, 0, difference.size)
        assertTrue(step < largest / 10, "a step of $step against $largest")
    }

    @Test
    fun `another space asked for while it plays fades the old tail out without a click`() {
        val reverb = Reverb(RATE, config)
        reverb.set(wetOnly, immediate = true)
        val input = FxSignals.sine(440.0, 0.3, 1.0)
        val change = input.size / 2
        val output = FloatArray(input.size) {
            if (it == change) reverb.set(wetOnly.copy(space = ReverbSpace.ROOM, decaySec = 0.6), immediate = false)
            reverb.process(input[it].toDouble()).toFloat()
        }
        val steady = FxSignals.largestStep(output, change / 2, change)
        val around = FxSignals.largestStep(output, change - 10, output.size)
        assertTrue(around < steady * 1.5, "a step of $around against $steady before the switch")
    }

    @Test
    fun `another space starts from silence once the old tail has faded out`() {
        // the pre-delay stays within the smoothing time: what it still holds of before the switch goes into the old lines
        val hall = wetOnly.copy(preDelayMs = 20.0)
        val reverb = Reverb(RATE, config)
        reverb.set(hall, immediate = true)
        FxSignals.sine(440.0, 0.3, 0.5).forEach { reverb.process(it.toDouble()) }
        reverb.set(hall.copy(space = ReverbSpace.ROOM, decaySec = 0.6), immediate = false)
        val after = FloatArray(RATE / 2) { reverb.process(0.0).toFloat() }
        assertTrue(FxSignals.peak(after, 0, rampSamples / 2) > 0.0, "the old tail is faded out, not cut")
        assertEquals(0.0, FxSignals.peak(after, rampSamples, after.size), 0.0, "no tail of the hall left in the room")
    }
}
