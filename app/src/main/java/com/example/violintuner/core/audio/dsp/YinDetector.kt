package com.example.violintuner.core.audio.dsp

import com.example.violintuner.core.domain.IntonationConfig

/**
 * YIN (de Cheveigné & Kawahara, 2002) with the candidate rule and refinement of spec 5.1:
 * among the dips of the normalized difference function, the first one that is within
 * [IntonationConfig.octaveDipMargin] of the deepest wins, and it is a pitch only if it is below
 * the threshold. Plain YIN takes the first dip under the threshold outright and jumps an
 * octave up on a G string with strong even harmonics.
 */
class YinDetector(private val config: IntonationConfig = IntonationConfig()) : PitchDetector {
    private var samples = DoubleArray(0)
    private var difference = DoubleArray(0)
    private var normalized = DoubleArray(0)
    private val candidateLags = DoubleArray(MAX_CANDIDATES)
    private val candidateDepths = DoubleArray(MAX_CANDIDATES)

    override fun detect(window: FloatArray, sampleRateHz: Int): PitchEstimate {
        val lags = LagRange(config, sampleRateHz)
        val size = lags.top + 1
        require(window.size > size) { "window of ${window.size} is too short for lag ${lags.top}" }
        ensureBuffers(window.size, size)
        removeDc(window, samples)

        computeDifference(size, integration = window.size - lags.top)
        if (!normalize(size)) return PitchEstimate(freqHz = null, clarity = 0.0)

        // Candidates are all dips, not only those below the threshold: with a noisy tone the
        // dips at τ and 2τ hover around the threshold together, and thresholding first would
        // let 2τ win whenever τ lands a hair above it - a confident octave error.
        var roughDeepest = Double.MAX_VALUE
        for (lag in lags.min..lags.max) {
            if (isDip(lag) && normalized[lag] < roughDeepest) roughDeepest = normalized[lag]
        }
        if (roughDeepest == Double.MAX_VALUE) return PitchEstimate(freqHz = null, clarity = 0.0)

        // Refinement is the costly part, so only dips that can still matter get refined.
        val worthRefining = roughDeepest + 2 * config.octaveDipMargin
        var candidateCount = 0
        var deepest = Double.MAX_VALUE
        for (lag in lags.min..lags.max) {
            if (!isDip(lag) || normalized[lag] > worthRefining) continue
            if (candidateCount == candidateLags.size) break
            val refinedLag = refineLag(size, lag)
            val depth = LagSearch.interpolate(normalized, size, refinedLag)
            candidateLags[candidateCount] = refinedLag
            candidateDepths[candidateCount] = depth
            candidateCount++
            if (depth < deepest) deepest = depth
        }
        for (i in 0 until candidateCount) {
            if (candidateDepths[i] > deepest + config.octaveDipMargin) continue
            val depth = candidateDepths[i]
            return PitchEstimate(
                freqHz = if (depth < config.yinThreshold) sampleRateHz / candidateLags[i] else null,
                clarity = (1 - depth).coerceIn(0.0, 1.0),
            )
        }
        return PitchEstimate(freqHz = null, clarity = 0.0)
    }

    private fun isDip(lag: Int): Boolean =
        normalized[lag] < normalized[lag - 1] && normalized[lag] <= normalized[lag + 1]

    /**
     * The cumulative-mean normalization tilts the dip towards smaller lags, so the position is
     * taken from the raw difference function whenever it has a minimum at the same lag.
     */
    private fun refineLag(size: Int, lag: Int): Double {
        val rawHasMinimum = difference[lag] <= difference[lag - 1] && difference[lag] <= difference[lag + 1]
        return LagSearch.refine(if (rawHasMinimum) difference else normalized, size, lag, sign = 1)
    }

    private fun computeDifference(size: Int, integration: Int) {
        difference[0] = 0.0
        for (lag in 1 until size) {
            var sum = 0.0
            for (j in 0 until integration) {
                val delta = samples[j] - samples[j + lag]
                sum += delta * delta
            }
            difference[lag] = sum
        }
    }

    /** Cumulative mean normalized difference; false when the window is pure silence. */
    private fun normalize(size: Int): Boolean {
        normalized[0] = 1.0
        var running = 0.0
        for (lag in 1 until size) {
            running += difference[lag]
            normalized[lag] = if (running > 0.0) difference[lag] * lag / running else 1.0
        }
        return running > 0.0
    }

    private fun ensureBuffers(windowSize: Int, lagCount: Int) {
        if (samples.size != windowSize) samples = DoubleArray(windowSize)
        if (difference.size != lagCount) {
            difference = DoubleArray(lagCount)
            normalized = DoubleArray(lagCount)
        }
    }

    private companion object {
        // A period of 16 samples fits about 19 times into the longest lag; noise adds a few.
        const val MAX_CANDIDATES = 64
    }
}
