package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.domain.sound.ReverbSettings
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * An algorithmic hall: a pre-delay, eight comb filters side by side and four all-pass filters in
 * a row — the classic Schroeder–Moorer layout with the Freeverb tunings. Not a convolution with
 * a real hall and not pretending to be one (spec 3.17).
 *
 * The feedback of every comb is derived from the wanted RT60, so «длина хвоста 1.8 с» means the
 * tail is 60 dB down after 1.8 seconds; the input of every comb is scaled by √(1 − g²), so a
 * longer tail is longer, not louder.
 */
internal class Reverb(private val sampleRate: Int, private val config: SoundConfig) {
    /** The walls the lines are built for, and the ones asked for while the old tail is still being faded out. */
    private var space: ReverbSpace? = null
    private var nextSpace: ReverbSpace? = null
    private var combs: Array<Comb> = emptyArray()
    private var allPasses: Array<AllPass> = emptyArray()
    private var decaySec = 0.0
    private var damping = 0.0

    /** A change on the fly takes this long (spec 5.11): the pre-delay's crossfade, the fall and the rise of [gate]. */
    private val rampSamples = (config.smoothingMs / MS_PER_SECOND * sampleRate).roundToInt().coerceAtLeast(1)

    /**
     * How far open the lines are, in steps of [rampSamples]: all the way while nothing changes (× 1.0 — the sound is
     * the same as with no gate at all), shut while another space waits to be built, then open again from silence.
     */
    private var gate = rampSamples

    private val preDelay = DoubleArray((config.preDelayMs.max / MS_PER_SECOND * sampleRate).toInt() + 2)
    private var preDelayWrite = 0

    /** The pre-delay moves by a crossfade of two taps, from [preDelayFrom] to [preDelayTo], never by a jump of the tap. */
    private var preDelayWanted = 0
    private var preDelayFrom = 0
    private var preDelayTo = 0
    private var preDelayFade = 0

    private var mixTarget = 0.0
    private var mix = 0.0
    private val glide = exp(-1.0 / (config.smoothingMs / MS_PER_SECOND * sampleRate))

    fun set(settings: ReverbSettings, immediate: Boolean) {
        // read before anything changes: a hall that rested was not run, and what its lines hold is a tail frozen long ago
        val resting = idle
        decaySec = settings.decaySec
        val cutoff = config.dullTailHz * (config.brightTailHz / config.dullTailHz).pow(settings.brightness)
        damping = exp(-2 * PI * cutoff / sampleRate)
        preDelayWanted = (settings.preDelayMs / MS_PER_SECOND * sampleRate).roundToInt().coerceIn(0, preDelay.size - 2)
        mixTarget = if (settings.enabled) settings.mix else 0.0
        if (immediate) mix = mixTarget
        if (immediate || resting || space == null) {
            // nothing is heard of the hall: no tail to fade out, no tap to glide
            if (settings.space != space) build(settings.space)
            nextSpace = null
            openAtOnce()
        } else {
            // null also calls off a switch still waiting, back to the walls that are there
            nextSpace = settings.space.takeIf { it != space }
        }
        combs.forEach { it.tune(decaySec, damping, sampleRate) }
        // a tail frozen when the hall went quiet belongs to another place of the recording: waking, it starts from silence
        if (resting && mixTarget > 0.0) reset()
    }

    fun reset() {
        nextSpace?.let(::build)
        nextSpace = null
        combs.forEach { it.clear() }
        allPasses.forEach { it.clear() }
        preDelay.fill(0.0)
        openAtOnce()
    }

    /** While idle the hall is not run at all ([SoundChain]), so its lines stand still; they are cleared on waking ([set]). */
    val idle: Boolean get() = mixTarget == 0.0 && mix < IDLE_BELOW

