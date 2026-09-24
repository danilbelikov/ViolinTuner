package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the meters of the «Звук» screen show; taken by the player a few dozen times a second. */
data class SoundMeters(
    /** Peak of what left the chain since the meters were last taken, dBFS. */
    val outputPeakDb: Double,
    /** How hard the compressor squeezes right now, positive decibels. */
    val reductionDb: Double,
    /** The limiter held the sound back noticeably since the meters were last taken. */
    val limiting: Boolean,
)

/**
 * The whole path of the sound (spec 5.11): equalizer → compressor → hall → volume → limiter.
 * One and the same object plays a recording aloud and renders it into a file — which is why
 * what is heard is what is sent. Mono, in place, not thread-safe: one thread feeds it, and
 * [set] is called from that same thread between buffers.
 *
 * Settings that leave the sound alone ([SoundRules.isNeutral]) are the caller's cue not to run
 * the chain at all: the limiter's look-ahead alone would delay the sound, and "everything off"
 * is promised to be the recording bit for bit.
 */
class SoundChain(private val sampleRate: Int, private val config: SoundConfig = SoundConfig()) {
    private val rampSamples = (config.smoothingMs / MS_PER_SECOND * sampleRate).roundToInt().coerceAtLeast(1)
    private val equalizer = Equalizer(sampleRate, config, rampSamples)
    private val compressor = Compressor(sampleRate, config)
    private val reverb = Reverb(sampleRate, config)
    private val limiter = Limiter(sampleRate, config)

    private var gainTarget = 1.0
    private var gain = 1.0
    private val glide = exp(-1.0 / rampSamples)
    private var outputPeak = 0.0
    private var primed = false

    /** The limiter looks ahead, so the sound leaves this many samples late; a file is trimmed by as much. */
    val latencySamples: Int get() = limiter.latencySamples

    /** The first settings — and any with [immediate] — take effect at once; later ones glide in over [SoundConfig.smoothingMs]. */
    fun set(settings: SoundSettings, immediate: Boolean = false) {
        val clean = SoundRules.clean(settings, config)
        val atOnce = immediate || !primed
        primed = true
        equalizer.set(clean.eq, atOnce)
        compressor.set(clean.compressor, atOnce)
        reverb.set(clean.reverb, atOnce)
        gainTarget = if (clean.output.enabled) 10.0.pow(clean.output.gainDb / 20) else 1.0
        if (atOnce) gain = gainTarget
    }

    /** After a seek: what rang in the filters and the hall belongs to another place of the recording. */
    fun reset() {
        equalizer.reset()
        compressor.reset()
        reverb.reset()
        limiter.reset()
        outputPeak = 0.0
    }

    fun process(buffer: FloatArray, count: Int = buffer.size) {
        for (index in 0 until count) {
            var value = buffer[index].toDouble()
            value = equalizer.process(value)
            if (!compressor.idle) value = compressor.process(value)
            if (!reverb.idle) value = reverb.process(value)
            gain = gainTarget + (gain - gainTarget) * glide
            value = limiter.process(value * gain)
            val level = abs(value)
            if (level > outputPeak) outputPeak = level
            buffer[index] = value.toFloat()
        }
    }

    /** Reads the meters and starts them anew. */
    fun takeMeters(): SoundMeters {
        val meters = SoundMeters(Limiter.toDb(outputPeak), compressor.reductionDb, limiter.takeLimiting())
        outputPeak = 0.0
        return meters
    }

    /** How many samples the hall rings on after the last one of the recording (spec 5.11). */
    fun tailSamples(settings: SoundSettings): Int = (SoundRules.tailSec(settings, config) * sampleRate).roundToInt()

    private companion object {
        const val MS_PER_SECOND = 1_000.0
    }
}
