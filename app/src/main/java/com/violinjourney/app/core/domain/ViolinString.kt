package com.violinjourney.app.core.domain

import kotlin.math.abs

/** Open strings, low to high. */
enum class ViolinString(val midi: Int) {
    G3(55), D4(62), A4(69), E5(76);

    val note: Note get() = Note(midi)

    fun frequency(a4Hz: Double): Double = PitchMath.midiToFrequency(midi.toDouble(), a4Hz)

    companion object {
        fun fromMidi(midi: Int): ViolinString? = entries.firstOrNull { it.midi == midi }
    }
}

/** Target selection for the tuning mode (spec 5.4). */
object StringSnapper {
    /**
     * A [locked] string always wins. Otherwise the nearest string by pitch, or null when the
     * sound is further than [IntonationConfig.stringSnapMarginCents] from every string.
     */
    fun snap(freqHz: Double, locked: ViolinString?, config: IntonationConfig): ViolinString? {
        if (locked != null) return locked
        val midi = PitchMath.frequencyToMidi(freqHz, config.a4Hz)
        val nearest = ViolinString.entries.minBy { abs(midi - it.midi) }
        val distanceCents = abs(PitchMath.centsFromNote(midi, nearest.midi))
        return nearest.takeIf { distanceCents <= config.stringSnapMarginCents }
    }
}
