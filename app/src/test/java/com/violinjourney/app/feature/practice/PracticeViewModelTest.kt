package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.practice.testPracticeFinisher
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.FakeBlockStore
import com.violinjourney.app.core.domain.practice.NoBlocks
import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.progress.FakeProfileRepository
import com.violinjourney.app.core.domain.progress.FakeTrophyRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.domain.venue.FollowTheRoad
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.MutableWallClock
import com.violinjourney.app.feature.journey.JourneyMotion
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private val trophies = FakeTrophyRepository()
    private val profiles = FakeProfileRepository()
    private val avatarFiles = FakeAvatarFiles()
    private val zone: TimeZone = TimeZone.of("Europe/Moscow")

    // 2026-09-17 18:00 Moscow
    private val journey = FakeJourneyRepository()
    private val clock = MutableWallClock(Instant.parse("2026-09-17T15:00:00Z").toEpochMilliseconds(), zone)
    private val today = LocalDate(2026, 9, 17)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * [finisher] is one per app: a test that saves as the prompt would passes its own to share it with the screen. [watchState]
     * false — the test watches the state itself, to stop watching it as a screen in the background does.
     */
    private fun TestScope.viewModel(
        finishAsk: FinishPracticeAsk = FinishPracticeAsk(),
        repository: FakePracticeRepository = this@PracticeViewModelTest.repository,
        avatarFiles: AvatarFiles = this@PracticeViewModelTest.avatarFiles,
        finisher: PracticeFinisher = testPracticeFinisher(repository, store, clock, journey = journey),
        repertoire: RepertoireRepository = FakeRepertoireRepository(),
        blocks: BlockStore = NoBlocks,
        watchState: Boolean = true,
    ): Pair<PracticeViewModel, MutableList<PracticeEffect>> {
        val viewModel = PracticeViewModel(
            repository, store, finisher, sessions, config, repertoire, clock,
            trophies, profiles, avatarFiles, ProgressConfig(), journey, blocks = blocks, finishAsk = finishAsk, venues = Venues(FollowTheRoad, journey),
            journeyConfig = JourneyConfig(), analytics = NoOpAnalytics(), backings = NoBackings,
        )
        val effects = mutableListOf<PracticeEffect>()
        if (watchState) backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.timer.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    /** «Мой путь» opened from the path row: «Трофеи» and «Имя и фото» open only over it (spec 3.36.2). */
    private fun TestScope.openPath(viewModel: PracticeViewModel) {
        viewModel.onIntent(PracticeIntent.PathClicked)
        runCurrent()
        assertEquals(PracticeSheet.Path, viewModel.state.value.sheet)
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
        assertFalse(state.running)
        assertNull(viewModel.timer.value)
        assertEquals(YearMonth(2026, 9), state.month)
        assertNull("no day is selected until its sheet is open (spec 3.36.2)", state.selected)
    }

    @Test
    fun `start stores the moment, opens live and the timer follows the clock`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(RunningPractice(clock.nowMs, null), store.running.value)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
        assertTrue(viewModel.state.value.running)
        assertEquals(0L, viewModel.timer.value?.elapsedMs)
        pass(2_500)
        assertEquals(2_000L, viewModel.timer.value?.elapsedMs)
        pass(500)
        assertEquals(3_000L, viewModel.timer.value?.elapsedMs)
    }

    @Test
    fun `the state stands still while only the clock ticks`() = runTest {
        // the photo's file is looked for each time the state is worked out: that is what must not happen every second
        var looks = 0
        val counting = object : AvatarFiles by avatarFiles {
            override fun existing(name: String) = avatarFiles.existing(name).also { looks++ }
        }
        profiles.setAvatarFile(avatarFiles.import("content://photo"))
        val (viewModel, _) = viewModel(avatarFiles = counting)
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        val looksAtStart = looks
        val states = mutableListOf<PracticeState>()
        val ticks = mutableListOf<Long?>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        backgroundScope.launch { viewModel.timer.collect { ticks += it?.elapsedMs } }
        pass(5_000)
        // the calendar, the header and the day's records are not worked out again for a tick of the practice clock
        assertEquals(1, states.size)
        assertTrue(states.single().running)
        assertEquals(looksAtStart, looks)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L, 5_000L), ticks)
    }

    @Test
    fun `a second start does not restart a running practice`() = runTest {
        val (viewModel, effects) = viewModel()
        store.startIfIdle(clock.nowMs - 10 * MS_PER_MINUTE)
        runCurrent()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(clock.nowMs - 10 * MS_PER_MINUTE, store.running.value!!.startedAtEpochMs)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
    }

    @Test
    fun `a start here in the moment another screen starts one keeps the first start`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        // the tag on Live a second ago, written before this screen has heard of it
        val other = clock.nowMs - 1_000
        store.startIfIdle(other)
        runCurrent()
        assertEquals(RunningPractice(other, null), store.running.value)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
    }

    @Test
    fun `«Не сохранять» on the sheet of a practice saved elsewhere leaves the next one running`() = runTest {
        val finisher = testPracticeFinisher(repository, store, clock, journey = journey)
        val (viewModel, _) = viewModel(finisher = finisher)
        viewModel.onIntent(PracticeIntent.StartClicked)
        val first = clock.nowMs
        pass(20 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Summary)

        // the forgotten-practice prompt saves it, and the tag on Live begins the next one, before the sheet is answered
        assertNotNull(finisher.save(first, 20 * MS_PER_MINUTE))
        val next = clock.nowMs
        store.startIfIdle(next)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()

        assertEquals(RunningPractice(next, null), store.running.value)
        assertEquals(1, repository.entries.value.size)
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
        assertEquals(47 * MS_PER_MINUTE + 20_000, viewModel.timer.value?.elapsedMs)

        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(
            listOf(PracticeEntry(today, start, 47 * MS_PER_MINUTE + 20_000, manual = false, id = 1)),
            repository.entries.value,
        )
        assertNull(store.running.value)
        // «Занятие сохранено» takes the summary's place (spec 3.31)
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
        assertFalse(viewModel.state.value.running)
        assertNull(viewModel.timer.value)
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
        clock.nowMs = Instant.parse("2026-09-17T20:50:00Z").toEpochMilliseconds() // 23:50 Moscow
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(LocalDate(2026, 9, 17), repository.entries.value.single().date)
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
    fun `two quick taps on «Сохранить» store the practice and its takts once`() = runTest {
        // a row takes a moment to write, as in the database: the second tap lands while the first one writes
        val slow = FakePracticeRepository(addDelayMs = 1)
        val (viewModel, _) = viewModel(repository = slow)
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        pass(1_000)

        assertEquals(1, slow.entries.value.size)
        assertEquals(1, journey.earnings.size)
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
    }

    @Test
    fun `«Не сохранять» right after «Сохранить» keeps the recap`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()

        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
        assertEquals(1, repository.entries.value.size)
        assertNull(store.running.value)
    }

    @Test
    fun `a late «Не сохранять» leaves the recap alone`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)

        // whatever order the taps are run in, «Не сохранять» answers the summary alone
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
        assertEquals(1, repository.entries.value.size)
    }

    @Test
    fun `a double tap on «Закончить» of a practice too short to keep says so once`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()

        assertNull(store.running.value)
        assertEquals(1, effects.count { it == PracticeEffect.ShowTooShort })
    }

    @Test
    fun `hiding the summary is not an answer - the practice runs on and nothing is saved`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Summary)

        viewModel.onIntent(PracticeIntent.SummaryHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertNotNull("still running", store.running.value)
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `finishing asked for from Live opens the summary once, even in a view model made for the ask`() = runTest {
        store.startIfIdle(clock.millis() - 20 * MS_PER_MINUTE)
        val ask = FinishPracticeAsk().apply { ask() }
        val (viewModel, _) = viewModel(ask)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(20, sheet.minutes)
        assertEquals(false, ask.asked.value)

        viewModel.onIntent(PracticeIntent.SummaryHidden)
        runCurrent()
        assertNull("the ask is forgotten, not repeated", viewModel.state.value.sheet)
    }

    @Test
    fun `months move back and never past the current one`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
        viewModel.onIntent(PracticeIntent.MonthBack)
        viewModel.onIntent(PracticeIntent.MonthBack)
        runCurrent()
        assertEquals(YearMonth(2026, 7), viewModel.state.value.month)
        assertTrue(viewModel.state.value.canGoForward)
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth(2026, 8), viewModel.state.value.month)
    }

    @Test
    fun `a tapped day opens its sheet and a day to come opens nothing`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 18)))
        runCurrent()
        assertNull("tomorrow has nothing to open until R9", viewModel.state.value.sheet)
        assertNull(viewModel.state.value.selected)

        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 3)))
        runCurrent()
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 3)), viewModel.state.value.sheet)
        assertEquals(LocalDate(2026, 9, 3), viewModel.state.value.selected?.date)
        assertEquals(listOf(LocalDate(2026, 9, 3)), viewModel.state.value.cells.filterNotNull().filter { it.isSelected }.map { it.date })

        // one sheet at a time: another day tapped under the sheet opens nothing
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 4)))
        runCurrent()
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 3)), viewModel.state.value.sheet)
    }

    @Test
    fun `a swipe hides the sheet of the day and the day is no longer selected`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        runCurrent()
        assertTrue(viewModel.state.value.selected!!.isToday)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertNull(viewModel.state.value.selected)
        assertTrue(viewModel.state.value.cells.filterNotNull().none { it.isSelected })
    }

    @Test
    fun `«Время за день» opens only over the sheet of the day - and the day stays selected under it`() = runTest {
        repository.add(PracticeEntry(LocalDate(2026, 9, 16), 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertNull("no sheet of a day, nothing to edit", viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        val edit = viewModel.state.value.sheet as PracticeSheet.EditTime
        assertEquals(LocalDate(2026, 9, 16), edit.date)
        assertEquals(50, edit.minutes)
        assertEquals("the ring «выбран» stays", LocalDate(2026, 9, 16), viewModel.state.value.selected?.date)
        // a late swipe of the sheet of the day does not close what took its place
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertEquals(edit, viewModel.state.value.sheet)
    }

    @Test
    fun `«Время за день» of a day over twelve hours opens at twelve and keeps its real sum`() = runTest {
        val day = LocalDate(2026, 9, 16)
        repository.add(PracticeEntry(day, 1_000, 7 * MS_PER_HOUR, manual = false))
        repository.add(PracticeEntry(day, 2_000, 6 * MS_PER_HOUR + 10 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(day))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        val edit = viewModel.state.value.sheet as PracticeSheet.EditTime
        assertEquals("the limit of a day (5.6)", 720, edit.minutes)
        assertEquals("«было 13 ч 10 мин» says the real sum (spec 3.36.3)", 13 * MS_PER_HOUR + 10 * MS_PER_MINUTE, edit.dayTotalMs)
        viewModel.onIntent(PracticeIntent.EditTimeStepped(-1))
        runCurrent()
        assertEquals(DayCaption.Was(13 * MS_PER_HOUR + 10 * MS_PER_MINUTE), PracticeReducer.dayCaptionOf(viewModel.state.value.sheet as PracticeSheet.EditTime))
        // untouched, «Сохранить» rewrites nothing: the day keeps its thirteen hours
        viewModel.onIntent(PracticeIntent.EditTimeStepped(+1))
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertTrue(repository.replacedDays.isEmpty())
        assertEquals(PracticeSheet.Day(day), viewModel.state.value.sheet)
    }

    @Test
    fun `editing a day replaces its time with a manual entry`() = runTest {
        repository.add(PracticeEntry(LocalDate(2026, 9, 16), 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(50, (viewModel.state.value.sheet as PracticeSheet.EditTime).minutes)
        viewModel.onIntent(PracticeIntent.EditTimeAdded(30))
        viewModel.onIntent(PracticeIntent.EditTimeStepped(+1))
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        val (date, duration, start) = repository.replacedDays.single()
        assertEquals(LocalDate(2026, 9, 16), date)
        assertEquals(85 * MS_PER_MINUTE, duration)
        assertEquals(LocalDate(2026, 9, 16).atTime(12, 0).toInstant(zone).toEpochMilliseconds(), start)
        // spec 3.36.2: «Сохранить» gives the sheet of the day back, with the new time
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 16)), viewModel.state.value.sheet)
        assertEquals(85 * MS_PER_MINUTE, viewModel.state.value.selected?.totalMs)
    }

    @Test
    fun `the time of a day set by hand earns no takts - the window stays the first run`() = runTest {
        journey.start(clock.millis())
        val (viewModel, _) = viewModel()
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        runCurrent()
        assertTrue(viewModel.journeyWindow.value!!.neverEarned)

        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        viewModel.onIntent(PracticeIntent.EditTimeAdded(60))
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertEquals(60 * MS_PER_MINUTE, repository.replacedDays.single().second)
        assertTrue(viewModel.state.value.hasHistory)
        // spec 3.36.2: the time of a day is not takts (3.23) — «Ваша комната» stays until a practice earns the first ones
        val window = viewModel.journeyWindow.value!!
        assertTrue(window.neverEarned)
        assertEquals(0L, window.balance)
        assertTrue(journey.earnings.isEmpty())
    }

    @Test
    fun `the first practice saved ends the first run of the window`() = runTest {
        val (viewModel, _) = viewModel()
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        runCurrent()
        assertTrue(viewModel.journeyWindow.value!!.neverEarned)
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(20 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertFalse(viewModel.journeyWindow.value!!.neverEarned)
    }

    @Test
    fun `clearing a day to zero and cancelling the sheet`() = runTest {
        repository.add(PracticeEntry(today, 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        // «Отмена» and a swipe alike (the sheet sends the same intent): the sheet of the day with the old time
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertEquals(50 * MS_PER_MINUTE, viewModel.state.value.todayMs)
        assertEquals(PracticeSheet.Day(today), viewModel.state.value.sheet)
        assertEquals(50 * MS_PER_MINUTE, viewModel.state.value.selected?.totalMs)
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertEquals(0L, viewModel.state.value.todayMs)
        assertFalse(viewModel.state.value.hasHistory)
        assertEquals(PracticeSheet.Day(today), viewModel.state.value.sheet)
        assertEquals(0L, viewModel.state.value.selected?.totalMs)
    }

    @Test
    fun `a record of the sheet of the day opens its screen and the sheet comes back with the screen`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        runCurrent()
        viewModel.onIntent(PracticeIntent.SessionClicked(7))
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenSession(7)), effects)
        val away = viewModel.state.value
        assertTrue("the sheet steps aside while the record is on the screen", away.sheetsAway)
        assertEquals("the model keeps it", PracticeSheet.Day(LocalDate(2026, 9, 16)), away.sheet)

        // a trophy given meanwhile waits for the sheet, as under any sheet
        trophies.award(1, today)
        runCurrent()
        assertNull(viewModel.state.value.gift)

        // «назад» from the record: the screen is resumed and the sheet of the day rises again
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertFalse(viewModel.state.value.sheetsAway)
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 16)), viewModel.state.value.sheet)
        assertNull(viewModel.state.value.gift)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
    }

    @Test
    fun `a resumed screen without a record opened changes nothing`() = runTest {
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertEquals(PracticeSheet.Path, viewModel.state.value.sheet)
        assertFalse(viewModel.state.value.sheetsAway)
    }

    @Test
    fun `the header follows the entries, the trophies and the profile`() = runTest {
        val (viewModel, _) = viewModel()
        assertEquals(1, viewModel.state.value.header.level)
        assertEquals(listOf(TrophyBadge(1, given = false, index = 0)), viewModel.state.value.header.trophyRow)

        repository.replaceDay(today, 12 * MS_PER_HOUR, startedAtEpochMs = 0)
        trophies.award(1, today)
        trophies.award(10, today)
        // Seen already: a trophy stands in the row only once its gift sheet is answered.
        trophies.markShown(1)
        trophies.markShown(10)
        profiles.setName("Даня")
        runCurrent()

        val header = viewModel.state.value.header
        assertEquals("Даня", header.name)
        assertEquals(12 * MS_PER_HOUR, header.totalMs)
        assertEquals(4, header.level)
        assertEquals(listOf(TrophyBadge(1, true, index = 0), TrophyBadge(10, true, index = 1), TrophyBadge(50, false, index = 2)), header.trophyRow)
    }

    @Test
    fun `the name is stored when the profile sheet closes, cleaned and cut`() = runTest {
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals(PracticeSheet.Profile(nameDraft = "", importingPhoto = false), viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged("  Даня "))
        runCurrent()
        assertEquals("  Даня ", (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft)
        assertEquals("nothing is stored while typing", "", profiles.profile.value.name)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged("я".repeat(30)))
        runCurrent()
        assertEquals(24, (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft.length)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged(" Даня "))
        viewModel.onIntent(PracticeIntent.ProfileClosed)
        runCurrent()
        assertEquals("«Мой путь» comes back", PracticeSheet.Path, viewModel.state.value.sheet)
        assertEquals("Даня", profiles.profile.value.name)
        assertEquals("Даня", viewModel.state.value.header.name)
    }

    @Test
    fun `the profile sheet opens with the stored name and an emptied field removes it`() = runTest {
        profiles.setName("Даня")
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals("Даня", (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged(""))
        viewModel.onIntent(PracticeIntent.ProfileClosed)
        runCurrent()
        assertEquals("", profiles.profile.value.name)
    }

    @Test
    fun `a picked photo replaces the old one at once and the old file goes`() = runTest {
        val (viewModel, effects) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://first"))
        runCurrent()
        val first = profiles.profile.value.avatarFile
        assertEquals(setOf(first), avatarFiles.names)
        assertEquals(first, viewModel.state.value.header.avatarPath)
        assertFalse((viewModel.state.value.sheet as PracticeSheet.Profile).importingPhoto)

        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://second"))
        runCurrent()
        val second = profiles.profile.value.avatarFile
        assertTrue(second != null && second != first)
        assertEquals(setOf(second), avatarFiles.names)

        viewModel.onIntent(PracticeIntent.ProfilePhotoRemoved)
        runCurrent()
        assertNull(profiles.profile.value.avatarFile)
        assertNull(viewModel.state.value.header.avatarPath)
        assertTrue(avatarFiles.names.isEmpty())
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a photo that cannot be read says so and keeps the old one`() = runTest {
        val (viewModel, effects) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://first"))
        runCurrent()
        val first = profiles.profile.value.avatarFile

        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://broken"))
        runCurrent()
        assertEquals(listOf<PracticeEffect>(PracticeEffect.ShowPhotoFailed), effects)
        assertEquals(first, profiles.profile.value.avatarFile)
        assertFalse((viewModel.state.value.sheet as PracticeSheet.Profile).importingPhoto)
    }

    @Test
    fun `a photo whose file is gone reads as no photo`() = runTest {
        profiles.setAvatarFile("avatar-lost.jpg")
        val (viewModel, _) = viewModel()
        assertNull(viewModel.state.value.header.avatarPath)
    }

    @Test
    fun `«Все трофеи» take the place of «Мой путь» and give it back`() = runTest {
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        runCurrent()
        assertEquals(PracticeSheet.Trophies, viewModel.state.value.sheet)
        // one sheet at a time: the profile does not open over the trophies
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals(PracticeSheet.Trophies, viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.TrophiesClosed)
        runCurrent()
        assertEquals(PracticeSheet.Path, viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `«Имя и фото» takes the place of «Мой путь» and gives it back with the name stored`() = runTest {
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Profile)
        viewModel.onIntent(PracticeIntent.ProfileNameChanged("Аня"))
        viewModel.onIntent(PracticeIntent.ProfileClosed)
        runCurrent()
        assertEquals(PracticeSheet.Path, viewModel.state.value.sheet)
        assertEquals("Аня", profiles.profile.value.name)
        assertEquals("Аня", viewModel.state.value.header.name)
    }

    @Test
    fun `the trophies and the profile open only over «Мой путь»`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)

        repository.add(PracticeEntry(today, 1_000, 50 * MS_PER_MINUTE, manual = false))
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        val edit = viewModel.state.value.sheet
        assertTrue(edit is PracticeSheet.EditTime)
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        runCurrent()
        assertEquals(edit, viewModel.state.value.sheet)
    }

    @Test
    fun `«Мой путь» does not open over another sheet and its swipe closes only itself`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        viewModel.onIntent(PracticeIntent.PathClicked)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.EditTime)
        // a late swipe of «Мой путь» must not close what stands there now
        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.EditTime)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertEquals(PracticeSheet.Day(today), viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.PathClicked)
        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertEquals("nor over the sheet of the day", PracticeSheet.Day(today), viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)

        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertEquals(PracticeSheet.Trophies, viewModel.state.value.sheet)
    }

    @Test
    fun `«Настройки» of «Мой путь» close it and open the settings once`() = runTest {
        val (viewModel, effects) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.PathSettingsClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals(listOf<PracticeEffect>(PracticeEffect.OpenSettings), effects)
        viewModel.onIntent(PracticeIntent.PathSettingsClicked)
        runCurrent()
        assertEquals("no sheet, no second settings", 1, effects.size)
    }

    @Test
    fun `the gift waits while «Мой путь» and a sheet over it are open`() = runTest {
        val (viewModel, _) = viewModel()
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        runCurrent()
        trophies.award(1, today)
        runCurrent()
        assertNull("«Трофеи» are open", viewModel.state.value.gift)
        viewModel.onIntent(PracticeIntent.TrophiesClosed)
        runCurrent()
        assertNull("«Мой путь» is back", viewModel.state.value.gift)
        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
    }

    @Test
    fun `a practice saved by the prompt over «Мой путь» and a sheet over it is not recapped - the pill comes once all have closed`() = runTest {
        journey.start(clock.millis())
        val finisher = testPracticeFinisher(repository, store, clock, journey = journey)
        val (viewModel, _) = viewModel(finisher = finisher)
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        openPath(viewModel)
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        runCurrent()

        // saved elsewhere — the forgotten-practice prompt over this screen, through the one finisher of the app
        val start = clock.millis() - 110 * MS_PER_MINUTE
        store.startIfIdle(start)
        finisher.save(start, 110 * MS_PER_MINUTE)
        runCurrent()
        assertEquals("the recap does not take the place of «Трофеи»", PracticeSheet.Trophies, viewModel.state.value.sheet)
        assertNull("no pill under a sheet", viewModel.journeyWindow.value!!.justEarned)

        viewModel.onIntent(PracticeIntent.TrophiesClosed)
        runCurrent()
        assertEquals("«Трофеи» give «Мой путь» back, not a recap", PracticeSheet.Path, viewModel.state.value.sheet)
        assertNull("«Мой путь» is still open", viewModel.journeyWindow.value!!.justEarned)

        viewModel.onIntent(PracticeIntent.PathHidden)
        runCurrent()
        assertNull("no recap after the last sheet either", viewModel.state.value.sheet)
        assertEquals(220, viewModel.journeyWindow.value!!.justEarned)
        assertFalse(viewModel.state.value.recapPending)
    }

    @Test
    fun `«Вернуться к Live» opens Live and the practice runs on`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        effects.clear()
        viewModel.onIntent(PracticeIntent.BackToLiveClicked)
        runCurrent()
        assertEquals(listOf<PracticeEffect>(PracticeEffect.OpenLive), effects)
        assertTrue(viewModel.state.value.running)
    }

    @Test
    fun `the state knows the day the running practice began on`() = runTest {
        val (viewModel, _) = viewModel()
        assertNull(viewModel.state.value.runningSince)
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(today, viewModel.state.value.runningSince)
        assertEquals(today, viewModel.state.value.today)
    }

    @Test
    fun `after midnight the running practice belongs to yesterday`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T20:50:00Z").toEpochMilliseconds() // 23:50 in Moscow
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(today, viewModel.state.value.runningSince)

        pass(20 * MS_PER_MINUTE)
        val state = viewModel.state.value
        assertEquals(LocalDate(2026, 9, 18), state.today)
        assertEquals(today, state.runningSince)
        assertTrue(state.running)
        assertFalse("begun yesterday: it hatches no bar of today", state.runningToday)
        assertFalse("nor adds to today", state.withRunningToday)
    }

    @Test
    fun `gifts come one after another, lowest first, and each thanks marks its trophy seen`() = runTest {
        trophies.award(10, today)
        trophies.award(1, today)
        val (viewModel, _) = viewModel()
        assertEquals(1, viewModel.state.value.gift?.hours)
        assertTrue(viewModel.state.value.header.trophyRow.none { it.given })

        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()
        assertEquals(10, viewModel.state.value.gift?.hours)
        assertEquals(listOf(TrophyBadge(1, true, index = 0), TrophyBadge(10, false, index = 1)), viewModel.state.value.header.trophyRow)

        viewModel.onIntent(PracticeIntent.GiftAccepted(10))
        runCurrent()
        assertNull(viewModel.state.value.gift)
        assertTrue(trophies.trophies.value.all { it.shown })
        assertEquals(listOf(TrophyBadge(1, true, index = 0), TrophyBadge(10, true, index = 1), TrophyBadge(50, false, index = 2)), viewModel.state.value.header.trophyRow)
    }

    @Test
    fun `a gift waits while another sheet is open`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        trophies.award(1, today)
        runCurrent()
        assertNull("the edit sheet is still open", viewModel.state.value.gift)

        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertNull("the sheet of the day is back", viewModel.state.value.gift)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
    }

    @Test
    fun `a gift not answered comes back after the process dies`() = runTest {
        trophies.award(1, today)
        val (first, _) = viewModel()
        assertEquals(1, first.state.value.gift?.hours)

        val (second, _) = viewModel()
        // with its card: nothing practised yet, the trophy of 10 hours is all ten hours away (spec 3.36.3)
        assertEquals(
            Gift(hours = 1, index = 0, awardedDate = today, next = NextTrophy(10, index = 1, remainingMs = 10 * 60 * MS_PER_MINUTE)),
            second.state.value.gift,
        )
    }

    @Test
    fun `the journey window shows takts earned on the spot after their recap, not those earned before`() = runTest {
        journey.start(clock.millis())
        journey.earn(TaktEarning(clock.millis(), 100, 80, 600_000, 100))
        val finisher = testPracticeFinisher(repository, store, clock, journey = journey)
        val (viewModel, effects) = viewModel(finisher = finisher)
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        runCurrent()

        val before = viewModel.journeyWindow.value!!
        assertEquals(100L, before.balance)
        assertEquals(200L, before.missing)
        assertNull(before.justEarned)
        assertNull(viewModel.state.value.sheet) // history is not recapped

        // saved elsewhere — the forgotten-practice prompt over this screen, through the one finisher of the app:
        // the recap opens here all the same
        val start = clock.millis() - 110 * MS_PER_MINUTE
        store.startIfIdle(start)
        finisher.save(start, 110 * MS_PER_MINUTE)
        runCurrent()
        val recap = (viewModel.state.value.sheet as PracticeSheet.Recap).recap
        assertEquals(220, recap.takts) // 110 minutes, no notes, no elements
        assertEquals(110 * MS_PER_MINUTE, recap.durationMs)
        assertNull(viewModel.journeyWindow.value!!.justEarned) // the pill waits for the recap

        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        val fresh = viewModel.journeyWindow.value!!
        assertEquals(220, fresh.justEarned)
        assertTrue(fresh.canDepart)

        advanceTimeBy(JourneyMotion.EARNED_PILL_MS + 1)
        runCurrent()
        assertNull(viewModel.journeyWindow.value!!.justEarned)
        assertEquals(320L, viewModel.journeyWindow.value!!.balance)

        viewModel.onIntent(PracticeIntent.JourneyClicked)
        runCurrent()
        assertEquals(PracticeEffect.OpenJourney, effects.last())
    }

    @Test
    fun `saving recaps the practice once, and the recap comes before the gift and the pill`() = runTest {
        journey.start(clock.millis())
        val (viewModel, _) = viewModel()
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(62 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        trophies.award(1, today) // the hour the practice has just crossed
        runCurrent()

        val recap = (viewModel.state.value.sheet as PracticeSheet.Recap).recap
        assertEquals(124, recap.takts) // 62 minutes, no notes, no elements
        assertEquals(124, recap.sources.timeTakts)
        assertEquals(0, recap.sources.notesTakts)
        assertEquals(1, recap.streakDays)
        assertTrue(recap.streakExtended)
        assertNull(recap.dayTotalMs)
        assertEquals(1, journey.earnings.size)
        assertNull(viewModel.state.value.gift) // it waits for the recap

        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
        assertNull(viewModel.journeyWindow.value!!.justEarned) // and the pill waits for the gift

        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()
        assertEquals(124, viewModel.journeyWindow.value!!.justEarned)
        advanceTimeBy(JourneyMotion.EARNED_PILL_MS + 1)
        runCurrent()
        assertNull(viewModel.journeyWindow.value!!.justEarned)
        // the earning seen by the journey flow afterwards is the same practice: no second recap
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `a practice saved before the screen opened is history - not recapped`() = runTest {
        val finisher = testPracticeFinisher(repository, store, clock, journey = journey)
        val start = clock.millis() - 30 * MS_PER_MINUTE
        store.startIfIdle(start)
        finisher.save(start, 30 * MS_PER_MINUTE)
        trophies.award(1, today)

        val (viewModel, _) = viewModel(finisher = finisher)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals("nothing to wait for: the gift comes at once", 1, viewModel.state.value.gift?.hours)
    }

    @Test
    fun `a trophy the prompt's save brings waits for its recap`() = runTest {
        // a row takes a while to write: the prompt's «Закончить сейчас» is still saving when the trophy is given
        val slow = FakePracticeRepository(addDelayMs = 10)
        val finisher = testPracticeFinisher(slow, store, clock, journey = journey)
        val (viewModel, _) = viewModel(repository = slow, finisher = finisher)
        val start = clock.millis() - 62 * MS_PER_MINUTE
        store.startIfIdle(start)
        runCurrent()

        launch { finisher.save(start, 62 * MS_PER_MINUTE) }
        runCurrent()
        trophies.award(1, today)
        runCurrent()
        assertNull("the practice is being saved: the gift waits for its recap", viewModel.state.value.gift)

        advanceTimeBy(20)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
        assertNull(viewModel.state.value.gift)

        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
        // answered, so that the pill waiting for the gift does not keep the screen's flows — the day's clock with them — alive
        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()
    }

    @Test
    fun `a practice left running past twelve hours is summed up to its last sound`() = runTest {
        val start = clock.millis() - 13 * MS_PER_HOUR
        store.startIfIdle(start)
        store.markSound(start + 40 * MS_PER_MINUTE)
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(40 * MS_PER_MINUTE, sheet.actualMs)
        assertEquals(40, sheet.maxMinutes)

        // the same from the tag on Live
        viewModel.onIntent(PracticeIntent.SummaryHidden)
        val (asked, _) = viewModel(FinishPracticeAsk().apply { ask() })
        runCurrent()
        assertEquals(40 * MS_PER_MINUTE, (asked.state.value.sheet as PracticeSheet.Summary).actualMs)
    }

    @Test
    fun `the sheet asked for from Live lists what was played even before the titles are heard`() = runTest {
        val start = clock.millis() - 20 * MS_PER_MINUTE
        store.startIfIdle(start)
        val stored = FakeRepertoireRepository()
        val scale = stored.add(PieceDraft(title = "G-dur · 3 октавы"), 0)
        // the titles take a moment to come, as from the database: the view model made for the ask has not heard them
        val slowTitles = object : RepertoireRepository by stored {
            override val pieces: Flow<List<Piece>> = stored.pieces.onStart { delay(50) }
        }
        val blocks = FakeBlockStore()
        blocks.update { BlockRules.started(it, start, scale, 10 * MS_PER_MINUTE, start) }
        val (viewModel, _) = viewModel(FinishPracticeAsk().apply { ask() }, repertoire = slowTitles, blocks = blocks)
        advanceTimeBy(100)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(listOf(PlayedLine("G-dur · 3 октавы", 10, 10, done = true)), sheet.played)
    }

    @Test
    fun `blocks left of another practice are not in its sheet`() = runTest {
        val start = clock.millis() - 20 * MS_PER_MINUTE
        store.startIfIdle(start)
        val pieces = FakeRepertoireRepository()
        val scale = pieces.add(PieceDraft(title = "G-dur · 3 октавы"), 0)
        val blocks = FakeBlockStore()
        // an hour earlier, another practice whose blocks were not cleared
        val other = start - MS_PER_HOUR
        blocks.update { BlockRules.started(it, other, scale, 10 * MS_PER_MINUTE, other) }

        val (viewModel, _) = viewModel(repertoire = pieces, blocks = blocks)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertTrue((viewModel.state.value.sheet as PracticeSheet.Summary).played.isEmpty())

        viewModel.onIntent(PracticeIntent.SummaryHidden)
        val (asked, _) = viewModel(FinishPracticeAsk().apply { ask() }, repertoire = pieces, blocks = blocks)
        runCurrent()
        assertTrue((asked.state.value.sheet as PracticeSheet.Summary).played.isEmpty())
    }

    @Test
    fun `saving an unchanged day writes nothing`() = runTest {
        repository.add(PracticeEntry(today, 1_000, 47 * MS_PER_MINUTE + 40_000, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(48, (viewModel.state.value.sheet as PracticeSheet.EditTime).minutes)
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertTrue(repository.replacedDays.isEmpty())
        assertEquals(47 * MS_PER_MINUTE + 40_000, viewModel.state.value.todayMs)
        assertEquals(PracticeSheet.Day(today), viewModel.state.value.sheet)
    }

    @Test
    fun `the month follows today across midnight`() = runTest {
        clock.nowMs = Instant.parse("2026-09-30T20:59:30Z").toEpochMilliseconds() // 23:59:30 in Moscow
        val (viewModel, _) = viewModel()
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)

        pass(60_000)
        val state = viewModel.state.value
        assertEquals(LocalDate(2026, 10, 1), state.cells.filterNotNull().single { it.isToday }.date)
        assertNull("no day is selected by itself", state.selected)
        assertEquals(YearMonth(2026, 10), state.month)
        assertFalse(state.canGoForward)
    }

    @Test
    fun `the sheet of a day keeps its date across midnight and today moves on`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T20:59:30Z").toEpochMilliseconds() // 23:59:30 in Moscow
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        runCurrent()
        assertTrue(viewModel.state.value.selected!!.isToday)
        pass(60_000)
        val state = viewModel.state.value
        assertEquals(PracticeSheet.Day(today), state.sheet)
        assertEquals(today, state.selected?.date)
        assertFalse("yesterday now", state.selected!!.isToday)
        assertEquals(LocalDate(2026, 9, 18), state.cells.filterNotNull().single { it.isToday }.date)
    }

    @Test
    fun `a sheet of a day the date went back under closes in the model - the gift comes and the screen opens sheets again`() = runTest {
        val (viewModel, _) = viewModel(watchState = false)
        var screen = backgroundScope.launch { viewModel.state.collect {} }

        // the screen in the background long enough for its state to stop, and the date goes back meanwhile — a zone crossed
        // westward over midnight, a clock set back by hand: the screen comes back to a sheet of a day to come
        fun awayWhileTheDateGoesBack() {
            screen.cancel()
            pass(10_000)
            clock.nowMs -= 24 * MS_PER_HOUR
            screen = backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
        }

        viewModel.onIntent(PracticeIntent.DaySelected(today))
        runCurrent()
        assertEquals(PracticeSheet.Day(today), viewModel.state.value.sheet)
        trophies.award(1, today)
        runCurrent()
        assertNull("the gift waits for the sheet of the day", viewModel.state.value.gift)

        awayWhileTheDateGoesBack()
        val yesterday = LocalDate(2026, 9, 16)
        val back = viewModel.state.value
        assertEquals(yesterday, back.today)
        assertNull("a day to come has no sheet: the model closes it as a hide would", back.sheet)
        assertNull(back.selected)
        assertTrue(back.cells.filterNotNull().none { it.isSelected })
        assertEquals("nothing holds the gift any more", 1, back.gift?.hours)
        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()

        // not stuck: a day opens its sheet again; «Время за день» over it closes with it when the date goes back once more
        viewModel.onIntent(PracticeIntent.DaySelected(yesterday))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(yesterday, (viewModel.state.value.sheet as PracticeSheet.EditTime).date)
        awayWhileTheDateGoesBack()
        assertEquals(LocalDate(2026, 9, 15), viewModel.state.value.today)
        assertNull("«Время за день» and the sheet of the day under it", viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertNull("a late «Отмена» brings back no sheet", viewModel.state.value.sheet)
        openPath(viewModel)
    }

    @Test
    fun `a practice saved by the prompt over the sheet of the day is not recapped - the pill comes once it has closed`() = runTest {
        journey.start(clock.millis())
        val finisher = testPracticeFinisher(repository, store, clock, journey = journey)
        val (viewModel, _) = viewModel(finisher = finisher)
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        runCurrent()

        // saved elsewhere — the forgotten-practice prompt over this screen, through the one finisher of the app
        val start = clock.millis() - 110 * MS_PER_MINUTE
        store.startIfIdle(start)
        finisher.save(start, 110 * MS_PER_MINUTE)
        runCurrent()
        assertEquals("the recap does not take the place of the sheet of the day", PracticeSheet.Day(LocalDate(2026, 9, 16)), viewModel.state.value.sheet)
        assertNull("no pill under a sheet", viewModel.journeyWindow.value!!.justEarned)

        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertNull("no recap after it either", viewModel.state.value.sheet)
        assertEquals(220, viewModel.journeyWindow.value!!.justEarned)
    }

    @Test
    fun `«В дорогу» of the recap closes it and goes home`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)

        viewModel.onIntent(PracticeIntent.RecapTravelClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals(PracticeEffect.OpenHome, effects.last())
    }

    @Test
    fun `a discarded practice and one too short are not recapped`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()
        assertNull(viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertTrue(journey.earnings.isEmpty())
    }
}
