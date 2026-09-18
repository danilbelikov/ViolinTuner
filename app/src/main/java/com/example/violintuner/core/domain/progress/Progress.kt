package com.example.violintuner.core.domain.progress

import com.example.violintuner.core.domain.practice.PracticeEntry
import com.example.violintuner.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR

/** Where a total practice time stands among the levels (spec 5.7). */
data class LevelProgress(
    /** 1-based. */
    val level: Int,
    /** Null on the last level. */
    val nextLevel: Int?,
    /** Share of the way from this level's threshold to the next one, 0..1; 1 on the last level. */
    val fraction: Float,
    /** Time left to the next level; null on the last one. */
    val toNextMs: Long?,
)

/** Levels and marks as pure functions of the total time; nothing here is stored. */
object Progress {
    /** Progress is the sum of every saved practice, manual ones included (spec 5.7). */
    fun totalMs(entries: List<PracticeEntry>): Long = entries.sumOf { it.durationMs }

    fun levelOf(totalMs: Long, config: ProgressConfig): LevelProgress {
        val thresholds = config.levelThresholdHours.map { it * MS_PER_HOUR }
        val level = thresholds.count { it <= totalMs }.coerceAtLeast(1)
        if (level == thresholds.size) return LevelProgress(level, nextLevel = null, fraction = 1f, toNextMs = null)
        val from = thresholds[level - 1]
        val to = thresholds[level]
        val done = (totalMs - from).coerceAtLeast(0)
        return LevelProgress(
            level = level,
            nextLevel = level + 1,
            fraction = (done.toDouble() / (to - from)).toFloat(),
            toNextMs = to - from - done,
        )
    }

    /** The lowest mark among [ProgressConfig.trophyHours] that is not in [awardedHours]; null when all are taken. */
    fun nextTrophyHours(awardedHours: Set<Int>, config: ProgressConfig): Int? =
        config.trophyHours.firstOrNull { it !in awardedHours }

    /** Time left to the mark; zero once it is reached. */
    fun remainingMs(totalMs: Long, markHours: Int): Long = (markHours * MS_PER_HOUR - totalMs).coerceAtLeast(0)

    /**
     * True for a mark that is shown as a horizon rather than a goal: not taken, at or past
     * [ProgressConfig.farTrophyHours] and not the next one to take.
     */
    fun isFar(markHours: Int, awardedHours: Set<Int>, config: ProgressConfig): Boolean =
        markHours !in awardedHours &&
            markHours >= config.farTrophyHours &&
            markHours != nextTrophyHours(awardedHours, config)
}
