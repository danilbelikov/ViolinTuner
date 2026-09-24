package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * The [Limiter] for two channels at once (spec 5.25: the violin and the backing summed): one gain, taken
 * from the louder side, for both — so a loud bar of the piano does not push the picture of the stereo
 * sideways. The same look-ahead, the same ceiling and release; the same delay of [latencySamples].
 */
class StereoLimiter(sampleRate: Int, config: SoundConfig) {
    private val ceiling = 10.0.pow(config.limiterCeilingDb / DB_PER_DECADE)
    private val window = (config.limiterLookAheadMs / MS_PER_SECOND * sampleRate).toInt().coerceAtLeast(2)
    private val release = exp(-1.0 / (config.limiterReleaseMs / MS_PER_SECOND * sampleRate))

    val latencySamples: Int = window - 1

    private val delayLeft = FloatArray(window)
    private val delayRight = FloatArray(window)
    private val wanted = DoubleArray(window) { 1.0 }
    private val minima = DoubleArray(window) { 1.0 }
    private var position = 0
    private var minimaSum = window.toDouble()
    private var gain = 1.0
    private var lowest = 1.0

    fun reset() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        wanted.fill(1.0)
        minima.fill(1.0)
        minimaSum = window.toDouble()
        gain = 1.0
        lowest = 1.0
    }

    /** [count] interleaved frames of [frames], in place. */
    fun process(frames: FloatArray, count: Int) {
        for (i in 0 until count) {
            val l = frames[2 * i]
            val r = frames[2 * i + 1]
            val level = max(abs(l), abs(r)).toDouble()
            val leaving = wanted[position]
            val entering = if (level > ceiling) ceiling / level else 1.0
            wanted[position] = entering
            if (entering <= lowest) {
                lowest = entering
            } else if (leaving <= lowest) {
                lowest = 1.0
                for (value in wanted) if (value < lowest) lowest = value
            }
            minimaSum += lowest - minima[position]
            minima[position] = lowest
            val averaged = minimaSum / window
            gain = if (averaged < gain) averaged else averaged + (gain - averaged) * release

            val next = if (position + 1 == window) 0 else position + 1
            frames[2 * i] = (delayLeft[next] * gain).toFloat()
            frames[2 * i + 1] = (delayRight[next] * gain).toFloat()
            delayLeft[position] = l
            delayRight[position] = r
            position = next
        }
    }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val DB_PER_DECADE = 20.0
    }
}
