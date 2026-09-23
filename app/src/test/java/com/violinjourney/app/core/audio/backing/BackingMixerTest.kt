package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The violin and the backing into one stereo stream (spec 5.25). */
class BackingMixerTest {
    private val rate = 1_000 // one sample a millisecond: shifts read as sample counts
    private val sound = SoundConfig()

    /** A backing whose left side is its own frame index /10 000 and whose right side is minus that: a sample says where it was read. */
    private val ramp = BackingSource { position, count, gain, left, right ->
        for (i in 0 until count) {
            val frame = position + i
            val value = if (frame in 0 until 100_000) frame / 100_000f else 0f
            left[i] = value * gain
            right[i] = -value * gain
        }
    }

    private fun mixer(offsetMs: Int = 0, gainDb: Float = 0f, heard: Boolean = true, fade: Int = 0, source: BackingSource = ramp) =
        BackingMixer(rate, source, offsetMs, gainDb, heard, fadeSamples = fade, soundConfig = sound)

    /** The mix of [count] silent violin samples from [position], with the limiter's delay taken out. */
    private fun run(mixer: BackingMixer, position: Long, count: Int): FloatArray {
        val delay = mixer.latencySamples
        val violin = FloatArray(count + delay)
        val out = FloatArray((count + delay) * 2)
        mixer.mix(violin, count + delay, position, out)
        return out.copyOfRange(delay * 2, out.size)
    }

    @Test
    fun `a positive shift has the backing start later than the violin`() {
        val out = run(mixer(offsetMs = 200), position = 0, count = 400)
        assertEquals(0f, out[2 * 199], 0f) // the backing has not begun
        assertEquals(0f, out[2 * 200], 1e-6f) // its first frame
        assertEquals(50 / 100_000f, out[2 * 250], 1e-6f)
        assertEquals(-50 / 100_000f, out[2 * 250 + 1], 1e-6f) // the right side is the right side
    }

    @Test
    fun `a negative shift starts the backing already under way`() {
        val out = run(mixer(offsetMs = -100), position = 0, count = 10)
        assertEquals(100 / 100_000f, out[0], 1e-6f)
    }

    @Test
    fun `the violin sits in the middle and the level scales only the backing`() {
        val m = mixer(gainDb = -6f)
        val delay = m.latencySamples
        val violin = FloatArray(10 + delay) { 0.25f }
        val out = FloatArray((10 + delay) * 2)
        m.mix(violin, 10 + delay, 1_000, out)
        val gain = 10.0.pow(-6.0 / 20).toFloat()
        // what comes out after the limiter's delay is what went in at the first sample
        val backing = 1_000 / 100_000f * gain
        assertEquals(0.25f + backing, out[2 * delay], 1e-5f)
        assertEquals(0.25f - backing, out[2 * delay + 1], 1e-5f)
    }

    @Test
    fun `only the violin is heard when the backing is switched off`() {
        val out = run(mixer(heard = false), position = 5_000, count = 50)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun `a new shift glides in without a jump`() {
        val constant = BackingSource { _, count, gain, left, right -> left.fill(0.4f * gain, 0, count); right.fill(0.4f * gain, 0, count) }
        // from a steady level to silence: a fade takes 50 samples, not one
        val m = mixer(offsetMs = 0, fade = 50, source = constant)
        run(m, 0, 20)
        m.set(offsetMs = 0, gainDb = 0f, heard = false)
        val out = run(m, 20, 60)
        val steps = (1 until 50).map { abs(out[2 * it] - out[2 * (it - 1)]) }
        assertTrue("no step bigger than a fiftieth of the level", steps.all { it <= 0.4f / 50 + 1e-4f })
        assertEquals(0f, out[2 * 55], 1e-6f)
    }

    @Test
    fun `nothing leaves the mix above the ceiling, whatever the two sides add up to`() {
        val m = mixer(gainDb = 6f, source = BackingSource { _, count, gain, left, right -> left.fill(0.9f * gain, 0, count); right.fill(0.9f * gain, 0, count) })
        val violin = FloatArray(2_000) { 0.9f }
        val out = FloatArray(4_000)
        m.mix(violin, 2_000, 0, out)
        val ceiling = 10.0.pow(sound.limiterCeilingDb / 20).toFloat()
        assertTrue(out.all { abs(it) <= ceiling + 1e-4f })
    }
}
