package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.domain.journey.TaktSources
import com.violinjourney.app.core.domain.progress.LevelProgress
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import kotlinx.datetime.LocalDate

/** Where the road stands after the takts of the practice (spec 3.31). */
sealed interface RecapRoad {
    /** The intro has not been seen: takts wait for the journey to begin at home. */
    data object NotStarted : RecapRoad

    /** Nothing more is drawn after the current stop: the takts wait in the purse. */
    data class RouteDone(val balance: Long) : RecapRoad

    /**
     * The next leg, [nextIndex] on the route, costs [price]; the bar grows from [balanceBefore] to
     * [balanceAfter]. Enough when [balanceAfter] reaches the price.
     */
    data class Leg(val nextIndex: Int, val price: Int, val balanceBefore: Long, val balanceAfter: Long) : RecapRoad {
        val enough: Boolean get() = balanceAfter >= price
        val missing: Long get() = (price - balanceAfter).coerceAtLeast(0)
    }
}

/** «Занятие сохранено» (spec 3.31): what the practice just saved earned and changed. Built once, never stored. */
data class PracticeRecap(
    val durationMs: Long,
    /** All the time of the practice's day; null when this practice is the day's only one. */
    val dayTotalMs: Long?,
    val takts: Int,
    val sources: TaktSources,
    val road: RecapRoad,
    val streakDays: Int,
    /** The streak grew by the practice: it is longer with it than without it (spec 5.24). */
    val streakExtended: Boolean,
    val levelBefore: LevelProgress,
    val levelAfter: LevelProgress,
) {
    val levelUp: Boolean get() = levelAfter.level > levelBefore.level
}

object RecapRules {
    /**
     * [earning] is the row the save has just written and [practice] the entry it stored; [entries] and
     * [journey] are read after it, so they already hold the practice and its takts — «before» is them
     * minus the practice (spec 5.24). Without [practice] its day is guessed as that of the latest timed
     * entry — a fallback only: a clock set back or a copy restored with entries «from the future» fool it.
     */
    fun of(
        earning: TaktEarning,
        entries: List<PracticeEntry>,
        journey: JourneyProgress,
        today: LocalDate,
        journeyConfig: JourneyConfig,
        progressConfig: ProgressConfig,
        practice: PracticeEntry? = null,
    ): PracticeRecap {
        val totals = PracticeStats.dayTotals(entries)
        val date = practice?.date ?: entries.filter { !it.manual }.maxByOrNull { it.startedAtEpochMs }?.date ?: today
        val dayTotal = totals[date] ?: earning.durationMs
        val total = Progress.totalMs(entries)
        // the days without this practice: its own day less its length, and gone if nothing else was played on it
        val totalsBefore = totals.toMutableMap().apply {
            val left = dayTotal - earning.durationMs
            if (left > 0) put(date, left) else remove(date)
        }
        val streak = PracticeStats.streak(totals, today)
        return PracticeRecap(
            durationMs = earning.durationMs,
            dayTotalMs = dayTotal.takeIf { it > earning.durationMs },
            takts = earning.takts,
            sources = JourneyRules.taktsBySource(earning, journeyConfig),
            road = roadOf(journey, earning.takts),
            streakDays = streak,
            // an old forgotten practice saved days later grows no streak: the streak ends today or yesterday (spec 5.6)
            streakExtended = streak > PracticeStats.streak(totalsBefore, today),
            levelBefore = Progress.levelOf((total - earning.durationMs).coerceAtLeast(0), progressConfig),
            levelAfter = Progress.levelOf(total, progressConfig),
        )
    }

    fun roadOf(journey: JourneyProgress, takts: Int): RecapRoad {
        if (!journey.started) return RecapRoad.NotStarted
        val next = JourneyRules.next(journey) ?: return RecapRoad.RouteDone(journey.balance)
        return RecapRoad.Leg(
            nextIndex = JourneyRoute.indexOf(next.id),
            price = next.price,
            balanceBefore = (journey.balance - takts).coerceAtLeast(0),
            balanceAfter = journey.balance,
        )
    }
}
