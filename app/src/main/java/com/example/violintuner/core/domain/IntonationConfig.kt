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

    // Session recording and analysis (spec 3.9, 5.5)
    val sessionBucketMs: Long = 50,
    val minSegmentMs: Long = 200,
    val minSessionMs: Long = 2_000,
    val maxSessionMs: Long = 3_600_000,
    /** The mini bar of the recording strip is full at this duration and scales beyond it. */
    val recordingBarMinMs: Long = 20_000,
    /** Score and per-string percentages: green from here, amber from the next, red below. */
    val scoreGoodPercent: Int = 75,
    val scoreFairPercent: Int = 55,
    val problemNotesMax: Int = 3,
    /** A mean deviation smaller than this reads as "no bias". */
    val biasNeutralCents: Double = 2.0,
    val sessionPreviewNotes: Int = 8,
    val historyWeeks: Int = 6,
    /** The "month" filter of the history: this many days back, today included. */
    val historyMonthDays: Int = 30,

    // Cents scale on the Live screen: ±range around the target (handoff `sizes`)
    val scaleRangeCents: Double = 50.0,

    // Audio framing (spec 5.1)
    val sampleRateHz: Int = 44_100,
    /** Tried in this order after the device's native rate. */
    val supportedSampleRatesHz: List<Int> = listOf(48_000, 44_100),
    val windowSizeSamples: Int = 2_048,
    val hopSizeSamples: Int = 512,
    /** Exact zeros for this long mean the input is cut off, not quiet (spec 3.4). */
    val digitalSilenceTimeoutMs: Long = 2_000,

    // Pitch detectors (spec 5.1)
    val yinThreshold: Double = 0.15,
    /** A dip this close to the deepest one still counts as a candidate; the first such wins. */
    val octaveDipMargin: Double = 0.05,
    /** MPM: the first NSDF peak at least this fraction of the highest one wins. */
    val mpmPeakRatio: Double = 0.9,
) {
    val minFrequencyHz: Double
        get() = PitchMath.midiToFrequency(
            lowestMidi - rangeMarginBelowCents / PitchMath.CENTS_PER_SEMITONE, a4Hz,
        )

    val maxFrequencyHz: Double
        get() = PitchMath.midiToFrequency(
            highestMidi + rangeMarginAboveCents / PitchMath.CENTS_PER_SEMITONE, a4Hz,
        )

    /** Silence threshold as linear RMS, full scale = 1.0. */
    val silenceRms: Double
        get() = 10.0.pow(silenceRmsDbfs / DB_PER_AMPLITUDE_DECADE)

    private companion object {
        const val DB_PER_AMPLITUDE_DECADE = 20.0
    }
}
