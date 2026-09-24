package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.session.HistoryWeeks
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.yearMonth

/** Sums and derived figures of the practice screen (spec 3.12, 5.6). Pure functions of the entries. */
object PracticeStats {
    private const val DAYS_PER_WEEK = 7
    private const val NOON_HOUR = 12

    /** Total time per day; days without practice are absent. */
    fun dayTotals(entries: List<PracticeEntry>): Map<LocalDate, Long> =
        entries.groupBy { it.date }.mapValues { (_, day) -> day.sumOf { it.durationMs } }

    /** Time on the calendar week (Monday to Sunday) that contains [today]. */
    fun weekTotal(totals: Map<LocalDate, Long>, today: LocalDate): Long {
        val start = HistoryWeeks.weekStartOf(today)
        val end = start.plus(1, DateTimeUnit.WEEK)
        return totals.entries.sumOf { (date, ms) -> if (date >= start && date < end) ms else 0L }
    }

    fun monthTotal(totals: Map<LocalDate, Long>, month: YearMonth): Long =
        totals.entries.sumOf { (date, ms) -> if (date.yearMonth == month) ms else 0L }

    /**
     * Consecutive days with practice ending today, or yesterday when today has none yet: a day
     * that is not over cannot break the streak (spec 5.6).
     */
    fun streak(totals: Map<LocalDate, Long>, today: LocalDate): Int {
        var day = if (practised(totals, today)) today else today.minus(1, DateTimeUnit.DAY)
        var count = 0
        while (practised(totals, day)) {
            count++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /** 0 for a day without practice, then 1 to 4 by the thresholds of [PracticeConfig.fillLevelMinutes]. */
    fun fillLevel(totalMs: Long, config: PracticeConfig): Int {
        if (totalMs <= 0) return 0
        val minutes = totalMs / PracticeConfig.MS_PER_MINUTE
        return 1 + config.fillLevelMinutes.count { minutes >= it }
    }

    /** Days of [month] on a Monday-first grid; the cells before the 1st and after the last day are null. */
    fun calendarCells(month: YearMonth): List<LocalDate?> {
        val first = month.firstDay
        val leading = HistoryWeeks.weekStartOf(first).daysUntil(first)
        val days = List(month.numberOfDays) { first.plus(it, DateTimeUnit.DAY) }
        val cells: List<LocalDate?> = List(leading) { null } + days
        val trailing = (DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
        return cells + List(trailing) { null }
    }

    /** The nominal start of a manual entry: noon of its day, which carries no meaning of its own. */
    fun manualStartOf(date: LocalDate, zone: TimeZone): Long =
        date.atTime(NOON_HOUR, 0).toInstant(zone).toEpochMilliseconds()

    private fun practised(totals: Map<LocalDate, Long>, day: LocalDate) = (totals[day] ?: 0L) > 0L
}
