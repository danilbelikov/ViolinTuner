package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.assertEquals
import kotlin.test.Test

class PracticeStatsTest {
    private val config = PracticeConfig()
    private val zone: TimeZone = TimeZone.of("Europe/Moscow")

    private fun entry(date: String, minutes: Int, manual: Boolean = false) = PracticeEntry(
        date = LocalDate.parse(date),
        startedAtEpochMs = 0,
        durationMs = minutes * MS_PER_MINUTE,
        manual = manual,
    )

    private fun totals(vararg days: Pair<String, Int>): Map<LocalDate, Long> =
        days.associate { (date, minutes) -> LocalDate.parse(date) to minutes * MS_PER_MINUTE }

    @Test
    fun `day totals add every entry of the day — manual or timed`() {
        val totals = PracticeStats.dayTotals(
            listOf(entry("2026-09-17", 20), entry("2026-09-17", 25, manual = true), entry("2026-09-16", 50)),
        )
        assertEquals(totals("2026-09-17" to 45, "2026-09-16" to 50), totals)
    }

    @Test
    fun `week runs from monday to sunday around today`() {
        // 2026-09-17 is a Thursday; the week is 14–20 September.
        val totals = totals(
            "2026-09-13" to 100, "2026-09-14" to 35, "2026-09-17" to 45, "2026-09-20" to 10, "2026-09-21" to 100,
        )
        assertEquals(90 * MS_PER_MINUTE, PracticeStats.weekTotal(totals, LocalDate.parse("2026-09-17")))
        assertEquals(90 * MS_PER_MINUTE, PracticeStats.weekTotal(totals, LocalDate.parse("2026-09-14")))
        assertEquals(90 * MS_PER_MINUTE, PracticeStats.weekTotal(totals, LocalDate.parse("2026-09-20")))
    }

    @Test
    fun `week crossing a month boundary is still one week`() {
        // 2026-08-31 is a Monday.
        val totals = totals("2026-08-31" to 10, "2026-09-01" to 20, "2026-09-06" to 30, "2026-09-07" to 40)
        assertEquals(60 * MS_PER_MINUTE, PracticeStats.weekTotal(totals, LocalDate.parse("2026-09-02")))
    }

    @Test
    fun `month total takes the calendar month only`() {
        val totals = totals("2026-08-31" to 10, "2026-09-01" to 20, "2026-09-30" to 30, "2026-10-01" to 40)
        assertEquals(50 * MS_PER_MINUTE, PracticeStats.monthTotal(totals, YearMonth(2026, 9)))
    }

    @Test
    fun `brief sample of the design adds up`() {
        val minutes = mapOf(
            1 to 30, 2 to 50, 4 to 100, 5 to 15, 7 to 45, 8 to 70, 9 to 25, 11 to 125, 12 to 40, 13 to 55,
            14 to 35, 15 to 80, 16 to 50, 17 to 45,
        )
        val totals = minutes.entries.associate { (day, m) -> LocalDate(2026, 9, day) to m * MS_PER_MINUTE }
        val today = LocalDate(2026, 9, 17)
        assertEquals(210 * MS_PER_MINUTE, PracticeStats.weekTotal(totals, today))
        assertEquals(765 * MS_PER_MINUTE, PracticeStats.monthTotal(totals, YearMonth(2026, 9)))
        assertEquals(7, PracticeStats.streak(totals, today))
    }

    @Test
    fun `streak counts back from today`() {
        val totals = totals("2026-09-15" to 5, "2026-09-16" to 5, "2026-09-17" to 5)
        assertEquals(3, PracticeStats.streak(totals, LocalDate.parse("2026-09-17")))
    }

    @Test
    fun `today without practice does not break the streak yet`() {
        val totals = totals("2026-09-15" to 5, "2026-09-16" to 5)
        assertEquals(2, PracticeStats.streak(totals, LocalDate.parse("2026-09-17")))
    }

    @Test
    fun `a missed day resets the streak`() {
        val totals = totals("2026-09-13" to 5, "2026-09-14" to 5, "2026-09-16" to 5, "2026-09-17" to 5)
        assertEquals(2, PracticeStats.streak(totals, LocalDate.parse("2026-09-17")))
        assertEquals(0, PracticeStats.streak(totals, LocalDate.parse("2026-09-19")))
    }

    @Test
    fun `a day edited to zero is not part of a streak`() {
        val totals = totals("2026-09-16" to 0, "2026-09-17" to 5)
        assertEquals(1, PracticeStats.streak(totals, LocalDate.parse("2026-09-17")))
        assertEquals(0, PracticeStats.streak(emptyMap(), LocalDate.parse("2026-09-17")))
    }

    @Test
    fun `fill levels switch on the spec thresholds`() {
        val expected = mapOf(
            0 to 0, 1 to 1, 19 to 1, 20 to 2, 44 to 2, 45 to 3, 89 to 3, 90 to 4, 300 to 4,
        )
        for ((minutes, level) in expected) {
            assertEquals(level, PracticeStats.fillLevel(minutes * MS_PER_MINUTE, config), "$minutes min")
        }
        assertEquals(1, PracticeStats.fillLevel(30_000, config), "under a minute still counts")
        assertEquals(1, PracticeStats.fillLevel(20 * MS_PER_MINUTE - 1, config), "19:59 is level 1")
    }

    @Test
    fun `calendar grid starts on monday and is whole weeks`() {
        // September 2026 starts on a Tuesday and ends on a Wednesday.
        val cells = PracticeStats.calendarCells(YearMonth(2026, 9))
        assertEquals(35, cells.size)
        assertEquals(null, cells[0])
        assertEquals(LocalDate(2026, 9, 1), cells[1])
        assertEquals(LocalDate(2026, 9, 30), cells[30])
        assertEquals(List(4) { null }, cells.takeLast(4))
        // June 2026 starts on a Monday: no leading cells.
        assertEquals(LocalDate(2026, 6, 1), PracticeStats.calendarCells(YearMonth(2026, 6))[0])
        // February 2027 is exactly four weeks from Monday to Sunday.
        assertEquals(28, PracticeStats.calendarCells(YearMonth(2027, 2)).size)
    }

    @Test
    fun `practice date is the local date of the start`() {
        // 2026-09-17 23:30 in Moscow is 20:30 UTC.
        val start = LocalDate.parse("2026-09-17").atTime(23, 30).toInstant(zone).toEpochMilliseconds()
        assertEquals(LocalDate.parse("2026-09-17"), practiceDateOf(start, zone))
        assertEquals(LocalDate.parse("2026-09-17"), practiceDateOf(start, TimeZone.of("UTC")))
        assertEquals(LocalDate.parse("2026-09-18"), practiceDateOf(start, TimeZone.of("Asia/Tokyo")))
    }

    @Test
    fun `manual entries start at noon of their day`() {
        val start = PracticeStats.manualStartOf(LocalDate.parse("2026-09-16"), zone)
        assertEquals(LocalDate.parse("2026-09-16"), practiceDateOf(start, zone))
        assertEquals(12, Instant.fromEpochMilliseconds(start).toLocalDateTime(zone).hour)
    }
}
