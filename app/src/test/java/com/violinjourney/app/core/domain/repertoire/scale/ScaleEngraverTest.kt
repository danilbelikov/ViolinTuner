package com.violinjourney.app.core.domain.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.Tonic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reference frames of the handoff (24h1–24h6), as numbers. */
class ScaleEngraverTest {
    private fun scale(tonic: Tonic, accidental: Accidental, kind: ScaleKind, octaves: Int) =
        Scales.build(ScaleSpec(tonic, accidental, kind, octaves), 55, 100)!!

    private fun accidentals(engraving: Engraving) =
        engraving.systems.flatMap { it.notes }.mapNotNull { n -> n.accidental?.let { "${n.note.letter}${n.note.octave}:$it" } }

    @Test
    fun `a major scale needs no accidentals, the signature carries them`() {
        val engraving = ScaleEngraver.engrave(scale(Tonic.E, Accidental.FLAT, ScaleKind.MAJOR, 2), widthSp = 60f)
        assertEquals(emptyList<String>(), accidentals(engraving))
        assertEquals(listOf(4, 7, 3), engraving.signs.map { it.position })
        assertTrue(engraving.signs.none { it.sharp })
    }

    @Test
    fun `sharps of the signature sit where a treble clef puts them`() {
        val engraving = ScaleEngraver.engrave(scale(Tonic.G, Accidental.SHARP, ScaleKind.NATURAL_MINOR, 1), widthSp = 60f)
        assertEquals(listOf(8, 5, 9, 6, 3), engraving.signs.map { it.position })
        assertTrue(engraving.signs.all { it.sharp })
    }

    @Test
    fun `the melodic minor cancels its raised steps on the way down, once each`() {
        val engraving = ScaleEngraver.engrave(scale(Tonic.A, Accidental.NATURAL, ScaleKind.MELODIC_MINOR, 1), widthSp = 200f)
        assertEquals(listOf("F4:1", "G4:1", "G4:0", "F4:0"), accidentals(engraving))
    }

    @Test
    fun `the state of a step survives the break of a system`() {
        // narrow enough to break between the way up and the way down
        val engraving = ScaleEngraver.engrave(scale(Tonic.A, Accidental.NATURAL, ScaleKind.MELODIC_MINOR, 1), widthSp = 30f)
        assertTrue(engraving.systems.size > 1)
        assertEquals(listOf("F4:1", "G4:1", "G4:0", "F4:0"), accidentals(engraving))
    }

    @Test
    fun `the harmonic minor marks its seventh once per octave and keeps it`() {
        val engraving = ScaleEngraver.engrave(scale(Tonic.G, Accidental.SHARP, ScaleKind.HARMONIC_MINOR, 2), widthSp = 300f)
        assertEquals(listOf("F4:2", "F5:2"), accidentals(engraving))
    }

    @Test
    fun `notes are shared evenly between the systems`() {
        val g = scale(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)
        // 356 dp of the card at 7.5 dp a space
        val engraving = ScaleEngraver.engrave(g, widthSp = 356f / 7.5f)
        assertEquals(listOf(15, 14, 14), engraving.systems.map { it.notes.size })
        assertEquals(listOf(false, false, true), engraving.systems.map { it.last })
        assertEquals(3 * NotationMetrics.SYSTEM_HEIGHT, engraving.heightSp, 0f)
        // the landscape stand: 29 notes go 15 + 14, not 27 + 2
        val a = scale(Tonic.A, Accidental.NATURAL, ScaleKind.MELODIC_MINOR, 2)
        assertEquals(listOf(15, 14), ScaleEngraver.engrave(a, widthSp = 70f).systems.map { it.notes.size })
    }

    @Test
    fun `no key, kind or width leaves a lonely note on the last system`() {
        for (tonic in Tonic.entries) for (accidental in Accidental.entries) for (kind in ScaleKind.entries) for (octaves in 1..3) {
            val built = Scales.build(ScaleSpec(tonic, accidental, kind, octaves), 55, 100) ?: continue
            for (width in listOf(356f / 7.5f, 388f / 7f, 364f / 10f, 700f / 10f, 328f / 6.5f)) {
                val sizes = ScaleEngraver.engrave(built, width).systems.map { it.notes.size }
                assertEquals(built.notes.size, sizes.sum())
                assertTrue("$tonic $accidental $kind $octaves at $width: $sizes", sizes.max() - sizes.min() <= 2 && sizes.min() >= 2)
            }
        }
    }

    @Test
    fun `stems go up below the middle line and down from it`() {
        val notes = ScaleEngraver.engrave(scale(Tonic.C, Accidental.NATURAL, ScaleKind.MAJOR, 1), widthSp = 200f).systems.single().notes
        assertEquals(listOf(-2, -1, 0, 1, 2, 3, 4, 5), notes.take(8).map { it.position })
        assertEquals(listOf(true, true, true, true, true, true, false, false), notes.take(8).map { it.stemUp })
    }

    @Test
    fun `ledger lines run from the staff out to the note`() {
        val notes = ScaleEngraver.engrave(scale(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3), widthSp = 400f).systems.single().notes
        assertEquals(listOf(-2, -4), notes.first().ledgers) // G3 hangs under its second ledger line
        assertEquals(listOf(-2), notes.first { it.note.letter == Tonic.C && it.note.octave == 4 }.ledgers)
        assertEquals(emptyList<Int>(), notes.first { it.note.letter == Tonic.E && it.note.octave == 4 }.ledgers)
        assertEquals(listOf(10), notes.first { it.note.letter == Tonic.A && it.note.octave == 5 }.ledgers)
        assertEquals(listOf(10, 12, 14, 16), notes.first { it.note.octave == 6 && it.note.letter == Tonic.G }.ledgers)
    }

    @Test
    fun `what would need a sixth ledger line is written an octave lower under 8va`() {
        val engraving = ScaleEngraver.engrave(scale(Tonic.D, Accidental.NATURAL, ScaleKind.MAJOR, 3), widthSp = 400f)
        val notes = engraving.systems.single().notes
        val top = notes.single { it.note.octave == 7 && it.note.letter == Tonic.D }
        assertTrue(top.ottava)
        assertEquals(13, top.position)
        assertEquals(1, notes.count { it.ottava })
        assertTrue(notes.filter { it.note.position == 19 }.none { it.ottava })
        val span = engraving.systems.single().ottavas.single()
        assertTrue(span.fromX < top.x && top.x < span.toX)
        // the bracket gets room of its own above the five ledger lines
        assertEquals(NotationMetrics.SYSTEM_HEIGHT + NotationMetrics.OTTAVA_ROOM, engraving.heightSp, 0f)
        assertEquals(NotationMetrics.OTTAVA_ROOM + NotationMetrics.SYSTEM_TOP + NotationMetrics.STAFF_HEIGHT, engraving.y(0, 0), 0f)
    }

    @Test
    fun `notes keep their step, an accidental pushes its note to the right`() {
        val notes = ScaleEngraver.engrave(scale(Tonic.A, Accidental.NATURAL, ScaleKind.HARMONIC_MINOR, 1), widthSp = 200f).systems.single().notes
        assertEquals(NotationMetrics.NOTE_STEP, notes[1].x - notes[0].x, 1e-4f)
        val sharpened = notes.indexOfFirst { it.accidental != null }
        assertEquals(NotationMetrics.NOTE_STEP + NotationMetrics.ACCIDENTAL_EXTRA, notes[sharpened].x - notes[sharpened - 1].x, 1e-4f)
    }
}
