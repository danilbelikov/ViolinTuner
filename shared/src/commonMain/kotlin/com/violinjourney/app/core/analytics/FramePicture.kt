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
 * Nothing here remembers a note or a moment: counts, a peak and two histograms, so a visit of any
 * length costs the same fixed memory. What each number answers:
 * - the clarity is that of the frames not quieter than [silenceRms]: those are the frames the clarity
 *   threshold decides about, and the detector does not even run on the quieter ones (spec 5.1);
 * - the floor is the loudness 10 % of the frames stay under — the room while the violin is silent, when
 *   the visit had pauses; with the peak, it is what the silence threshold is learnt from;
 * - the seconds are the time the microphone was heard: a reopened input ([newStretch]) starts its own
 *   clock, and the seconds of the reopening are not counted.
 */
class FramePicture(private val toleranceCents: Int, private val a4Hz: Int, private val silenceRms: Double) {
    private var frames = 0
    private var loud = 0
    private var silent = 0
    private var noisy = 0
    private var active = 0
    private var peakRms = 0.0
    private val clarity = IntArray(CLARITY_BUCKETS)
    private val loudness = IntArray(RMS_BUCKETS)
    private var heardMs = 0L
    private var lastMs = NONE

    fun add(frame: PitchFrame, reading: IntonationReading) {
        frames++
        when (reading) {
            IntonationReading.Silence -> silent++
            IntonationReading.TooNoisy -> noisy++
            is IntonationReading.Active -> active++
        }
        peakRms = maxOf(peakRms, frame.rms)
        loudness[dbfsOf(frame.rms) - FLOOR_DBFS]++
        if (frame.rms >= silenceRms) {
            loud++
            clarity[bucketOf(frame.clarity)]++
        }
        // the time of a stretch goes on frame by frame; a clock that jumps back is a new input and adds nothing
        if (lastMs != NONE && frame.tMs > lastMs) heardMs += frame.tMs - lastMs
        lastMs = frame.tMs
    }

    /** The input was reopened (after a failure): its frames count their time from their own start. */
    fun newStretch() {
        lastMs = NONE
    }

    /** Null when the visit was too short to hold a picture — there is nothing to learn from it. */
    fun finish(): LiveFrames? {
        if (frames == 0 || heardMs < MIN_VISIT_MS) return null
        return LiveFrames(
            seconds = (heardMs / MS_PER_SECOND).toInt(),
            silencePct = percentOf(silent),
            noisyPct = percentOf(noisy),
            activePct = percentOf(active),
            clarityMedian = medianClarity(),
            floorRmsDbfs = floorDbfs(),
            peakRmsDbfs = dbfsOf(peakRms),
            toleranceCents = toleranceCents,
            a4Hz = a4Hz,
        )
    }

    private fun percentOf(count: Int) = (PERCENT * count.toDouble() / frames).roundToInt()

    private fun bucketOf(value: Double) = (value * CLARITY_BUCKETS).toInt().coerceIn(0, CLARITY_BUCKETS - 1)

    /**
     * The middle of the bucket the middle loud frame fell into: two decimals, which is all a threshold needs;
     * 0 when nothing in the visit was louder than silence.
     */
    private fun medianClarity(): Double {
        if (loud == 0) return 0.0
        var seen = 0
        for (bucket in clarity.indices) {
            seen += clarity[bucket]
            if (seen * 2 >= loud) return (bucket + 0.5) / CLARITY_BUCKETS
        }
        return 0.0
    }

    /** The whole decibel at which [FLOOR_SHARE] of the frames of the visit have been counted, from the quietest up. */
    private fun floorDbfs(): Int {
        val wanted = frames * FLOOR_SHARE
        var seen = 0
        for (bucket in loudness.indices) {
            seen += loudness[bucket]
            if (seen >= wanted) return FLOOR_DBFS + bucket
        }
        return 0
    }

    /** Whole decibels; digital silence has no logarithm: exact zeros are the floor, not −∞. */
    private fun dbfsOf(rms: Double) =
        if (rms > 0) (DB_PER_DECADE * log10(rms)).roundToInt().coerceIn(FLOOR_DBFS, 0) else FLOOR_DBFS

    private companion object {
        const val MIN_VISIT_MS = 30_000L
        const val MS_PER_SECOND = 1_000L
        const val NONE = -1L
        const val CLARITY_BUCKETS = 100
        const val PERCENT = 100.0
        const val DB_PER_DECADE = 20.0
        const val FLOOR_DBFS = -120

        /** One bucket per whole decibel from [FLOOR_DBFS] to 0 dBFS. */
        const val RMS_BUCKETS = -FLOOR_DBFS + 1
        const val FLOOR_SHARE = 0.1
    }
}
