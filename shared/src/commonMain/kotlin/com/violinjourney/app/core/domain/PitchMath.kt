package com.violinjourney.app.core.domain

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Equal-temperament conversions. "Fractional MIDI" is pitch on a log scale, 1.0 = one semitone. */
object PitchMath {
    const val CENTS_PER_OCTAVE = 1200.0
    const val CENTS_PER_SEMITONE = 100.0
    const val SEMITONES_PER_OCTAVE = 12
    const val A4_MIDI = 69

    fun frequencyToMidi(freqHz: Double, a4Hz: Double): Double =
        A4_MIDI + SEMITONES_PER_OCTAVE * log2(freqHz / a4Hz)

    fun midiToFrequency(midi: Double, a4Hz: Double): Double =
        a4Hz * 2.0.pow((midi - A4_MIDI) / SEMITONES_PER_OCTAVE)

    fun centsBetween(freqHz: Double, targetHz: Double): Double =
        CENTS_PER_OCTAVE * log2(freqHz / targetHz)

    fun nearestMidi(fractionalMidi: Double): Int = fractionalMidi.roundToInt()

    /** Deviation of [fractionalMidi] from the note [targetMidi], in cents. */
    fun centsFromNote(fractionalMidi: Double, targetMidi: Int): Double =
        (fractionalMidi - targetMidi) * CENTS_PER_SEMITONE
}
