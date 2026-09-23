package com.violinjourney.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringSnapperTest {
    private val config = IntonationConfig()

    private fun freq(midi: Double) = PitchMath.midiToFrequency(midi, config.a4Hz)

    private fun snap(midi: Double, locked: ViolinString? = null) =
        StringSnapper.snap(freq(midi), locked, config)

    @Test
    fun `string frequencies derive from the A4 reference`() {
        assertEquals(196.0, ViolinString.G3.frequency(440.0), 0.01)
        assertEquals(293.66, ViolinString.D4.frequency(440.0), 0.01)
        assertEquals(440.0, ViolinString.A4.frequency(440.0), 1e-9)
        assertEquals(659.26, ViolinString.E5.frequency(440.0), 0.01)
        assertEquals(442.0, ViolinString.A4.frequency(442.0), 1e-9)
    }

    @Test
    fun `each string snaps to itself`() {
        ViolinString.entries.forEach { assertEquals(it, snap(it.midi.toDouble())) }
    }

    @Test
    fun `midpoint between strings is measured in cents, not hertz`() {
        assertEquals(ViolinString.D4, snap(65.4))
        assertEquals(ViolinString.A4, snap(65.6))
    }

    @Test
    fun `margin is 350 cents on the outer sides`() {
        assertEquals(ViolinString.G3, snap(55 - 3.4))
        assertNull(snap(55 - 3.6))
        assertEquals(ViolinString.E5, snap(76 + 3.4))
        assertNull(snap(76 + 3.6))
    }

    @Test
    fun `locked string wins regardless of pitch`() {
        assertEquals(ViolinString.A4, snap(62.0, locked = ViolinString.A4))
        assertEquals(ViolinString.G3, snap(90.0, locked = ViolinString.G3))
    }

    @Test
    fun `strings map back from midi`() {
        assertEquals(ViolinString.D4, ViolinString.fromMidi(62))
        assertNull(ViolinString.fromMidi(63))
        assertEquals("E5", ViolinString.E5.note.name)
    }
}
