package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig

/**
 * McLeod Pitch Method (McLeod & Wyvill, 2005): normalized square difference function, one
 * "key maximum" per positive lobe, and the first of them that reaches
 * [IntonationConfig.mpmPeakRatio] of the highest wins. Clarity is the height of that peak.
 */
class MpmDetector(private val config: IntonationConfig = IntonationConfig()) : PitchDetector {
    private var samples = DoubleArray(0)
    private var nsdf = DoubleArray(0)

    override fun detect(window: FloatArray, sampleRateHz: Int): PitchEstimate {
        val lags = LagRange(config, sampleRateHz)
        val size = lags.top + 1
        require(window.size > size) { "window of ${window.size} is too short for lag ${lags.top}" }
        if (samples.size != window.size) samples = DoubleArray(window.size)
        if (nsdf.size != size) nsdf = DoubleArray(size)
        removeDc(window, samples)
        if (!computeNsdf(size)) return PitchEstimate(freqHz = null, clarity = 0.0)

        var bestLag = 0.0
        var bestHeight = 0.0
        var highest = 0.0
        for (pass in 0..1) {
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
                val refinedLag = LagSearch.refine(nsdf, size, peak, sign = -1)
                val height = LagSearch.interpolate(nsdf, size, refinedLag)
                if (pass == 0) {
                    if (height > highest) highest = height
                } else if (height >= config.mpmPeakRatio * highest) {
                    bestLag = refinedLag
                    bestHeight = height
                    break
                }
            }
            if (highest <= 0.0) break
        }
        if (bestLag == 0.0) return PitchEstimate(freqHz = null, clarity = 0.0)
        return PitchEstimate(freqHz = sampleRateHz / bestLag, clarity = bestHeight.coerceIn(0.0, 1.0))
    }

    /** n'(τ) = 2·r(τ) / m(τ) over the shrinking overlap; false when the window is silent. */
    private fun computeNsdf(size: Int): Boolean {
        var any = false
        for (lag in 0 until size) {
            var correlation = 0.0
            var energy = 0.0
            for (j in 0 until samples.size - lag) {
                val a = samples[j]
                val b = samples[j + lag]
                correlation += a * b
                energy += a * a + b * b
            }
            nsdf[lag] = if (energy > 0.0) 2 * correlation / energy else 0.0
            if (energy > 0.0) any = true
        }
        return any
    }
}
