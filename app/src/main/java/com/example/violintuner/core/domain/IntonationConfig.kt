package com.example.violintuner.core.domain

import kotlin.math.pow

/**
 * Every tunable number of the intonation domain, with starting values from docs/spec.md
 * (sections 5 and 3.5). Nothing else in the code base hard-codes these.
 */
data class IntonationConfig(
    /** Concert pitch reference; user-adjustable in v2. */
    val a4Hz: Double = 440.0,

    // Zones (spec 5.2, 5.3)
    val toleranceCents: Double = 8.0,
    val nearCents: Double = 20.0,
    val hysteresisCents: Double = 1.5,

    // Smoothing and stability (spec 5.3)
    val medianWindow: Int = 5,
    val emaAlpha: Double = 0.3,
    val noteLockMs: Long = 100,
    val pitchGapToleranceMs: Long = 100,
    val holdFillMs: Long = 2_000,
    val zoneCrossfadeMs: Int = 150,

    // Signal gating (spec 5.1)
    val silenceTimeoutMs: Long = 300,
    val noisyTimeoutMs: Long = 1_000,
    val silenceRmsDbfs: Double = -45.0,
    val clarityThreshold: Double = 0.85,

    // Range of interest: G3 − 350 cents … E7 + 50 cents (spec 5.1)
    val lowestMidi: Int = 55,
    val highestMidi: Int = 100,
    val rangeMarginBelowCents: Double = 350.0,
    val rangeMarginAboveCents: Double = 50.0,

    // Open-string snapping (spec 3.5, 5.4)
    val stringSnapMarginCents: Double = 350.0,

    // Audio framing (spec 5.1)
    val sampleRateHz: Int = 44_100,
    val hopSizeSamples: Int = 512,
) {
    /** Silence threshold as linear RMS, full scale = 1.0. */
    val silenceRms: Double
        get() = 10.0.pow(silenceRmsDbfs / DB_PER_AMPLITUDE_DECADE)

    private companion object {
        const val DB_PER_AMPLITUDE_DECADE = 20.0
    }
}
