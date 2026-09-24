package com.violinjourney.app.core.domain.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.Tonic

/** The four scales a violinist plays every day (spec 3.22). The melodic minor goes up raised and comes down natural. */
enum class ScaleKind(val mode: KeyMode) {
    MAJOR(KeyMode.MAJOR),
    NATURAL_MINOR(KeyMode.MINOR),
    HARMONIC_MINOR(KeyMode.MINOR),
    MELODIC_MINOR(KeyMode.MINOR),
}

/** What the player picks: a key, a kind, how many octaves. Everything else — the notes, the name, the range — follows. */
data class ScaleSpec(val tonic: Tonic, val accidental: Accidental, val kind: ScaleKind, val octaves: Int) {
    val key: MusicalKey get() = MusicalKey(tonic, accidental, kind.mode)
}

/**
 * One note of a scale as it is written, not as it sounds: the letter follows the letter (no
 * enharmonic shortcuts), [alter] is what the note carries in semitones — −1 flat, 0 natural,
 * 1 sharp, 2 double sharp — whether the key signature says so or an accidental has to.
 */
data class ScaleNote(val letter: Tonic, val octave: Int, val alter: Int) {
    val midi: Int get() = MIDI_PER_OCTAVE * (octave + 1) + Scales.semitoneOf(letter) + alter

    /** Steps of the staff from its bottom line: E4 is 0, the middle line B4 is 4, the top line F5 is 8. */
    val position: Int get() = octave * LETTERS + letter.ordinal - BOTTOM_LINE

    private companion object {
        const val MIDI_PER_OCTAVE = 12
        const val LETTERS = 7

        /** E4: octave 4 × 7 + the index of E. */
        const val BOTTOM_LINE = 30
    }
}

/** A scale built from a [ScaleSpec]: up to the top and back, the top note once. */
data class Scale(
    val spec: ScaleSpec,
    /** Sharps of the key signature when positive, flats when negative. */
    val fifths: Int,
    /** What the key signature does to each letter. */
    val signature: Map<Tonic, Int>,
    val notes: List<ScaleNote>,
) {
    val lowest: ScaleNote get() = notes.first()
    val highest: ScaleNote get() = notes.maxBy { it.midi }
}

/** The rules of scales (spec 5.16). Pure; the numbers of the instrument come from outside. */
object Scales {
    /** The order in which a key signature gains its sharps; flats come in the reverse order. */
    val SHARP_ORDER = listOf(Tonic.F, Tonic.C, Tonic.G, Tonic.D, Tonic.A, Tonic.E, Tonic.B)
    val FLAT_ORDER = SHARP_ORDER.reversed()

    const val MAX_SIGNS = 7
    const val MAX_OCTAVES = 3

    private val SEMITONES = mapOf(Tonic.C to 0, Tonic.D to 2, Tonic.E to 4, Tonic.F to 5, Tonic.G to 7, Tonic.A to 9, Tonic.B to 11)
    private val FIFTHS = mapOf(Tonic.F to -1, Tonic.C to 0, Tonic.G to 1, Tonic.D to 2, Tonic.A to 3, Tonic.E to 4, Tonic.B to 5)
    private const val MINOR_SHIFT = 3
    private const val LETTERS = 7
    private const val SIXTH = 5
    private const val SEVENTH = 6

    fun semitoneOf(letter: Tonic): Int = SEMITONES.getValue(letter)

    fun alterOf(accidental: Accidental): Int = when (accidental) {
        Accidental.FLAT -> -1
        Accidental.NATURAL -> 0
        Accidental.SHARP -> 1
    }

    /** Signs of the key signature: G-dur is 1, F-dur −1, a-moll 0, gis-moll 5. */
    fun fifthsOf(tonic: Tonic, accidental: Accidental, mode: KeyMode): Int =
        FIFTHS.getValue(tonic) + LETTERS * alterOf(accidental) - if (mode == KeyMode.MINOR) MINOR_SHIFT else 0

    /** Only keys a musician would write: seven signs at most. Gis-dur with its eight is As-dur to everyone. */
    fun isKeyAllowed(tonic: Tonic, accidental: Accidental, kind: ScaleKind): Boolean =
        kotlin.math.abs(fifthsOf(tonic, accidental, kind.mode)) <= MAX_SIGNS

    fun signatureOf(fifths: Int): Map<Tonic, Int> {
        val altered = if (fifths >= 0) SHARP_ORDER.take(fifths) else FLAT_ORDER.take(-fifths)
        return Tonic.entries.associateWith { if (it in altered) (if (fifths > 0) 1 else -1) else 0 }
    }

    /** The lowest tonic the instrument has: the first one not below [lowestMidi] (G3 on the violin). */
    fun lowestTonic(tonic: Tonic, accidental: Accidental, lowestMidi: Int): ScaleNote {
        var note = ScaleNote(tonic, octave = 0, alter = alterOf(accidental))
        while (note.midi < lowestMidi) note = note.copy(octave = note.octave + 1)
        return note
    }

    /** True while the top note still is on the instrument. */
    fun octavesFit(tonic: Tonic, accidental: Accidental, octaves: Int, lowestMidi: Int, highestMidi: Int): Boolean =
        octaves in 1..MAX_OCTAVES && lowestTonic(tonic, accidental, lowestMidi).midi + 12 * octaves <= highestMidi

    /** Null when the spec is not a scale this app draws: too many signs, or octaves the instrument does not have. */
    fun build(spec: ScaleSpec, lowestMidi: Int, highestMidi: Int): Scale? {
        if (!isKeyAllowed(spec.tonic, spec.accidental, spec.kind)) return null
        if (!octavesFit(spec.tonic, spec.accidental, spec.octaves, lowestMidi, highestMidi)) return null
        val fifths = fifthsOf(spec.tonic, spec.accidental, spec.kind.mode)
        val signature = signatureOf(fifths)
        val start = lowestTonic(spec.tonic, spec.accidental, lowestMidi)
        fun degree(step: Int, up: Boolean): ScaleNote {
            val degree = step % LETTERS
            val index = spec.tonic.ordinal + degree
            val letter = Tonic.entries[index % LETTERS]
            val raised = when (spec.kind) {
                ScaleKind.HARMONIC_MINOR -> degree == SEVENTH
                ScaleKind.MELODIC_MINOR -> up && (degree == SIXTH || degree == SEVENTH)
                else -> false
            }
            return ScaleNote(letter, start.octave + index / LETTERS + step / LETTERS, signature.getValue(letter) + if (raised) 1 else 0)
        }
        val top = LETTERS * spec.octaves
        val notes = (0..top).map { degree(it, up = true) } + (top - 1 downTo 0).map { degree(it, up = false) }
        return Scale(spec, fifths, signature, notes)
    }
}
