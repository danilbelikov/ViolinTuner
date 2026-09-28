package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.time.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/**
 * The clock of the running practice (spec 3.12, 3.36.3): it ticks on the whole seconds of the practice itself, so a second of the timer
 * of «Занятия» turns when it is due and a minute of «Занятие не закончено» — rounded at k minutes and 30 s — turns on a tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunningPracticeTickerTest {
    /** A whole second of the wall clock. */
    private val base = 1_790_000_000_000L

    /** The wall clock that goes with the virtual time of the test. */
    private fun TestScope.clock() = object : WallClock {
        override fun instant(): Instant = Instant.fromEpochMilliseconds(base + testScheduler.currentTime)

        override val zone: TimeZone = TimeZone.UTC
    }

    @Test
    fun `the ticks fall on the whole seconds of the practice and not of the wall clock`() = runTest {
        val store = FakeRunningPracticeStore()
        // begun 0.7 s before a whole second of the wall
        store.startIfIdle(base - 700)
        val seen = mutableListOf<Long>()
        backgroundScope.launch { store.elapsedTicker(clock()).collect { it?.let(seen::add) } }
        runCurrent()
        advanceTimeBy(3_000)
        assertEquals(listOf(700L, 1_000L, 2_000L, 3_000L), seen, "the first at once, then at 1, 2 and 3 s of the practice")
    }

    @Test
    fun `the half minute a length rounds up at is a tick`() = runTest {
        val store = FakeRunningPracticeStore()
        store.startIfIdle(base - 12 * MS_PER_MINUTE - 29_300)
        val ticks = mutableListOf<PracticeTick>()
        backgroundScope.launch { store.practiceTicks(clock()).collect { it?.let(ticks::add) } }
        runCurrent()
        advanceTimeBy(701)
        assertEquals(12 * MS_PER_MINUTE + 30_000, ticks.last().elapsedMs, "12 min 30 s of the practice — «13 мин» from this very moment")
        assertEquals(base + 700, ticks.last().nowEpochMs)
    }

    @Test
    fun `a mark the store takes starts the ticks over with the new practice`() = runTest {
        val store = FakeRunningPracticeStore()
        store.startIfIdle(base - 5_000)
        val ticks = mutableListOf<PracticeTick?>()
        backgroundScope.launch { store.practiceTicks(clock()).collect { ticks += it } }
        runCurrent()
        advanceTimeBy(250)
        store.markSound(base + 250)
        runCurrent()
        assertEquals(base + 250, ticks.last()?.practice?.lastSoundEpochMs, "at once, not a second later")
        store.clear()
        runCurrent()
        assertNull(ticks.last(), "no practice, no ticks")
    }
}
