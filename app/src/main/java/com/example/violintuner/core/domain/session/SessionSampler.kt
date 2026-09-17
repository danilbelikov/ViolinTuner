package com.example.violintuner.core.domain.session

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationReading

/**
 * Folds engine readings into [IntonationConfig.sessionBucketMs] buckets. Time is the frame
 * clock the readings came with, so samples line up with the audio of the same stream.
 *
 * Only fresh measurements count: a reading held on screen through a pitch gap is not data, and
 * a bucket that has nothing else is "no note". If the note changes inside a bucket, the note
 * with more readings wins.
 */
class SessionSampler(private val config: IntonationConfig) {
    private val samples = ArrayList<SessionSample?>()
    private var startMs = -1L
    private var lastMs = -1L
    private var bucket = 0
    private val centsSum = HashMap<Int, Double>()
    private val counts = HashMap<Int, Int>()

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
        bucket++
    }

    private fun currentSample(): SessionSample? {
        val midi = counts.maxByOrNull { it.value }?.key ?: return null
        return SessionSample(midi, centsSum.getValue(midi) / counts.getValue(midi))
    }
}
