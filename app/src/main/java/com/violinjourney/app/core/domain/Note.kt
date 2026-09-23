package com.violinjourney.app.core.domain

/** A chromatic note identified by its MIDI number. Names are Latin with sharps only (spec 3.6). */
@JvmInline
value class Note(val midi: Int) {
    private val pitchClass: Int
        get() = Math.floorMod(midi, PitchMath.SEMITONES_PER_OCTAVE)

    val letter: Char
        get() = LETTERS[pitchClass]

    val isSharp: Boolean
        get() = SHARPS[pitchClass]

    val octave: Int
        get() = Math.floorDiv(midi, PitchMath.SEMITONES_PER_OCTAVE) + MIDI_OCTAVE_OFFSET

    /** E.g. "A4", "F#5". */
    val name: String
        get() = buildString {
            append(letter)
            if (isSharp) append(SHARP_SIGN)
            append(octave)
        }

    override fun toString(): String = name

    private companion object {
        const val SHARP_SIGN = '#'
        const val MIDI_OCTAVE_OFFSET = -1
        const val LETTERS = "CCDDEFFGGAAB"
        val SHARPS = booleanArrayOf(
            false, true, false, true, false, false, true, false, true, false, true, false,
        )
    }
}
