package com.violinjourney.app.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The day of a screen left open overnight (spec 3.12: «по умолчанию выбран сегодняшний»), and the moments the reminder of «Занятия» is
 * worked out at (spec 3.36.9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * The clock of the reminder of «Занятия» (spec 3.36.9, plan D9): «now» at once, then exactly at the moments it is told — the start
     * of a lesson at 17:00, its end at 17:45, the midnight after — and never in between: it sleeps, it does not poll.
     */
    @Test
    fun `the ticks come at once and again exactly at the moments asked for and never in between`() = runTest {
        val start = Instant.parse("2026-09-28T13:30:00Z") // 16:30 in Moscow
        val clock = TestClock(this, start.toEpochMilliseconds(), TimeZone.of("Europe/Moscow"))
        val lessonStart = Instant.parse("2026-09-28T14:00:00Z")
        val lessonEnd = Instant.parse("2026-09-28T14:45:00Z")
        val midnight = Instant.parse("2026-09-28T21:00:00Z")
        val asked = mutableListOf<Instant>()
        val seen = mutableListOf<Instant>()
        backgroundScope.launch {
            clock.ticksAt { at ->
                asked += at
                listOf(lessonStart, lessonEnd, midnight).firstOrNull { it > at } ?: (midnight + 1.days)
            }.collect { seen += it }
        }
        runCurrent()
        assertEquals(listOf(start), seen, "now at once")

        advanceTimeBy((lessonStart - start).inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(1, seen.size, "nothing before 17:00")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(start, lessonStart), seen, "the start of the lesson")

        advanceTimeBy((midnight - lessonStart).inWholeMilliseconds)
        runCurrent()
        assertEquals(listOf(start, lessonStart, lessonEnd, midnight), seen, "its end, then midnight — and nothing between them")
        assertEquals(seen, asked, "the next moment is asked once a tick, of the moment of that tick")
    }

    /** A next moment that is not after now (a clock set forward under it) lets a millisecond pass, rather than tick on the spot. */
    @Test
    fun `a moment that is not after now lets a millisecond pass`() = runTest {
        val start = Instant.parse("2026-09-28T13:30:00Z")
        val clock = TestClock(this, start.toEpochMilliseconds(), TimeZone.of("Europe/Moscow"))
        val seen = mutableListOf<Instant>()
        backgroundScope.launch { clock.ticksAt { at -> at }.collect { seen += it } }
        runCurrent()
        assertEquals(1, seen.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(start, start + 1.milliseconds), seen)
    }
}
