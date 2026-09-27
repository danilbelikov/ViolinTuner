package com.violinjourney.app.core.domain

/**
 * Sliding-window median; rejects isolated outliers such as one-frame octave errors. The window is a ring of
 * plain doubles sorted in place on every frame: a handful of values, no boxing and nothing allocated.
 */
class MedianFilter(private val window: Int) {
    private val ring = DoubleArray(window)
    private val sorted = DoubleArray(window)
    private var next = 0
    private var count = 0

    init {
        require(window > 0) { "window must be positive" }
    }

    fun add(value: Double): Double {
        ring[next] = value
        next = (next + 1) % window
        if (count < window) count++
        // the oldest value is overwritten, so the ring holds exactly the last [count]; their order does not matter
        for (i in 0 until count) {
            val v = ring[i]
            var j = i - 1
            while (j >= 0 && sorted[j] > v) {
                sorted[j + 1] = sorted[j]
                j--
            }
            sorted[j + 1] = v
        }
        val mid = count / 2
        return if (count % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun reset() {
        next = 0
        count = 0
    }
}

/** Exponential moving average; the first sample initializes the state. */
class EmaFilter(private val alpha: Double) {
    private var state: Double? = null

    init {
        require(alpha > 0 && alpha <= 1) { "alpha must be in (0, 1]" }
    }

    fun add(value: Double): Double {
        val next = state?.let { it + alpha * (value - it) } ?: value
        state = next
        return next
    }

    fun reset() {
        state = null
    }
}

/** Median followed by EMA over fractional MIDI (spec 5.3). */
class PitchSmoother(config: IntonationConfig) {
    private val median = MedianFilter(config.medianWindow)
    private val ema = EmaFilter(config.emaAlpha)

    fun add(fractionalMidi: Double): Double = ema.add(median.add(fractionalMidi))

    fun reset() {
        median.reset()
        ema.reset()
    }
}
