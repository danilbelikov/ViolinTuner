package com.violinjourney.app.core.audio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbMixerTest {
    private fun ramp(size: Int, from: Int = 1) = FloatArray(size) { (from + it).toFloat() }

    @Test
    fun `the original comes out late but bit for bit`() {
        val mixer = AbMixer(latencySamples = 3, fadeSamples = 4)
        val dry = ramp(8)
        mixer.passOriginal(dry, dry.size)
        assertEquals(listOf(0f, 0f, 0f, 1f, 2f, 3f, 4f, 5f), dry.toList())
        assertTrue(mixer.originalOnly)
    }

    @Test
    fun `at B the processing alone is heard`() {
        val mixer = AbMixer(latencySamples = 3, fadeSamples = 4)
        mixer.jumpTo(1f)
        val wet = FloatArray(6) { 100f }
        mixer.mix(ramp(6), wet, 6)
        assertTrue(wet.all { it == 100f })
        assertFalse(mixer.originalOnly)
    }

    @Test
    fun `a switch is a fade of the given length, against the original of the same moment`() {
        val mixer = AbMixer(latencySamples = 2, fadeSamples = 4)
        mixer.jumpTo(1f)
        mixer.mix(ramp(2), FloatArray(2), 2) // fills the delay with 1, 2
        mixer.target = 0f
        val wet = FloatArray(6) { 100f }
        mixer.mix(ramp(6, from = 3), wet, 6)
        // the original under the fade is 1, 2, 3 … — late by the two samples the chain is late by
        assertEquals(listOf(0.25f * 1 + 0.75f * 100, 0.5f * 2 + 0.5f * 100, 0.75f * 3 + 0.25f * 100, 4f, 5f, 6f), wet.toList())
        assertTrue(mixer.originalOnly)
    }

    @Test
    fun `a fade turned round midway goes back from where it was`() {
        val mixer = AbMixer(latencySamples = 0, fadeSamples = 4)
        mixer.target = 1f
        mixer.mix(FloatArray(2), FloatArray(2) { 1f }, 2)
        assertEquals(0.5f, mixer.processedShare, 0f)
        mixer.target = 0f
        mixer.mix(FloatArray(1), FloatArray(1) { 1f }, 1)
        assertEquals(0.25f, mixer.processedShare, 0f)
    }

    @Test
    fun `after a seek nothing of the old place is left in the delay`() {
        val mixer = AbMixer(latencySamples = 2, fadeSamples = 4)
        mixer.passOriginal(ramp(4), 4)
        mixer.reset()
        val dry = ramp(3, from = 50)
        mixer.passOriginal(dry, 3)
        assertEquals(listOf(0f, 0f, 50f), dry.toList())
    }
}
