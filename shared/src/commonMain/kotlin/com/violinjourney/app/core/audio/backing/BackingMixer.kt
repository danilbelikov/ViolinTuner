package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.audio.fx.StereoLimiter
import com.violinjourney.app.core.domain.backing.BackingOffset
import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.pow

/** Where the backing's samples come from: the prepared PCM on a device, anything in a test. */
fun interface BackingSource {
    /** [count] frames from [position] (may lie before the start or past the end: silence there), scaled by [gain]. */
    fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray)
}

/**
 * The violin, already through its chain, and the backing, shifted and levelled, into one stereo stream
 * (spec 5.25): the violin in the middle, the backing as it was recorded, a linked limiter over the sum.
 * A new shift or level glides in over [fadeSamples] — the backing read at the old shift fades out while the
 * new one fades in — so turning the slider while it plays never clicks. Pure; one per player or render.
 */
class BackingMixer(
    private val sampleRate: Int,
    private val source: BackingSource,
    offsetMs: Int,
    gainDb: Float,
    heard: Boolean,
    private val fadeSamples: Int,
    soundConfig: SoundConfig,
) {
    private val limiter = StereoLimiter(sampleRate, soundConfig)

    /** The limiter looks ahead: the mix comes out this much later than the violin went in. */
    val latencySamples: Int get() = limiter.latencySamples

    private var offset = offsetMs
    private var gain = gainOf(gainDb, heard)
    private var fromOffset = offsetMs
    private var fromGain = gain
    private var fadeLeft = 0

    private var left = FloatArray(0)
    private var right = FloatArray(0)
    private var oldLeft = FloatArray(0)
    private var oldRight = FloatArray(0)

    fun set(offsetMs: Int, gainDb: Float, heard: Boolean) {
        val newGain = gainOf(gainDb, heard)
        if (offsetMs == offset && newGain == gain) return
        // a change in the middle of a fade starts from where the ear is now: the old state is the one being faded to
        fromOffset = offset
        fromGain = gain
        offset = offsetMs
        gain = newGain
        fadeLeft = fadeSamples
    }

    fun reset() {
        limiter.reset()
        fadeLeft = 0
    }

    /**
     * [violin] holds [count] samples that start at the violin's sample [violinPosition]; [out] gets [count]
     * interleaved stereo frames.
     */
    fun mix(violin: FloatArray, count: Int, violinPosition: Long, out: FloatArray) {
        ensure(count)
        source.read(BackingOffset.backingSampleAt(violinPosition, offset, sampleRate), count, gain, left, right)
        if (fadeLeft > 0) {
            source.read(BackingOffset.backingSampleAt(violinPosition, fromOffset, sampleRate), count, fromGain, oldLeft, oldRight)
            for (i in 0 until count) {
                val t = if (fadeLeft > 0) 1f - fadeLeft.toFloat() / fadeSamples else 1f
                left[i] = oldLeft[i] * (1f - t) + left[i] * t
                right[i] = oldRight[i] * (1f - t) + right[i] * t
                if (fadeLeft > 0) fadeLeft--
            }
        }
        for (i in 0 until count) {
            out[2 * i] = violin[i] + left[i]
            out[2 * i + 1] = violin[i] + right[i]
        }
        limiter.process(out, count)
    }

    private fun ensure(count: Int) {
        if (left.size >= count) return
        left = FloatArray(count)
        right = FloatArray(count)
        oldLeft = FloatArray(count)
        oldRight = FloatArray(count)
    }

    private fun gainOf(gainDb: Float, heard: Boolean): Float = if (heard) 10.0.pow(gainDb / DB_PER_DECADE).toFloat() else 0f

    private companion object {
        const val DB_PER_DECADE = 20.0
    }
}
