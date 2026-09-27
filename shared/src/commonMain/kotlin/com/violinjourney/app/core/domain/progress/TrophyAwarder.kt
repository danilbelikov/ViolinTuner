package com.violinjourney.app.core.domain.progress

import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * Gives the trophies the total time has earned. Safe to call on every change of the practice
 * entries: marks that already have a trophy are skipped, and a total that went down takes
 * nothing away (spec 5.7).
 */
class TrophyAwarder(
    private val repository: TrophyRepository,
    private val config: ProgressConfig,
    private val clock: WallClock,
) {
    /**
     * Gives them for as long as it is collected — `AppStartViewModel` follows the entries while the app is open.
     * Trophies are given here rather than where a practice is saved: the entries change from the practice screen,
     * its sheets and the forgotten-practice prompt alike. Giving is idempotent, so the second pass that the new
     * trophies trigger finds nothing to do.
     */
    suspend fun follow(entries: Flow<List<PracticeEntry>>) {
        combine(entries, repository.trophies) { all, given -> Progress.totalMs(all) to given.mapTo(mutableSetOf()) { it.hours } }
            .collect { (totalMs, givenHours) -> award(totalMs, givenHours) }
    }

    suspend fun award(totalMs: Long, awardedHours: Set<Int>) {
        val today = clock.today()
        due(totalMs, awardedHours, config).forEach { repository.award(it, today) }
    }

    companion object {
        /** Marks reached by [totalMs] that have no trophy yet, lowest first. */
        fun due(totalMs: Long, awardedHours: Set<Int>, config: ProgressConfig): List<Int> =
            config.trophyHours.filter { it * MS_PER_HOUR <= totalMs && it !in awardedHours }
    }
}
