package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.ReminderDay
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.events.Running
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.events.FakeEventRepository
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
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
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
import kotlinx.datetime.LocalDateTime
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
    private val events = FakeEventRepository()
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
        events: EventRepository = this@PracticeViewModelTest.events,
    ): Pair<PracticeViewModel, MutableList<PracticeEffect>> {
        val viewModel = PracticeViewModel(
            repository, store, finisher, sessions, config, repertoire, clock,
            trophies, profiles, avatarFiles, ProgressConfig(), journey, blocks = blocks, finishAsk = finishAsk, venues = Venues(FollowTheRoad, journey),
            journeyConfig = JourneyConfig(), analytics = NoOpAnalytics(), backings = NoBackings, events = events, eventsConfig = EventsConfig(),
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

    /** A lesson — or an event of [kind] — on [date] at [start] (minutes from midnight; null — «весь день») for [duration] minutes. */
    private fun event(id: Long, date: LocalDate, start: Int? = null, duration: Int? = null, kind: BuiltInKind = BuiltInKind.LESSON) =
        CalendarEvent(id, KindRef.BuiltIn(kind), date, start, duration, title = "", place = "", notes = "", seriesId = null, detached = false, createdAtEpochMs = id)

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

    /**
     * Moves the wall clock and the virtual time together in one step, for long spans: what sleeps until a moment inside the span wakes
     * on the clock of its end — right for a span that ends at the moment a test is about (a midnight, the 1st of a month).
     */
    private fun TestScope.jump(ms: Long) {
        runCurrent()
        clock.nowMs += ms
        advanceTimeBy(ms)
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

    /** Forward to max(the current month + 12, the month of the farthest event) (spec 5.28, 3.36.9; was: never past the current one). */
    @Test
    fun `months move back and forward up to twelve months ahead and further to the farthest event`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.MonthBack)
        viewModel.onIntent(PracticeIntent.MonthBack)
        runCurrent()
        assertEquals(YearMonth(2026, 7), viewModel.state.value.month)
        assertTrue(viewModel.state.value.canGoForward)
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth(2026, 8), viewModel.state.value.month)

        repeat(14) { viewModel.onIntent(PracticeIntent.MonthForward) }
        runCurrent()
        assertEquals("a year ahead and no further", YearMonth(2027, 9), viewModel.state.value.month)
        assertFalse(viewModel.state.value.canGoForward)

        // an event further on takes the calendar to its month, and no further
        events.events.value = listOf(event(1, LocalDate(2027, 11, 5), start = 17 * 60, duration = 45))
        runCurrent()
        assertTrue(viewModel.state.value.canGoForward)
        repeat(3) { viewModel.onIntent(PracticeIntent.MonthForward) }
        runCurrent()
        assertEquals(YearMonth(2027, 11), viewModel.state.value.month)
        assertFalse(viewModel.state.value.canGoForward)
        assertEquals(1, viewModel.state.value.monthEvents)
        assertTrue(viewModel.state.value.monthIsFuture)
    }

    /**
     * Back on the current month by either arrow, the calendar follows today again (spec 3.36.9): over midnight of the 30th it goes to the
     * new month by itself, as in R2 — not only when it came back by «вперёд».
     */
    @Test
    fun `back on the current month by either arrow the calendar follows today again`() = runTest {
        val lastMinuteOfSeptember = Instant.parse("2026-09-30T20:59:00Z").toEpochMilliseconds() // 23:59 in Moscow
        for ((first, second) in listOf(PracticeIntent.MonthForward to PracticeIntent.MonthBack, PracticeIntent.MonthBack to PracticeIntent.MonthForward)) {
            clock.nowMs = lastMinuteOfSeptember
            val (viewModel, _) = viewModel()
            viewModel.onIntent(first)
            viewModel.onIntent(second)
            runCurrent()
            assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
            pass(60_000)
            assertEquals("came back by $second: the calendar follows today into October", YearMonth(2026, 10), viewModel.state.value.month)
        }
    }

    @Test
    fun `a tapped day opens its sheet and a day to come opens its sheet too`() = runTest {
        val (viewModel, _) = viewModel()
        events.events.value = listOf(event(1, LocalDate(2026, 9, 18), start = 17 * 60, duration = 45))
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 18)))
        runCurrent()
        // spec 3.36.9: a day to come is selected and opens its sheet — its events, no time (it was not selectable before R9)
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 18)), viewModel.state.value.sheet)
        val tomorrow = viewModel.state.value.selected!!
        assertTrue(tomorrow.isFuture && tomorrow.isTomorrow)
        assertEquals(0L, tomorrow.totalMs)
        assertEquals(listOf(1L), tomorrow.events.map { it.eventId })
        assertTrue(viewModel.state.value.cells.filterNotNull().single { it.date == LocalDate(2026, 9, 18) }.isSelected)
        // the time of a day to come is not edited (spec 3.35): «Изменить» opens nothing over its sheet
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 18)), viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()

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
    fun `an event of the sheet of the day opens its screen and the sheet comes back with the screen, as for a record`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 16)))
        runCurrent()
        viewModel.onIntent(PracticeIntent.DayEventClicked(5))
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenEvent(5)), effects)
        assertTrue("the sheet steps aside while the event is on the screen", viewModel.state.value.sheetsAway)
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 16)), viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertFalse(viewModel.state.value.sheetsAway)
        assertEquals("«назад» from the event: the same sheet", PracticeSheet.Day(LocalDate(2026, 9, 16)), viewModel.state.value.sheet)
    }

    @Test
    fun `an event in this day opens the form with the date of the sheet once and the sheet comes back with it`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.NewEventClicked)
        runCurrent()
        assertTrue("no sheet — no day to make an event on", effects.isEmpty())

        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 28)))
        runCurrent()
        // a double tap: the second lands on a sheet that has stepped aside
        viewModel.onIntent(PracticeIntent.NewEventClicked)
        viewModel.onIntent(PracticeIntent.NewEventClicked)
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenEventForm(LocalDate(2026, 9, 28))), effects)
        assertTrue("the sheet steps aside while the form is on the screen", viewModel.state.value.sheetsAway)

        // ✕ of the form: the same sheet rises again
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertFalse(viewModel.state.value.sheetsAway)
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 28)), viewModel.state.value.sheet)
    }

    @Test
    fun `an event saved by the form brings the sheet of its date up on its month`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 28)))
        runCurrent()
        viewModel.onIntent(PracticeIntent.NewEventClicked)
        runCurrent()
        // the date was changed in the form: the event lies on 26 October
        viewModel.onIntent(PracticeIntent.EventSaved(LocalDate(2026, 10, 26)))
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(PracticeSheet.Day(LocalDate(2026, 10, 26)), state.sheet)
        assertEquals(YearMonth(2026, 10), state.month)
        assertFalse(state.sheetsAway)
        // a date of the current month: the calendar follows today again
        viewModel.onIntent(PracticeIntent.DayEventClicked(5))
        runCurrent()
        viewModel.onIntent(PracticeIntent.EventSaved(LocalDate(2026, 9, 30)))
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertEquals(PracticeSheet.Day(LocalDate(2026, 9, 30)), viewModel.state.value.sheet)
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
    }

    @Test
    fun `an event saved from the reminder brings up no sheet`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.ReminderEventClicked(5))
        runCurrent()
        viewModel.onIntent(PracticeIntent.EventSaved(LocalDate(2026, 10, 26)))
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
    }

    @Test
    fun `a row of the reminder opens the screen of its event and leaves the sheets alone`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.ReminderEventClicked(5))
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenEvent(5)), effects)
        assertNull(viewModel.state.value.sheet)
        assertFalse(viewModel.state.value.sheetsAway)
    }

    @Test
    fun `the records of the sheet of the day are named by their events`() = runTest {
        val event = SessionEvent(3, "Осенний концерт", today, KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null)
        events.recordEvents.value = mapOf(3L to event)
        val samples = List(60) { SessionSample(69, 1.0) }
        val analysis = SessionAnalyzer.analyze(samples, IntonationConfig())
        sessions.save(
            NewSession(
                startedAtEpochMs = clock.nowMs - MS_PER_HOUR, durationMs = 3_000, config = IntonationConfig(), samples = samples,
                metrics = analysis.metrics!!, previewZones = SessionAnalyzer.previewZones(analysis.segments, IntonationConfig()), audioPath = "a.m4a",
                eventId = 3,
            ),
        )
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(today))
        runCurrent()
        assertEquals(event, viewModel.state.value.selected!!.sessions.single().event)
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
    fun `every time the screen opens the repeats of events are laid ahead from today`() = runTest {
        val (viewModel, _) = viewModel()
        assertEquals("nothing is laid before the screen is resumed", emptyList<LocalDate>(), events.laidAhead)
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertEquals("spec 5.28: when «Занятия» open", listOf(today), events.laidAhead)

        // the next morning: the screen opened again lays from the new day
        clock.nowMs += 24 * MS_PER_HOUR
        viewModel.onIntent(PracticeIntent.Resumed)
        runCurrent()
        assertEquals(listOf(today, LocalDate(2026, 9, 18)), events.laidAhead)
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
        assertTrue("a year ahead is open (spec 5.28)", state.canGoForward)
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

    /**
     * The date can go back under an open sheet — a zone crossed westward over midnight, a clock set back by hand. The sheet of a day that is
     * now to come lives on as such a day's sheet (spec 3.36.9; until R9 the model closed it); «Время за день» over it gives its place back
     * to it, as «Отмена» would: the time of a day to come is not edited (spec 3.35). Nothing is stuck: the gift comes once the sheet goes.
     */
    @Test
    fun `the sheet of a day the date went back under lives on - only «Время за день» over it gives its place back`() = runTest {
        val (viewModel, _) = viewModel(watchState = false)
        var screen = backgroundScope.launch { viewModel.state.collect {} }

        // the screen in the background long enough for its state to stop, and the date goes back meanwhile
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
        assertEquals("the sheet of a day to come lives on", PracticeSheet.Day(today), back.sheet)
        assertTrue(back.selected!!.isFuture && back.selected!!.isTomorrow)
        assertNull("the gift still waits for it", back.gift)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals("nothing holds the gift any more", 1, viewModel.state.value.gift?.hours)
        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()

        // «Время за день» of a day that is to come now gives its place back to the sheet of that day
        viewModel.onIntent(PracticeIntent.DaySelected(yesterday))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(yesterday, (viewModel.state.value.sheet as PracticeSheet.EditTime).date)
        awayWhileTheDateGoesBack()
        assertEquals(LocalDate(2026, 9, 15), viewModel.state.value.today)
        assertEquals(PracticeSheet.Day(yesterday), viewModel.state.value.sheet)
        assertTrue(viewModel.state.value.selected!!.isFuture)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals("a late «Отмена» changes nothing, and «Изменить» of a day to come opens nothing", PracticeSheet.Day(yesterday), viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
        openPath(viewModel)
    }

    /** «ещё N» of the reminder (spec 3.36.9): the sheet of the day of the first event it stands for, and the calendar comes to its month. */
    @Test
    fun `a day selected with its month brings the calendar to that month`() = runTest {
        clock.nowMs = Instant.parse("2026-09-30T15:00:00Z").toEpochMilliseconds() // 18:00 in Moscow, the 30th
        val (viewModel, _) = viewModel()
        val tomorrow = LocalDate(2026, 10, 1)
        events.events.value = (1L..3L).map { event(it, tomorrow, start = (9 + it.toInt()) * 60, duration = 45) }
        runCurrent()
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
        val reminder = viewModel.state.value.reminder!!
        assertEquals(tomorrow, reminder.firstHiddenDate(compact = false))

        viewModel.onIntent(PracticeIntent.DaySelected(tomorrow, moveMonth = true))
        runCurrent()
        val state = viewModel.state.value
        assertEquals(PracticeSheet.Day(tomorrow), state.sheet)
        assertEquals("tomorrow is in the next month", YearMonth(2026, 10), state.month)
        assertTrue(state.cells.filterNotNull().single { it.date == tomorrow }.isSelected)
        assertEquals(3, state.selected!!.events.size)
        viewModel.onIntent(PracticeIntent.DayHidden)
        viewModel.onIntent(PracticeIntent.MonthBack)
        runCurrent()
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)

        // a day of the current month moves nothing; one tapped in the calendar keeps the month shown
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate(2026, 9, 30), moveMonth = true))
        runCurrent()
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
        viewModel.onIntent(PracticeIntent.DayHidden)
        viewModel.onIntent(PracticeIntent.DaySelected(tomorrow))
        runCurrent()
        assertEquals(PracticeSheet.Day(tomorrow), viewModel.state.value.sheet)
        assertEquals(YearMonth(2026, 9), viewModel.state.value.month)
    }

    /**
     * The reminder is worked out again at the start and the end of the events it holds and at midnight (spec 3.36.9, plan D9), from the
     * moment of its clock: «идёт, до 17:45» comes at 17:00, the card goes at 17:45 — while the screen is open, by itself.
     */
    @Test
    fun `the reminder changes at the start and the end of its event by itself`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T13:30:00Z").toEpochMilliseconds() // 16:30 in Moscow
        events.events.value = listOf(event(1, today, start = 17 * 60, duration = 45))
        val (viewModel, _) = viewModel()
        val before = viewModel.state.value.reminder!!.rows.single()
        assertEquals(ReminderDay.TODAY, before.day)
        assertNull("not begun", before.running)
        val epoch = viewModel.state.value.reminderEpoch

        pass(30 * 60_000L - 1_000)
        assertNull("16:59:59", viewModel.state.value.reminder!!.rows.single().running)
        pass(1_000)
        assertEquals(Running.Until(LocalDateTime(2026, 9, 17, 17, 45)), viewModel.state.value.reminder!!.rows.single().running)
        pass(45 * 60_000L - 1_000)
        assertNotNull("17:44:59: still there", viewModel.state.value.reminder)
        pass(1_000)
        assertNull("over at 17:45: the card goes by itself", viewModel.state.value.reminder)
        // seen on the open screen: the card fades out (spec 3.36.9, «Движение») — no change counted as unseen
        assertEquals(epoch, viewModel.state.value.reminderEpoch)
    }

    /**
     * A card of an event that ended while the screen was away (spec 3.36.9: «Пересчёт — при открытии «Занятий»», the fade of 300 ms only
     * on the open screen; the review of stage 97): nothing is worked out in the background — the state is held as it was — but the
     * route reckons the reminder as the screen opens, before its first frame reads the state: that frame has no card any more, so the
     * screen does not open on «идёт, до 17:45» to fade it out and move the window of the home after it; and the change counts as
     * unseen, so the screen takes it at once. The state worked out anew when the screen watches again has nothing new.
     */
    @Test
    fun `a card over while the screen was away is gone in the first state of its return`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T14:30:00Z").toEpochMilliseconds() // 17:30 in Moscow
        events.events.value = listOf(event(1, today, start = 17 * 60, duration = 45))
        val (viewModel, _) = viewModel(watchState = false)
        var screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(Running.Until(LocalDateTime(2026, 9, 17, 17, 45)), viewModel.state.value.reminder!!.rows.single().running)
        val epoch = viewModel.state.value.reminderEpoch

        // away until 18:00 — long past the stop of the state 5 s after the screen went; the lesson ends at 17:45 meanwhile
        screen.cancel()
        pass(30 * 60_000L)
        assertNotNull("held as it was: nothing is worked out while the screen is away", viewModel.state.value.reminder)
        viewModel.onIntent(PracticeIntent.Opened)
        val held = viewModel.state.value
        assertNull("the first frame of the return has no card of a lesson over", held.reminder)
        assertEquals("a change the screen did not see", epoch + 1, held.reminderEpoch)

        screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertNull(viewModel.state.value.reminder)
        assertEquals("worked out anew: the same — nothing new", epoch + 1, viewModel.state.value.reminderEpoch)
        assertFalse(viewModel.state.value.loading)
    }

    /** The other way (the review of stage 97): tomorrow's lesson comes into the card at midnight while the screen is away — it is there in the first frame of the return, taken at once. */
    @Test
    fun `a card that came while the screen was away is there in the first state of its return`() = runTest {
        clock.nowMs = Instant.parse("2026-09-16T20:59:00Z").toEpochMilliseconds() // 23:59 on the 16th in Moscow
        events.events.value = listOf(event(1, LocalDate(2026, 9, 18), start = 17 * 60, duration = 45))
        val (viewModel, _) = viewModel(watchState = false)
        var screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertNull("two days before its lesson: no card", viewModel.state.value.reminder)
        val epoch = viewModel.state.value.reminderEpoch

        screen.cancel()
        pass(10 * 60_000L)
        viewModel.onIntent(PracticeIntent.Opened)
        val held = viewModel.state.value
        assertEquals("from 00:00 of the day before", ReminderDay.TOMORROW, held.reminder!!.rows.single().day)
        assertEquals(epoch + 1, held.reminderEpoch)

        screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(ReminderDay.TOMORROW, viewModel.state.value.reminder!!.rows.single().day)
        assertEquals(epoch + 1, viewModel.state.value.reminderEpoch)
    }

    /**
     * The state is worked out for 5 s after the screen went (as `stateIn(WhileSubscribed)` did): a card over in those seconds is a change
     * the screen did not see either, taken at once when it is back.
     */
    @Test
    fun `a card over in the seconds after the screen went is taken at once too`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T14:44:58Z").toEpochMilliseconds() // 17:44:58 in Moscow
        events.events.value = listOf(event(1, today, start = 17 * 60, duration = 45))
        val (viewModel, _) = viewModel(watchState = false)
        val screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertNotNull(viewModel.state.value.reminder)
        val epoch = viewModel.state.value.reminderEpoch

        screen.cancel()
        pass(3_000)
        assertNull("17:45:01: over", viewModel.state.value.reminder)
        assertEquals("seen by no one", epoch + 1, viewModel.state.value.reminderEpoch)
    }

    /**
     * The first state worked out when the screen watches again is a reckoning at the opening of «Занятия» too (spec 3.36.9): a change of
     * the reminder it brings is taken at once, as one [PracticeIntent.Opened] finds. Here nothing reckoned the held card — the screen
     * collects the state without opening — and the clock was set an hour forward meanwhile.
     */
    @Test
    fun `what the first state at the opening changes is taken at once`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T14:30:00Z").toEpochMilliseconds() // 17:30 in Moscow
        events.events.value = listOf(event(1, today, start = 17 * 60, duration = 45))
        val (viewModel, _) = viewModel(watchState = false)
        var screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        val epoch = viewModel.state.value.reminderEpoch

        screen.cancel()
        pass(10_000)
        clock.nowMs += MS_PER_HOUR
        assertNotNull("the card held as it was", viewModel.state.value.reminder)
        screen = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertNull("18:30 by the clock: over", viewModel.state.value.reminder)
        assertEquals("taken at once", epoch + 1, viewModel.state.value.reminderEpoch)
    }

    /**
     * The pill waits on the state for the gift to be answered (spec 3.31) and so keeps it worked out after the screen went; a change of
     * the reminder then is still one the screen did not see — taken at once on the return (the review of stage 97).
     */
    @Test
    fun `a card that changes while only the pill waits on the state is taken at once too`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T13:00:00Z").toEpochMilliseconds() // 16:00 in Moscow
        events.events.value = listOf(event(1, today, start = 17 * 60 + 10, duration = 45))
        journey.start(clock.millis())
        val (viewModel, _) = viewModel(watchState = false)
        val screen = backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(62 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        trophies.award(1, today) // the hour the practice has just crossed
        runCurrent()
        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        try {
            assertEquals("the gift holds the pill", 1, viewModel.state.value.gift?.hours)
            assertNull("17:02: not begun", viewModel.state.value.reminder!!.rows.single().running)
            val epoch = viewModel.state.value.reminderEpoch

            // the screen goes; the pill still waits on the state, which stays worked out
            screen.cancel()
            pass(10 * MS_PER_MINUTE)
            assertNotNull("17:12: begun", viewModel.state.value.reminder!!.rows.single().running)
            assertEquals("begun out of sight", epoch + 1, viewModel.state.value.reminderEpoch)
        } finally {
            // the gift answered, the pill stops waiting — whatever failed above: a state worked out for ever would keep the test from
            // ending (its days pass on the virtual clock one midnight after another)
            viewModel.onIntent(PracticeIntent.GiftAccepted(1))
            pass(JourneyMotion.EARNED_PILL_MS + 1)
        }
    }

    /**
     * A month put in place that becomes the current one by itself follows today again (spec 3.36.9; the review of stage 97): on the 30th
     * «ещё 1» of a lesson on the 1st puts the calendar on October, and so does the arrow forward; after midnight October is the current
     * month, and on the 1st of November the calendar is on November by itself, as in R2 — not held on October as a month put by hand.
     */
    @Test
    fun `a month put in place that becomes the current one follows today again`() = runTest {
        val first = LocalDate(2026, 10, 1)
        val ways: List<List<PracticeIntent>> = listOf(
            listOf(PracticeIntent.DaySelected(first, moveMonth = true), PracticeIntent.DayHidden),
            listOf(PracticeIntent.MonthForward),
        )
        for (way in ways) {
            clock.nowMs = Instant.parse("2026-09-30T15:00:00Z").toEpochMilliseconds() // 18:00 on the 30th in Moscow
            events.events.value = (1L..3L).map { event(it, first, start = (9 + it.toInt()) * 60, duration = 45) }
            val (viewModel, _) = viewModel()
            way.forEach(viewModel::onIntent)
            runCurrent()
            assertEquals("$way", YearMonth(2026, 10), viewModel.state.value.month)

            jump(6 * MS_PER_HOUR)
            assertEquals(first, viewModel.state.value.today)
            assertEquals(YearMonth(2026, 10), viewModel.state.value.month)
            jump(31 * 24 * MS_PER_HOUR)
            val november = viewModel.state.value
            assertEquals(LocalDate(2026, 11, 1), november.today)
            assertEquals("$way: the calendar follows today into November", YearMonth(2026, 11), november.month)
            assertTrue(november.cells.filterNotNull().single { it.date == LocalDate(2026, 11, 1) }.isToday)
        }
    }

    /**
     * The legend follows the order of the form (spec 3.36.9): the built-in kinds, then those of one's own by the alphabet of the interface
     * — not in the order they were made in, the order the storage gives them (the review of stage 97).
     */
    @Test
    fun `the legend has the built-in kinds and then ones own by the alphabet`() = runTest {
        // «Оркестр» was made before «Мастер-класс»
        events.storedKinds.value = listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1), StoredKind.Own(10, "Мастер-класс", 4, KindSign.BOLT, 2))
        events.events.value = listOf(
            CalendarEvent(1, KindRef.Custom(9), LocalDate(2026, 9, 20), 11 * 60, 120, "", "", "", null, detached = false, createdAtEpochMs = 1),
            CalendarEvent(2, KindRef.Custom(10), LocalDate(2026, 9, 21), 14 * 60, null, "", "", "", null, detached = false, createdAtEpochMs = 2),
            event(3, LocalDate(2026, 9, 22), start = 17 * 60, duration = 45),
        )
        val (viewModel, _) = viewModel()
        assertEquals(
            listOf(KindRef.BuiltIn(BuiltInKind.LESSON), KindRef.Custom(10), KindRef.Custom(9)),
            viewModel.state.value.legend.map { it.ref },
        )
    }

    /** Midnight with the sheet of tomorrow open (spec 3.36.9): it becomes the sheet of today — time and «Изменить», no «завтра». */
    @Test
    fun `at midnight the sheet of tomorrow becomes the sheet of today`() = runTest {
        clock.nowMs = Instant.parse("2026-09-17T20:59:30Z").toEpochMilliseconds() // 23:59:30 in Moscow
        val (viewModel, _) = viewModel()
        val tomorrow = LocalDate(2026, 9, 18)
        viewModel.onIntent(PracticeIntent.DaySelected(tomorrow))
        runCurrent()
        assertTrue(viewModel.state.value.selected!!.let { it.isTomorrow && it.isFuture })
        pass(60_000)
        val day = viewModel.state.value.selected!!
        assertEquals(tomorrow, day.date)
        assertTrue(day.isToday)
        assertFalse(day.isFuture || day.isTomorrow)
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals("its time can be edited now", tomorrow, (viewModel.state.value.sheet as PracticeSheet.EditTime).date)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        viewModel.onIntent(PracticeIntent.DayHidden)
        runCurrent()
    }

    /** The reminder comes with «Сегодня» (spec 3.36.9, «Загрузка»): until the events are read the screen stays loading. */
    @Test
    fun `the first state waits for the events`() = runTest {
        val read = MutableStateFlow(false)
        val slow = object : EventRepository by events {
            override val events: Flow<List<CalendarEvent>> = read.filter { it }.flatMapLatest { this@PracticeViewModelTest.events.events }
        }
        val (viewModel, _) = viewModel(events = slow)
        assertTrue("the events are not read yet", viewModel.state.value.loading)
        read.value = true
        runCurrent()
        assertFalse(viewModel.state.value.loading)
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
