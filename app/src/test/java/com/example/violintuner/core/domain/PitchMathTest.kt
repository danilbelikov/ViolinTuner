package com.example.violintuner.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMathTest {
    private val a4 = IntonationConfig().a4Hz

    @Test
    fun `A4 reference maps to midi 69 with zero cents`() {
        val midi = PitchMath.frequencyToMidi(440.0, a4)
        assertEquals(69.0, midi, EPS)
        assertEquals(0.0, PitchMath.centsFromNote(midi, 69), EPS)
    }

    @Test
    fun `G3 is 196 Hz`() {
        assertEquals(196.0, PitchMath.midiToFrequency(55.0, a4), 0.01)
    }

    @Test
    fun `one semitone up is 100 cents`() {
        val semitoneUp = PitchMath.midiToFrequency(70.0, a4)
        assertEquals(100.0, PitchMath.centsBetween(semitoneUp, 440.0), EPS)
        assertEquals(466.16, semitoneUp, 0.01)
    }

    @Test
    fun `octave is 1200 cents`() {
        assertEquals(1200.0, PitchMath.centsBetween(880.0, 440.0), EPS)
        assertEquals(-1200.0, PitchMath.centsBetween(220.0, 440.0), EPS)
    }

    @Test
    fun `nearest note switches at 50 cents`() {
        assertEquals(69, PitchMath.nearestMidi(69.49))
        assertEquals(70, PitchMath.nearestMidi(69.51))
        assertEquals(69, PitchMath.nearestMidi(68.51))
    }

    @Test
    fun `frequency and midi round trip over the violin range`() {
        for (midi in 55..100) {
            val freq = PitchMath.midiToFrequency(midi.toDouble(), a4)
            assertEquals(midi.toDouble(), PitchMath.frequencyToMidi(freq, a4), EPS)
        }
    }

    @Test
    fun `reference pitch shifts every note`() {
        assertEquals(69.0, PitchMath.frequencyToMidi(442.0, 442.0), EPS)
        assertEquals(442.0 / 440.0 * 196.0, PitchMath.midiToFrequency(55.0, 442.0), 0.01)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