    fun process(sample: Double): Double {
        mix = mixTarget + (mix - mixTarget) * glide

        val waiting = nextSpace
        if (waiting != null) {
            if (--gate <= 0) {
                gate = 0
                build(waiting)
                nextSpace = null
            }
        } else if (gate < rampSamples) {
            gate++
        }
        val open = if (gate == rampSamples) 1.0 else gate.toDouble() / rampSamples

        preDelay[preDelayWrite] = sample
        if (preDelayFade == 0 && preDelayTo != preDelayWanted) {
            // a wish that came during a crossfade waits for its end: the tap never jumps
            preDelayFrom = preDelayTo
            preDelayTo = preDelayWanted
            preDelayFade = rampSamples
        }
        var delayed = tap(preDelayTo)
        if (preDelayFade > 0) {
            val old = preDelayFade.toDouble() / rampSamples
            delayed = delayed * (1 - old) + tap(preDelayFrom) * old
            preDelayFade--
        }
        if (++preDelayWrite == preDelay.size) preDelayWrite = 0

        var wet = 0.0
        val input = delayed * open
        for (comb in combs) wet += comb.process(input)
        wet *= combSum
        for (allPass in allPasses) wet = allPass.process(wet)
        return sample * (1 - mix) + wet * open * mix
    }

    private fun tap(samplesBack: Int): Double {
        var read = preDelayWrite - samplesBack
        if (read < 0) read += preDelay.size
        return preDelay[read]
    }

    /** Lines open, the pre-delay where it is wanted: after a reset, or while nothing of the hall is heard. */
    private fun openAtOnce() {
        gate = rampSamples
        preDelayFrom = preDelayWanted
        preDelayTo = preDelayWanted
        preDelayFade = 0
    }

    /**
     * Another space is other walls: new delay lines. The old tail has been faded out over the smoothing time before its
     * lines are dropped ([gate]), and the new ones start from silence — the tail is cut, but without a click.
     */
    private fun build(newSpace: ReverbSpace) {
        space = newSpace
        val scale = sizeOf(newSpace) * sampleRate / TUNED_AT
        combs = Array(COMB_TUNINGS.size) { Comb((COMB_TUNINGS[it] * scale).roundToInt()).apply { tune(decaySec, damping, sampleRate) } }
        allPasses = Array(ALL_PASS_TUNINGS.size) { AllPass((ALL_PASS_TUNINGS[it] * scale).roundToInt()) }
    }

    private val combSum = 1 / sqrt(COMB_TUNINGS.size.toDouble())

    private class Comb(length: Int) {
        private val buffer = DoubleArray(length.coerceAtLeast(1))
        private var index = 0
        private var feedback = 0.0
        private var damping = 0.0
        private var inputGain = 1.0
        private var filtered = 0.0

        fun tune(decaySec: Double, damping: Double, sampleRate: Int) {
            // 60 dB down after decaySec: the loop is passed decaySec / loopTime times.
            feedback = 10.0.pow(-3.0 * buffer.size / (decaySec * sampleRate))
            this.damping = damping
            inputGain = sqrt(1 - feedback * feedback)
        }

        fun clear() {
            buffer.fill(0.0)
            filtered = 0.0
        }

        fun process(sample: Double): Double {
            val out = buffer[index]
            filtered = out * (1 - damping) + filtered * damping
            if (filtered > -DENORMAL && filtered < DENORMAL) filtered = 0.0
            buffer[index] = sample * inputGain + filtered * feedback
            if (++index == buffer.size) index = 0
            return out
        }
    }

    private class AllPass(length: Int) {
        private val buffer = DoubleArray(length.coerceAtLeast(1))
        private var index = 0

        fun clear() = buffer.fill(0.0)

        fun process(sample: Double): Double {
            val delayed = buffer[index]
            buffer[index] = sample + delayed * ALL_PASS_FEEDBACK
            if (++index == buffer.size) index = 0
            return delayed - sample
        }
    }

    private companion object {
        /** Delay lengths in samples at 44.1 kHz, mutually prime so that their echoes never line up (Freeverb). */
        val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALL_PASS_TUNINGS = intArrayOf(556, 441, 341, 225)
        const val TUNED_AT = 44_100.0
        const val ALL_PASS_FEEDBACK = 0.5
        const val MS_PER_SECOND = 1_000.0
        const val DENORMAL = 1e-30
        const val IDLE_BELOW = 1e-5

        /** How far apart the walls are: what makes a room a room before the tail is even heard. */
        fun sizeOf(space: ReverbSpace): Double = when (space) {
            ReverbSpace.ROOM -> 0.6
            ReverbSpace.HALL -> 1.0
            ReverbSpace.CATHEDRAL -> 1.5
        }
    }
}
