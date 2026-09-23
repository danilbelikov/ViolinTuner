package com.violinjourney.app.core.domain.repertoire

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicalKeyTest {
    private fun name(tonic: Tonic, accidental: Accidental, mode: KeyMode) = MusicalKey(tonic, accidental, mode).germanName

    @Test
    fun `the keys of the brief read as a violinist writes them`() {
        assertEquals("G-dur", name(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR))
        assertEquals("a-moll", name(Tonic.A, Accidental.NATURAL, KeyMode.MINOR))
        assertEquals("d-moll", name(Tonic.D, Accidental.NATURAL, KeyMode.MINOR))
        assertEquals("fis-moll", name(Tonic.F, Accidental.SHARP, KeyMode.MINOR))
    }

    @Test
    fun `b natural is H and b flat is plain B`() {
        assertEquals("H-dur", name(Tonic.B, Accidental.NATURAL, KeyMode.MAJOR))
        assertEquals("h-moll", name(Tonic.B, Accidental.NATURAL, KeyMode.MINOR))
        assertEquals("B-dur", name(Tonic.B, Accidental.FLAT, KeyMode.MAJOR))
        assertEquals("b-moll", name(Tonic.B, Accidental.FLAT, KeyMode.MINOR))
        assertEquals("His-dur", name(Tonic.B, Accidental.SHARP, KeyMode.MAJOR))
    }

    @Test
    fun `e flat and a flat drop a vowel, the other flats do not`() {
        assertEquals("Es-dur", name(Tonic.E, Accidental.FLAT, KeyMode.MAJOR))
        assertEquals("es-moll", name(Tonic.E, Accidental.FLAT, KeyMode.MINOR))
        assertEquals("As-dur", name(Tonic.A, Accidental.FLAT, KeyMode.MAJOR))
        assertEquals("Des-dur", name(Tonic.D, Accidental.FLAT, KeyMode.MAJOR))
        assertEquals("ges-moll", name(Tonic.G, Accidental.FLAT, KeyMode.MINOR))
        assertEquals("Ces-dur", name(Tonic.C, Accidental.FLAT, KeyMode.MAJOR))
        assertEquals("Fes-dur", name(Tonic.F, Accidental.FLAT, KeyMode.MAJOR))
    }

    @Test
    fun `every sharp adds is`() {
        val expected = mapOf(
            Tonic.C to "Cis", Tonic.D to "Dis", Tonic.E to "Eis", Tonic.F to "Fis",
            Tonic.G to "Gis", Tonic.A to "Ais", Tonic.B to "His",
        )
        expected.forEach { (tonic, root) -> assertEquals("$root-dur", name(tonic, Accidental.SHARP, KeyMode.MAJOR)) }
    }

    @Test
    fun `all forty two keys have distinct names, major capitalised and minor not`() {
        val names = Tonic.entries.flatMap { t -> Accidental.entries.flatMap { a -> KeyMode.entries.map { m -> name(t, a, m) } } }
        assertEquals(42, names.toSet().size)
        names.forEach { n ->
            if (n.endsWith("-dur")) assertEquals(n.first().uppercaseChar(), n.first()) else assertEquals(n.lowercase(), n)
        }
    }
}
