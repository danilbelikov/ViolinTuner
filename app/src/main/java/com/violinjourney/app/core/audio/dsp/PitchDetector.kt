package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig

/** [freqHz] is null when no periodicity was found; [clarity] is 0..1 either way. */
data class PitchEstimate(val freqHz: Double?, val clarity: Double)

/**
 * Monophonic pitch detector over one analysis window. Implementations reuse internal buffers:
 * not thread-safe, one instance per audio stream.
 */
interface PitchDetector {
    /** [window] holds [IntonationConfig.windowSizeSamples] samples scaled to -1..1. */
    fun detect(window: FloatArray, sampleRateHz: Int): PitchEstimate
}

/** Detectors are stateful and bound to a config: every audio stream creates its own. */
fun interface PitchDetectorFactory {
    fun create(config: IntonationConfig): PitchDetector
}
