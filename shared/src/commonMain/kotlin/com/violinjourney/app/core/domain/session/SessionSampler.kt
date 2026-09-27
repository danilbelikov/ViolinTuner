package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading

/**
 * Folds engine readings into [IntonationConfig.sessionBucketMs] buckets. Time is the frame
 * clock the readings came with, so samples line up with the audio of the same stream.
 *
 * Only fresh measurements count: a reading held on screen through a pitch gap is not data, and
 * a bucket that has nothing else is "no note". If the note changes inside a bucket, the note
 * with more readings wins; a tie goes to the note heard last — what the player was seeing then —
 * and not to whichever the map happens to list first, which differs between Android and iOS.
 */
class SessionSampler(private val config: IntonationConfig) {
    private val samples = ArrayList<SessionSample?>()
    private var startMs = -1L
    private var lastMs = -1L
    private var bucket = 0
    private val centsSum = HashMap<Int, Double>()
    private val counts = HashMap<Int, Int>()

    /** When each note of the bucket was last heard, by reading: the tie-break. */
    private val lastHeard = HashMap<Int, Int>()
    private var readingIndex = 0

    /** Time covered so far. */
    val durationMs: Long
        get() = if (startMs < 0) 0 else lastMs - startMs

    fun add(tMs: Long, reading: IntonationReading) {
        if (startMs < 0) startMs = tMs
        lastMs = tMs
        val index = ((tMs - startMs) / config.sessionBucketMs).toInt()
        while (bucket < index) closeBucket()
        if (reading is IntonationReading.Active && !reading.held) {
            val midi = reading.note.midi
            centsSum[midi] = (centsSum[midi] ?: 0.0) + reading.cents
            counts[midi] = (counts[midi] ?: 0) + 1
            lastHeard[midi] = readingIndex++
        }
    }

    /** Buckets that are complete; grows only, so callers can remember how far they have read. */
    val closedSamples: List<SessionSample?>
        get() = samples

    /** Samples of every bucket touched so far, the running one included. */
    fun snapshot(): List<SessionSample?> = samples + currentSample()

    private fun closeBucket() {
        samples += currentSample()
        centsSum.clear()
        counts.clear()
        lastHeard.clear()
        bucket++
    }

    private fun currentSample(): SessionSample? {
        val midi = counts.keys.maxWithOrNull(compareBy<Int> { counts.getValue(it) }.thenBy { lastHeard.getValue(it) }) ?: return null
        return SessionSample(midi, centsSum.getValue(midi) / counts.getValue(midi))
    }
}
