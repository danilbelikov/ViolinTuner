package com.violinjourney.app.core.domain

import kotlin.math.pow

/** Frame builders shared by engine tests. Frames are 10 ms apart. */
internal object TestFrames {
    const val STEP_MS = 10L
    private const val A4_HZ = 440.0

    fun hzOf(midi: Int, cents: Double = 0.0): Double =
        PitchMath.midiToFrequency(midi.toDouble(), A4_HZ) * 2.0.pow(cents / PitchMath.CENTS_PER_OCTAVE)

    fun played(tMs: Long, midi: Int, cents: Double = 0.0): PitchFrame =
        PitchFrame.pitched(tMs, hzOf(midi, cents), clarity = 0.97, rms = 0.2, a4Hz = A4_HZ)

    fun quiet(tMs: Long): PitchFrame = PitchFrame.unpitched(tMs, clarity = 0.0, rms = 0.0005)

    fun noise(tMs: Long): PitchFrame = PitchFrame.unpitched(tMs, clarity = 0.3, rms = 0.1)

    /** Timestamps from [fromMs] inclusive to [untilMs] exclusive. */
    fun times(fromMs: Long, untilMs: Long): List<Long> = (fromMs until untilMs step STEP_MS).toList()
}
