package com.violinjourney.app.core.domain.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.Accidental.FLAT
import com.violinjourney.app.core.domain.repertoire.Accidental.NATURAL
import com.violinjourney.app.core.domain.repertoire.Accidental.SHARP
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.Tonic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class ScalesTest {
    private val g3 = 55
    private val e7 = 100

    private fun scale(tonic: Tonic, accidental: Accidental, kind: ScaleKind, octaves: Int) =
        Scales.build(ScaleSpec(tonic, accidental, kind, octaves), g3, e7)

    private fun names(notes: List<ScaleNote>) = notes.joinToString(" ") {
        it.letter.name + when (it.alter) { -1 -> "b"; 1 -> "#"; 2 -> "x"; else -> "" } + it.octave
    }

    @Test
    fun `key signatures count their signs`() {
        assertEquals(1, Scales.fifthsOf(Tonic.G, NATURAL, KeyMode.MAJOR))
        assertEquals(-1, Scales.fifthsOf(Tonic.F, NATURAL, KeyMode.MAJOR))
        assertEquals(0, Scales.fifthsOf(Tonic.A, NATURAL, KeyMode.MINOR))
        assertEquals(5, Scales.fifthsOf(Tonic.G, SHARP, KeyMode.MINOR))
        assertEquals(-3, Scales.fifthsOf(Tonic.E, FLAT, KeyMode.MAJOR))
        assertEquals(7, Scales.fifthsOf(Tonic.C, SHARP, KeyMode.MAJOR))
        assertEquals(-7, Scales.fifthsOf(Tonic.A, FLAT, KeyMode.MINOR))
    }

    @Test
    fun `fifteen major and fifteen minor keys are allowed — the ones with eight signs are not`() {
        val major = Tonic.entries.flatMap { t -> Accidental.entries.map { t to it } }.count { (t, a) -> Scales.isKeyAllowed(t, a, ScaleKind.MAJOR) }
        val minor = Tonic.entries.flatMap { t -> Accidental.entries.map { t to it } }.count { (t, a) -> Scales.isKeyAllowed(t, a, ScaleKind.HARMONIC_MINOR) }
        assertEquals(15, major)
        assertEquals(15, minor)
        assertFalse(Scales.isKeyAllowed(Tonic.G, SHARP, ScaleKind.MAJOR))
        assertTrue(Scales.isKeyAllowed(Tonic.G, SHARP, ScaleKind.NATURAL_MINOR))
        assertNull(scale(Tonic.G, SHARP, ScaleKind.MAJOR, 1))
    }

    @Test
    fun `a major scale goes up and comes back — the top note once`() {
        val c = scale(Tonic.C, NATURAL, ScaleKind.MAJOR, 1)!!
        assertEquals("C4 D4 E4 F4 G4 A4 B4 C5 B4 A4 G4 F4 E4 D4 C4", names(c.notes))
        assertEquals(15, c.notes.size)
        assertEquals(43, scale(Tonic.G, NATURAL, ScaleKind.MAJOR, 3)!!.notes.size)
    }

    @Test
    fun `the scale starts at the lowest tonic the violin has`() {
        assertEquals("G3", names(listOf(scale(Tonic.G, NATURAL, ScaleKind.MAJOR, 3)!!.lowest)))
        assertEquals("G6", names(listOf(scale(Tonic.G, NATURAL, ScaleKind.MAJOR, 3)!!.highest)))
        assertEquals("F4", names(listOf(scale(Tonic.F, NATURAL, ScaleKind.MAJOR, 1)!!.lowest)))
        assertEquals("Ab3", names(listOf(scale(Tonic.A, FLAT, ScaleKind.MAJOR, 1)!!.lowest)))
        // G flat sounds below the open G string
        assertEquals("Gb4", names(listOf(scale(Tonic.G, FLAT, ScaleKind.MAJOR, 1)!!.lowest)))
    }

    @Test
    fun `octaves that leave the instrument are not offered`() {
        assertTrue(Scales.octavesFit(Tonic.G, NATURAL, 3, g3, e7))
        assertTrue(Scales.octavesFit(Tonic.D, NATURAL, 3, g3, e7)) // D4 … D7
        assertTrue(Scales.octavesFit(Tonic.E, NATURAL, 3, g3, e7)) // E4 … E7, the very top
        assertFalse(Scales.octavesFit(Tonic.F, NATURAL, 3, g3, e7)) // F7 is off the fingerboard we know
        assertTrue(Scales.octavesFit(Tonic.F, NATURAL, 2, g3, e7))
        assertFalse(Scales.octavesFit(Tonic.G, NATURAL, 4, g3, e7))
        assertNull(scale(Tonic.F, NATURAL, ScaleKind.MAJOR, 3))
    }

    @Test
    fun `letters follow letters — the signature does the rest`() {
        assertEquals("Eb4 F4 G4 Ab4 Bb4 C5 D5 Eb5", names(scale(Tonic.E, FLAT, ScaleKind.MAJOR, 1)!!.notes.take(8)))
        assertEquals("F#4 G#4 A#4 B4 C#5 D#5 E#5 F#5", names(scale(Tonic.F, SHARP, ScaleKind.MAJOR, 1)!!.notes.take(8)))
    }

    @Test
    fun `the harmonic minor raises its seventh both ways — up to a double sharp`() {
        val a = scale(Tonic.A, NATURAL, ScaleKind.HARMONIC_MINOR, 1)!!
        assertEquals("A3 B3 C4 D4 E4 F4 G#4 A4 G#4 F4 E4 D4 C4 B3 A3", names(a.notes))
        val gis = scale(Tonic.G, SHARP, ScaleKind.HARMONIC_MINOR, 1)!!
        assertEquals("G#3 A#3 B3 C#4 D#4 E4 Fx4 G#4", names(gis.notes.take(8)))
    }

    @Test
    fun `the melodic minor goes up raised and comes down natural`() {
        val a = scale(Tonic.A, NATURAL, ScaleKind.MELODIC_MINOR, 1)!!
        assertEquals("A3 B3 C4 D4 E4 F#4 G#4 A4 G4 F4 E4 D4 C4 B3 A3", names(a.notes))
        val natural = scale(Tonic.A, NATURAL, ScaleKind.NATURAL_MINOR, 1)!!
        assertEquals("A3 B3 C4 D4 E4 F4 G4 A4 G4 F4 E4 D4 C4 B3 A3", names(natural.notes))
    }

    @Test
    fun `positions count from the bottom line of the staff`() {
        assertEquals(0, ScaleNote(Tonic.E, 4, 0).position)
        assertEquals(4, ScaleNote(Tonic.B, 4, 0).position)
        assertEquals(8, ScaleNote(Tonic.F, 5, 1).position)
        assertEquals(-2, ScaleNote(Tonic.C, 4, 0).position)
        assertEquals(-5, ScaleNote(Tonic.G, 3, 0).position)
        assertEquals(20, ScaleNote(Tonic.D, 7, 0).position)
        assertEquals(60, ScaleNote(Tonic.C, 4, 0).midi)
        assertEquals(55, ScaleNote(Tonic.G, 3, 0).midi)
    }

    @Test
    fun `every allowed key builds in every kind it allows`() {
        for (tonic in Tonic.entries) for (accidental in Accidental.entries) for (kind in ScaleKind.entries) {
            if (Scales.isKeyAllowed(tonic, accidental, kind)) assertNotNull(scale(tonic, accidental, kind, 1), "$tonic $accidental $kind")
        }
    }
}
