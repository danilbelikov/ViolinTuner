package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * How this phone heard this violin in this room, folded into one event per visit to Live rather
 * than one per frame (spec 3.34). This is the only way to learn the silence and clarity thresholds
 * on instruments and rooms that are not the one the app was written in.
 *
 * Nothing here remembers a note or a moment: counts, a peak and a histogram of clarity, so a visit
 * of any length costs the same fixed memory.
 */
class FramePicture(private val toleranceCents: Int, private val a4Hz: Int) {
    private var frames = 0
    private var silent = 0
    private var noisy = 0
    private var active = 0
    private var peakRms = 0.0
    private val clarity = IntArray(CLARITY_BUCKETS)
    private var firstMs = -1L
    private var lastMs = -1L

    fun add(frame: PitchFrame, reading: IntonationReading) {
        frames++
        when (reading) {
            IntonationReading.Silence -> silent++
            IntonationReading.TooNoisy -> noisy++
            is IntonationReading.Active -> active++
        }
        peakRms = maxOf(peakRms, frame.rms)
        clarity[bucketOf(frame.clarity)]++
        if (firstMs < 0) firstMs = frame.tMs
        lastMs = frame.tMs
    }

    /** Null when the visit was too short to hold a picture — there is nothing to learn from it. */
    fun finish(): LiveFrames? {
        val elapsedMs = lastMs - firstMs
        if (frames == 0 || elapsedMs < MIN_VISIT_MS) return null
        return LiveFrames(
            seconds = (elapsedMs / MS_PER_SECOND).toInt(),
            silencePct = percentOf(silent),
            noisyPct = percentOf(noisy),
            activePct = percentOf(active),
            clarityMedian = medianClarity(),
            peakRmsDbfs = peakDbfs(),
            toleranceCents = toleranceCents,
            a4Hz = a4Hz,
        )
    }

    private fun percentOf(count: Int) = (PERCENT * count.toDouble() / frames).roundToInt()

    private fun bucketOf(value: Double) = (value * CLARITY_BUCKETS).toInt().coerceIn(0, CLARITY_BUCKETS - 1)

    /** The middle of the bucket the middle frame fell into: two decimals, which is all a threshold needs. */
    private fun medianClarity(): Double {
        var seen = 0
        for (bucket in clarity.indices) {
            seen += clarity[bucket]
            if (seen * 2 >= frames) return (bucket + 0.5) / CLARITY_BUCKETS
        }
        return 0.0
    }

    /** Digital silence has no logarithm: exact zeros are reported as the floor, not as −∞. */
    private fun peakDbfs() = if (peakRms > 0) (DB_PER_DECADE * log10(peakRms)).roundToInt().coerceAtLeast(FLOOR_DBFS) else FLOOR_DBFS

    private companion object {
        const val MIN_VISIT_MS = 30_000L
        const val MS_PER_SECOND = 1_000L
        const val CLARITY_BUCKETS = 100
        const val PERCENT = 100.0
        const val DB_PER_DECADE = 20.0
        const val FLOOR_DBFS = -120
    }
}
