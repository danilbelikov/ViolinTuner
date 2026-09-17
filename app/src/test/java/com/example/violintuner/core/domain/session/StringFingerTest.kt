package com.example.violintuner.core.domain.session

import com.example.violintuner.core.domain.ViolinString
import org.junit.Assert.assertEquals
import org.junit.Test

class StringFingerTest {
    private fun assertPosition(midi: Int, string: ViolinString, finger: Finger) =
        assertEquals("midi $midi", StringFinger(string, finger), StringFinger.of(midi))

    @Test
    fun `open strings`() {
        ViolinString.entries.forEach { assertPosition(it.midi, it, Finger.OPEN) }
    }

    @Test
    fun `first position on the lower strings covers six semitones`() {
        assertPosition(56, ViolinString.G3, Finger.FIRST)
        assertPosition(57, ViolinString.G3, Finger.FIRST) // A3, as in the handoff table
        assertPosition(59, ViolinString.G3, Finger.SECOND) // B3
        assertPosition(60, ViolinString.G3, Finger.THIRD) // C4
        assertPosition(61, ViolinString.G3, Finger.THIRD)
        assertPosition(66, ViolinString.D4, Finger.SECOND) // F#4
        assertPosition(73, ViolinString.A4, Finger.SECOND) // C#5
        assertPosition(74, ViolinString.A4, Finger.THIRD) // D5
    }

    @Test
    fun `the E string goes on to the fourth finger and into positions`() {
        assertPosition(78, ViolinString.E5, Finger.FIRST) // F#5
        assertPosition(81, ViolinString.E5, Finger.THIRD) // A5
        assertPosition(83, ViolinString.E5, Finger.FOURTH) // B5
        assertPosition(84, ViolinString.E5, Finger.HIGHER_POSITION)
        assertPosition(100, ViolinString.E5, Finger.HIGHER_POSITION)
    }

    @Test
    fun `a flat G string below G3 is still the open G`() {
        assertPosition(54, ViolinString.G3, Finger.OPEN)
    }
}
