package com.example.violintuner.core.domain.practice

import com.example.violintuner.core.domain.session.HistoryWeeks
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
        val end = start.plusWeeks(1)
        return totals.entries.sumOf { (date, ms) -> if (date >= start && date < end) ms else 0L }
    }

    fun monthTotal(totals: Map<LocalDate, Long>, month: YearMonth): Long =
        totals.entries.sumOf { (date, ms) -> if (YearMonth.from(date) == month) ms else 0L }

    /**
     * Consecutive days with practice ending today, or yesterday when today has none yet: a day
     * that is not over cannot break the streak (spec 5.6).
     */
    fun streak(totals: Map<LocalDate, Long>, today: LocalDate): Int {
        var day = if (practised(totals, today)) today else today.minusDays(1)
        var count = 0
        while (practised(totals, day)) {
            count++
            day = day.minusDays(1)
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
        val first = month.atDay(1)
        val leading = ChronoUnit.DAYS.between(HistoryWeeks.weekStartOf(first), first).toInt()
        val days = List(month.lengthOfMonth()) { month.atDay(it + 1) }
        val cells: List<LocalDate?> = List(leading) { null } + days
        val trailing = (DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
        return cells + List(trailing) { null }
    }

    /** The nominal start of a manual entry: noon of its day, which carries no meaning of its own. */
    fun manualStartOf(date: LocalDate, zone: ZoneId): Long =
        date.atTime(NOON_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    private fun practised(totals: Map<LocalDate, Long>, day: LocalDate) = (totals[day] ?: 0L) > 0L
}
