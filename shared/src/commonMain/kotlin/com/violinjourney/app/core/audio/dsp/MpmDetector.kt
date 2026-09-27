package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig

/**
 * McLeod Pitch Method (McLeod & Wyvill, 2005): normalized square difference function, one
 * "key maximum" per positive lobe, and the first of them that reaches
 * [IntonationConfig.mpmPeakRatio] of the highest wins. Clarity is the height of that peak.
 *
 * Refinement is the costly part, so each key maximum is refined once, and a frame whose highest
 * key maximum is far below [IntonationConfig.clarityThreshold] even at its integer lag is given up
 * at once ([ROUGH_MARGIN]): noise, a bow scraping and a room talking cost little.
 */
class MpmDetector(private val config: IntonationConfig = IntonationConfig()) : PitchDetector {
    private var samples = DoubleArray(0)
    private var nsdf = DoubleArray(0)
    private var keyPeaks = IntArray(0)
    private var refinedLags = DoubleArray(0)
    private var refinedHeights = DoubleArray(0)
    private var lagRange: LagRange? = null
    private var lagRate = 0

    override fun detect(window: FloatArray, sampleRateHz: Int): PitchEstimate {
        val lags = lagsFor(sampleRateHz)
        val size = lags.top + 1
        require(window.size > size) { "window of ${window.size} is too short for lag ${lags.top}" }
        if (samples.size != window.size) samples = DoubleArray(window.size)
        if (nsdf.size != size) {
            nsdf = DoubleArray(size)
            keyPeaks = IntArray(size)
            refinedLags = DoubleArray(size)
            refinedHeights = DoubleArray(size)
        }
        removeDc(window, samples)
        if (!computeNsdf(size)) return PitchEstimate(freqHz = null, clarity = 0.0)

        val nsdf = nsdf
        val peaks = keyPeaks
        var count = 0
        var roughHighest = 0.0
        var lag = 1
        while (lag < size && nsdf[lag] > 0) lag++ // the lobe around lag 0 is not a period
        while (lag <= lags.max) {
            if (nsdf[lag] <= 0) {
                lag++
                continue
            }
            var peak = lag
            while (lag <= lags.max && nsdf[lag] > 0) {
                if (nsdf[lag] > nsdf[peak]) peak = lag
                lag++
            }
            val isKeyMaximum = peak >= lags.min &&
                nsdf[peak] >= nsdf[peak - 1] && nsdf[peak] >= nsdf[peak + 1]
            if (!isKeyMaximum) continue
            peaks[count++] = peak
            if (nsdf[peak] > roughHighest) roughHighest = nsdf[peak]
        }
        if (count == 0) return PitchEstimate(freqHz = null, clarity = 0.0)

        if (roughHighest < config.clarityThreshold - ROUGH_MARGIN) {
            // No refinement can lift it to the threshold: not a pitch, and the clarity is the winner's at its integer lag.
            val floor = config.mpmPeakRatio * roughHighest
            var first = 0
            while (nsdf[peaks[first]] < floor) first++
            return PitchEstimate(freqHz = null, clarity = nsdf[peaks[first]].coerceIn(0.0, 1.0))
        }

        var highest = 0.0
        for (i in 0 until count) {
            val refinedLag = LagSearch.refine(nsdf, size, peaks[i], sign = -1)
            val height = LagSearch.interpolate(nsdf, size, refinedLag)
            refinedLags[i] = refinedLag
            refinedHeights[i] = height
            if (height > highest) highest = height
        }
        if (highest <= 0.0) return PitchEstimate(freqHz = null, clarity = 0.0)
        val floor = config.mpmPeakRatio * highest
        for (i in 0 until count) {
            if (refinedHeights[i] >= floor) {
                return PitchEstimate(freqHz = sampleRateHz / refinedLags[i], clarity = refinedHeights[i].coerceIn(0.0, 1.0))
            }
        }
        return PitchEstimate(freqHz = null, clarity = 0.0)
    }

    /** The bounds of the lags, worked out once per sample rate rather than on every frame. */
    private fun lagsFor(sampleRateHz: Int): LagRange {
        lagRange?.takeIf { lagRate == sampleRateHz }?.let { return it }
        return LagRange(config, sampleRateHz).also {
            lagRange = it
            lagRate = sampleRateHz
        }
    }

    /**
     * n'(τ) = 2·r(τ) / m(τ) over the shrinking overlap; false when the window is silent. The energy m(τ) is a
     * running sum — m(τ+1) = m(τ) − x[n−1−τ]² − x[τ]² — and the correlation is summed four ways at once, so the
     * additions do not wait for each other.
     */
    private fun computeNsdf(size: Int): Boolean {
        val x = samples
        val out = nsdf
        val n = x.size
        var squares = 0.0
        for (value in x) squares += value * value
        val m0 = 2 * squares
        if (m0 <= 0.0) {
            out.fill(0.0, 0, size)
            return false
        }
        val floor = m0 * ENERGY_FLOOR
        var energy = m0
        for (lag in 0 until size) {
            val limit = n - lag
            var c0 = 0.0
            var c1 = 0.0
            var c2 = 0.0
            var c3 = 0.0
            var j = 0
            while (j + UNROLL <= limit) {
                c0 += x[j] * x[j + lag]
                c1 += x[j + 1] * x[j + 1 + lag]
                c2 += x[j + 2] * x[j + 2 + lag]
                c3 += x[j + 3] * x[j + 3 + lag]
                j += UNROLL
            }
            while (j < limit) {
                c0 += x[j] * x[j + lag]
                j++
            }
            val correlation = (c0 + c1) + (c2 + c3)
            out[lag] = if (energy > floor) 2 * correlation / energy else 0.0
            val leaving = x[n - 1 - lag]
            val entering = x[lag]
            energy -= leaving * leaving + entering * entering
        }
        return true
    }

    private companion object {
        /**
         * How far below the clarity threshold the highest key maximum may be at its integer lag and still be refined.
         * Lanczos interpolation is exact at integer lags, so refinement only lifts a crest by what lies between two
         * samples: a period of at least 16 samples (E7 + 50 cents at 44.1 kHz) puts the crest within half a sample of
         * one, where even a sawtooth's NSDF is less than 0.09 lower (MpmReferenceTest holds it to the old detector).
         */
        const val ROUGH_MARGIN = 0.1

        /** A share of the whole window's energy below which an overlap counts as silent: the running sum is not exact. */
        const val ENERGY_FLOOR = 1e-12

        const val UNROLL = 4
    }
}
