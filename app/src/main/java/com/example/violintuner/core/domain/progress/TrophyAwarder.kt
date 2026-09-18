package com.example.violintuner.core.domain.progress

import com.example.violintuner.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Gives the trophies the total time has earned. Safe to call on every change of the practice
 * entries: marks that already have a trophy are skipped, and a total that went down takes
 * nothing away (spec 5.7).
 */
class TrophyAwarder @Inject constructor(
    private val repository: TrophyRepository,
    private val config: ProgressConfig,
    private val clock: Clock,
) {
    suspend fun award(totalMs: Long, awardedHours: Set<Int>) {
        val today = LocalDate.now(clock)
        due(totalMs, awardedHours, config).forEach { repository.award(it, today) }
    }

    companion object {
        /** Marks reached by [totalMs] that have no trophy yet, lowest first. */
        fun due(totalMs: Long, awardedHours: Set<Int>, config: ProgressConfig): List<Int> =
            config.trophyHours.filter { it * MS_PER_HOUR <= totalMs && it !in awardedHours }
    }
}
