package com.violinjourney.app.core.domain

/** Sliding-window median; rejects isolated outliers such as one-frame octave errors. */
class MedianFilter(private val window: Int) {
    private val values = ArrayDeque<Double>(window)

    init {
        require(window > 0) { "window must be positive" }
    }

    fun add(value: Double): Double {
        if (values.size == window) values.removeFirst()
        values.addLast(value)
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun reset() = values.clear()
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
