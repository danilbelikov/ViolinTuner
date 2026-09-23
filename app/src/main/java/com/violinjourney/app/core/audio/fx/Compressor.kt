package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.domain.sound.CompressorSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Makes loud places quieter and — with the make-up gain — quiet ones louder. A peak detector, a
 * soft knee, the gain eased in by the attack and let go by the release. Switching it off is a
 * glide to "no change", not a cut.
 */
internal class Compressor(private val sampleRate: Int, private val config: SoundConfig) {
    private var thresholdDb = 0.0
    private var slope = 0.0
    private var attack = 0.0
    private var release = 0.0
    private var makeupTargetDb = 0.0
    private var enabledTarget = 0.0

    /** Decibels the signal is being turned down by right now; zero or negative. */
    private var gainReductionDb = 0.0
    private var makeupDb = 0.0
    private var enabled = 0.0
    private val glide = coefficient(config.smoothingMs)

    /** For the meter «Сейчас сжимает»: how hard it squeezes at this moment, as a positive number of decibels. */
    val reductionDb: Double get() = -gainReductionDb * enabled

    fun set(settings: CompressorSettings, immediate: Boolean) {
        thresholdDb = settings.thresholdDb
        slope = 1 / settings.ratio - 1
        attack = coefficient(settings.attackMs)
        release = coefficient(settings.releaseMs)
        makeupTargetDb = settings.makeupDb
        enabledTarget = if (settings.enabled) 1.0 else 0.0
        if (immediate) {
            makeupDb = makeupTargetDb
            enabled = enabledTarget
        }
    }

    fun reset() {
        gainReductionDb = 0.0
    }

    val idle: Boolean get() = enabledTarget == 0.0 && enabled < IDLE_BELOW

    fun process(sample: Double): Double {
        enabled = enabledTarget + (enabled - enabledTarget) * glide
        makeupDb = makeupTargetDb + (makeupDb - makeupTargetDb) * glide

        val levelDb = 20 * log10(abs(sample) + SILENCE)
        val wanted = reductionFor(levelDb)
        // More reduction is an attack, less of it a release.
        val pace = if (wanted < gainReductionDb) attack else release
        gainReductionDb = wanted + (gainReductionDb - wanted) * pace
        return sample * 10.0.pow((gainReductionDb + makeupDb) * enabled / 20)
    }

    /** The static curve with a soft knee [SoundConfig.kneeDb] wide around the threshold. */
    private fun reductionFor(levelDb: Double): Double {
        val over = levelDb - thresholdDb
        val knee = config.kneeDb
        return when {
            2 * over < -knee -> 0.0
            2 * abs(over) <= knee -> slope * (over + knee / 2).pow(2) / (2 * knee)
            else -> slope * over
        }
    }

    private fun coefficient(ms: Double): Double = exp(-1.0 / (ms / MS_PER_SECOND * sampleRate))

    private companion object {
        const val SILENCE = 1e-9
        const val MS_PER_SECOND = 1_000.0
        const val IDLE_BELOW = 1e-4
    }
}
