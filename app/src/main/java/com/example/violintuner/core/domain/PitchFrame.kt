package com.example.violintuner.core.domain

/**
 * One pitch estimate (spec 6). [freqHz], [cents] and [midi] are null when the detector found
 * no pitch; [cents] is relative to the nearest equal-tempered note [midi].
 * [rms] is linear with full scale = 1.0, [clarity] is 0..1.
 */
data class PitchFrame(
    val tMs: Long,
    val freqHz: Double?,
    val cents: Double?,
    val midi: Int?,
    val clarity: Double,
    val rms: Double,
) {
    companion object {
        fun pitched(
            tMs: Long,
            freqHz: Double,
            clarity: Double,
            rms: Double,
            a4Hz: Double,
        ): PitchFrame {
            val fractionalMidi = PitchMath.frequencyToMidi(freqHz, a4Hz)
            val midi = PitchMath.nearestMidi(fractionalMidi)
            return PitchFrame(
                tMs = tMs,
                freqHz = freqHz,
                cents = PitchMath.centsFromNote(fractionalMidi, midi),
                midi = midi,
                clarity = clarity,
                rms = rms,
            )
        }

        fun unpitched(tMs: Long, clarity: Double, rms: Double): PitchFrame =
            PitchFrame(tMs = tMs, freqHz = null, cents = null, midi = null, clarity = clarity, rms = rms)
    }
}
