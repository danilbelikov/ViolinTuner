package com.example.violintuner.feature.repertoire.piece

/**
 * The neutral level indicator of a blind take (spec 3.15, handoff 13d1): the last few moments
 * of loudness as a row of bars, newest on the right. It says "the microphone hears you" and
 * nothing about hitting the notes — there are no frequencies in it, only how loud.
 *
 * Frames come some eighty times a second; a bar stands for [periodMs] and keeps the loudest
 * frame of its period, so the row changes twenty times a second, not eighty. Time comes from
 * the frames; not thread-safe.
 */
class LevelHistory(private val size: Int, private val periodMs: Long) {
    private val bars = ArrayDeque<Float>()
    private var bucketStartTMs: Long? = null
    private var bucketPeak = 0f
    private var snapshot: List<Float> = List(size) { 0f }

    /** The row after this frame: the same list instance until a bar closes, so that equal states stay equal. */
    fun add(tMs: Long, level: Float): List<Float> {
        val start = bucketStartTMs
        if (start == null || tMs < start) {
            bucketStartTMs = tMs
            bucketPeak = level
            return snapshot
        }
        if (tMs - start < periodMs) {
            bucketPeak = maxOf(bucketPeak, level)
            return snapshot
        }
        bars.addLast(bucketPeak.coerceIn(0f, 1f))
        if (bars.size > size) bars.removeFirst()
        bucketStartTMs = tMs
        bucketPeak = level
        snapshot = List(size - bars.size) { 0f } + bars
        return snapshot
    }

    fun reset() {
        bars.clear()
        bucketStartTMs = null
        bucketPeak = 0f
        snapshot = List(size) { 0f }
    }
}
