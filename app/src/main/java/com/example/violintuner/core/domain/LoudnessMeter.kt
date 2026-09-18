package com.example.violintuner.core.domain

import kotlin.math.exp
import kotlin.math.log10

/**
 * How loud the violin is, 0..1, for the ring that breathes with the sound (spec 5.8). The RMS
 * of a frame is put on a dBFS scale from the silence threshold to a ceiling and then smoothed:
 * fast towards a louder sound, slow back. Time comes from the frames; not thread-safe.
 *
 * Not part of [IntonationReading]: loudness says nothing about intonation and is not recorded.
 */
class LoudnessMeter(private val config: IntonationConfig) {
    private var level = 0.0
    private var lastTMs: Long? = null

    fun process(tMs: Long, rms: Double): Float {
        val target = rawLevel(rms, config)
        val previous = lastTMs
        lastTMs = tMs
        if (previous == null) {
            level = target
        } else {
            val elapsedMs = (tMs - previous).coerceAtLeast(0)
            val timeConstantMs = if (target > level) config.levelAttackMs else config.levelReleaseMs
            level += (target - level) * (1.0 - exp(-elapsedMs.toDouble() / timeConstantMs))
        }
        return level.toFloat()
    }

    fun reset() {
        level = 0.0
        lastTMs = null
    }

    companion object {
        private const val DB_PER_DECADE = 20.0

        /** Unsmoothed: 0 at the silence threshold and below (exact zeros included), 1 at the ceiling and above. */
        fun rawLevel(rms: Double, config: IntonationConfig): Double {
            if (rms <= 0.0) return 0.0
            val dbfs = DB_PER_DECADE * log10(rms)
            return ((dbfs - config.silenceRmsDbfs) / (config.levelCeilingDbfs - config.silenceRmsDbfs)).coerceIn(0.0, 1.0)
        }
    }
}
