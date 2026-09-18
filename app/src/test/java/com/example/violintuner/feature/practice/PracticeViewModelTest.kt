package com.example.violintuner.feature.practice

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.practice.FakePracticeRepository
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.practice.PracticeEntry
import com.example.violintuner.core.domain.practice.PracticeFinisher
import com.example.violintuner.core.domain.practice.RunningPractice
import com.example.violintuner.core.domain.session.FakeSessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {
    private val repository = FakePracticeRepository()
    private val store = FakeRunningPracticeStore()
    private val sessions = FakeSessionRepository()
    private val config = PracticeConfig()
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** A clock the test moves by hand; the ticker's delays run on the test scheduler. */
    private class TestClock(var nowMs: Long, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = TestClock(nowMs, zone)
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    // 2026-09-17 18:00 Moscow
    private val clock = TestClock(Instant.parse("2026-09-17T15:00:00Z").toEpochMilli(), zone)
    private val today = LocalDate.of(2026, 9, 17)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): Pair<PracticeViewModel, MutableList<PracticeEffect>> {
        val viewModel = PracticeViewModel(repository, store, PracticeFinisher(repository, store, clock), sessions, config, IntonationConfig(), clock)
        val effects = mutableListOf<PracticeEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    /** Moves the wall clock and the virtual time together, a second at a time, like real time does. */
    private fun TestScope.pass(ms: Long) {
        runCurrent()
        var left = ms
        while (left > 0) {
            val slice = minOf(left, 1_000L)
            clock.nowMs += slice
            advanceTimeBy(slice)
            runCurrent() // a tick due exactly now runs against the clock of this moment
            left -= slice
        }
        runCurrent()
    }

    @Test
    fun `starts empty and idle`() = runTest {
        val (viewModel, _) = viewModel()
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertFalse(state.hasHistory)
        assertNull(state.runningMs)
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(today, state.selected.date)
    }

    @Test
    fun `start stores the moment, opens live and the timer follows the clock`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(RunningPractice(clock.nowMs, null), store.running.value)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
        assertEquals(0L, viewModel.state.value.runningMs)
        pass(2_500)
        assertEquals(2_000L, viewModel.state.value.runningMs)
        pass(500)
        assertEquals(3_000L, viewModel.state.value.runningMs)
    }

    @Test
    fun `a second start does not restart a running practice`() = runTest {
        val (viewModel, effects) = viewModel()
        store.start(clock.nowMs - 10 * MS_PER_MINUTE)
        runCurrent()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(clock.nowMs - 10 * MS_PER_MINUTE, store.running.value!!.startedAtEpochMs)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
    }

    @Test
    fun `stopping under a minute drops the practice with a toast`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(59_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertNull(store.running.value)
        assertNull(viewModel.state.value.sheet)
        assertEquals(PracticeEffect.ShowTooShort, effects.last())
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `stopping opens the summary and saving stores the practice on its start day`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        val start = clock.nowMs
        pass(47 * MS_PER_MINUTE + 20_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(47, sheet.minutes)
        assertEquals(47 * MS_PER_MINUTE + 20_000, sheet.actualMs)
        // the timer keeps going behind the sheet: the practice is not over until "Сохранить"
        assertEquals(47 * MS_PER_MINUTE + 20_000, viewModel.state.value.runningMs)

        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(
            listOf(PracticeEntry(today, start, 47 * MS_PER_MINUTE + 20_000, manual = false, id = 1)),
            repository.entries.value,
        )
        assertNull(store.running.value)
        assertNull(viewModel.state.value.sheet)
        assertNull(viewModel.state.value.runningMs)
        assertTrue(viewModel.state.value.hasHistory)
        assertEquals(47 * MS_PER_MINUTE + 20_000, viewModel.state.value.todayMs)
    }

    @Test
    fun `a trimmed summary saves whole minutes`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(47 * MS_PER_MINUTE + 20_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryStepped(-1))
        viewModel.onIntent(PracticeIntent.SummaryStepped(-1))
        runCurrent()
        assertEquals(37, (viewModel.state.value.sheet as PracticeSheet.Summary).minutes)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(37 * MS_PER_MINUTE, repository.entries.value.single().durationMs)
    }

    @Test
    fun `a practice started before midnight belongs to that day`() = runTest {
        val (viewModel, _) = viewModel()
        clock.nowMs = Instant.parse("2026-09-17T20:50:00Z").toEpochMilli() // 23:50 Moscow
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 17), repository.entries.value.single().date)
    }

    @Test
    fun `discarding the summary saves nothing`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()
        assertNull(store.running.value)
        assertNull(viewModel.state.value.sheet)
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `months move back and never past the current one`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)
        viewModel.onIntent(PracticeIntent.MonthBack)
        viewModel.onIntent(PracticeIntent.MonthBack)
        runCurrent()
        assertEquals(YearMonth.of(2026, 7), viewModel.state.value.month)
        assertTrue(viewModel.state.value.canGoForward)
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth.of(2026, 8), viewModel.state.value.month)
    }

    @Test
    fun `days are selectable up to today`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 3)))
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 3), viewModel.state.value.selected.date)
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 18)))
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 3), viewModel.state.value.selected.date)
    }

    @Test
    fun `editing a day replaces its time with a manual entry`() = runTest {
        repository.add(PracticeEntry(LocalDate.of(2026, 9, 16), 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 16)))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(50, (viewModel.state.value.sheet as PracticeSheet.EditTime).minutes)
        viewModel.onIntent(PracticeIntent.EditTimeAdded(30))
        viewModel.onIntent(PracticeIntent.EditTimeStepped(+1))
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        val (date, duration, start) = repository.replacedDays.single()
        assertEquals(LocalDate.of(2026, 9, 16), date)
        assertEquals(85 * MS_PER_MINUTE, duration)
        assertEquals(LocalDate.of(2026, 9, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), start)
        assertNull(viewModel.state.value.sheet)
        assertEquals(85 * MS_PER_MINUTE, viewModel.state.value.selected.totalMs)
    }

    @Test
    fun `clearing a day to zero and cancelling the sheet`() = runTest {
        repository.add(PracticeEntry(today, 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertEquals(50 * MS_PER_MINUTE, viewModel.state.value.todayMs)
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertEquals(0L, viewModel.state.value.todayMs)
        assertFalse(viewModel.state.value.hasHistory)
    }

    @Test
    fun `a session card opens the session`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.SessionClicked(7))
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenSession(7)), effects)
    }
}
