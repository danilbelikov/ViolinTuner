package com.violinjourney.app.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The day of a screen left open overnight (spec 3.12: «по умолчанию выбран сегодняшний»). */
class DateTickerTest {
    /** Wall time that follows the virtual time of the test, from [startMs]. */
    private class TestClock(private val scope: TestScope, private val startMs: Long, override val zone: TimeZone) : WallClock {
        override fun instant(): Instant = Instant.fromEpochMilliseconds(startMs + scope.testScheduler.currentTime)
    }

    @Test
    fun `the date comes at once and the next one exactly at midnight`() = runTest {
        // 23:59:30 in Moscow
        val clock = TestClock(this, Instant.parse("2026-09-30T20:59:30Z").toEpochMilliseconds(), TimeZone.of("Europe/Moscow"))
        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.dates().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 30)), seen, "today at once")

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, seen.size, "nothing new before midnight")

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 30), LocalDate(2026, 10, 1)), seen, "tomorrow at midnight — once")
    }

    @Test
    fun `the night of the clock change is one day long`() = runTest {
        // Berlin 29 March 2026: 02:00 becomes 03:00 — the night is an hour shorter, midnight is still midnight
        val clock = TestClock(this, Instant.parse("2026-03-28T23:00:00Z").toEpochMilliseconds(), TimeZone.of("Europe/Berlin"))
        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.dates().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 3, 29)), seen)

        advanceTimeBy(23 * 3_600_000L - 1)
        runCurrent()
        assertEquals(1, seen.size, "a day of 23 hours has not ended yet")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(LocalDate(2026, 3, 30), seen.last())
    }
}
