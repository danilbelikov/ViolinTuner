package com.violinjourney.app.feature.events

import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.time.MutableWallClock
import com.violinjourney.app.feature.events.performances.PerformancesEffect
import com.violinjourney.app.feature.events.performances.PerformancesIntent
import com.violinjourney.app.feature.events.performances.PerformancesState
import com.violinjourney.app.feature.events.performances.PerformancesViewModel
import kotlin.time.Instant
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * «Выступления» (spec 3.35, 3.36.9): «Впереди» and «Прошли» with what each row shows, a press that leaves the screen once, and the list
 * that moves on by itself at the end of a concert and at midnight — on the virtual time of the test ([pass]), as the clock of a phone
 * and the sleep of [com.violinjourney.app.core.time.ticksAt] go together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerformancesViewModelTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val config = EventsConfig()

    // Saturday 24 October 2026, 18:00 in Moscow: the day of the autumn concert, at 18:30 for an hour and a half
    private val today = LocalDate(2026, 10, 24)
    private val clock = MutableWallClock(Instant.parse("2026-10-24T15:00:00Z").toEpochMilliseconds(), moscow)
    private val events = FakeEventRepository()
    private val sessions = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): PerformancesViewModel {
        val viewModel = PerformancesViewModel(events, sessions, repertoire, config, clock, background = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: PerformancesViewModel): List<PerformancesEffect> {
        val effects = mutableListOf<PerformancesEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        return effects
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

    /** Moves the wall clock and the virtual time together in one step, for a span that ends at the moment a test is about. */
    private fun TestScope.jump(ms: Long) {
        runCurrent()
        clock.nowMs += ms
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun performance(id: Long, date: LocalDate, start: Int? = null, duration: Int? = null, title: String = "", place: String = "") =
        CalendarEvent(id, KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date, start, duration, title, place, notes = "", seriesId = null, detached = false, createdAtEpochMs = id)

    private fun recording(id: Long, eventId: Long?, startedAt: Long, video: String? = null) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 220_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 4.0, biasCents = 1.0, previewZones = listOf(Zone.IN_TUNE),
        audioPath = "$id.m4a", videoPath = video, eventId = eventId,
    )

    private val concert = performance(1, today, start = 18 * 60 + 30, duration = 90, title = "Осенний концерт", place = "Малый зал музыкальной школы")
    private val round = performance(2, LocalDate(2026, 11, 20), title = "Отборочный тур", place = "Москва, Малый зал консерватории")
    private val academic = performance(3, LocalDate(2026, 9, 13), start = 15 * 60, duration = 60, title = "Академический концерт")
    private val exam = performance(4, LocalDate(2026, 5, 18), start = 11 * 60, duration = 45, title = "Экзамен, 4 класс")
    private val lesson = CalendarEvent(5, KindRef.BuiltIn(BuiltInKind.LESSON), today, 17 * 60, 45, "", "", "", null, false, 5)

    private fun PerformancesViewModel.loaded(): PerformancesState = state.value.also { assertTrue("loaded", !it.loading) }

    /**
     * Spec 3.36.9: «Впереди» the nearest first — the concert of today until it is over, with «сегодня» — «Прошли» the freshest first;
     * a row says its programme in one line, and at its end the thumbnail of the newest video, the number of its recordings, or nothing.
     */
    @Test
    fun `ahead the nearest first and over the freshest first with what each row shows`() = runTest {
        val vivaldi = repertoire.add(PieceDraft(title = "Концерт ля минор, 1 ч.", composer = "А. Вивальди"), nowEpochMs = 1)
        val melody = repertoire.add(PieceDraft(title = "Мелодия", composer = "П. Чайковский"), nowEpochMs = 2)
        events.events.value = listOf(exam, lesson, round, concert, academic)
        // the programme of tonight in an order other than that of the repertoire: the row says it in its own order
        events.programs.value = mapOf(1L to listOf(melody, vivaldi), 3L to listOf(vivaldi))
        sessions.sessions.value = listOf(
            recording(11, eventId = 3, startedAt = 1_789_300_000_000, video = "academic-1.mp4"),
            // the thumbnail as the store found it (spec 3.38): of the newest video, the one at the end of the row
            recording(12, eventId = 3, startedAt = 1_789_300_600_000, video = "academic-2.mp4").copy(thumbPath = "/files/sessions/academic-2-thumb.jpg"),
            recording(13, eventId = 4, startedAt = 1_779_000_000_000),
            recording(14, eventId = 4, startedAt = 1_779_000_100_000),
            recording(15, eventId = null, startedAt = 1_789_300_000_000),
        )
        val viewModel = viewModel()
        assertTrue("the bar and an empty zone while the events are read", viewModel.state.value.loading)
        runCurrent()

        val state = viewModel.loaded()
        assertEquals(listOf(1L, 2L), state.ahead.map { it.row.eventId })
        assertEquals(listOf(3L, 4L), state.past.map { it.row.eventId })
        val tonight = state.ahead[0].row
        assertEquals(0, tonight.days)
        assertEquals(listOf("П. Чайковский", "А. Вивальди"), tonight.program)
        assertEquals(EventName.Titled("Осенний концерт"), tonight.name)
        assertEquals(27, state.ahead[1].row.days)
        assertNull("the round is all day", state.ahead[1].row.startMinutes)
        assertEquals("the round has no programme — not another's", emptyList<String>(), state.ahead[1].row.program)
        assertEquals("the academic concert has its own", listOf("А. Вивальди"), state.past[0].row.program)
        assertEquals(emptyList<String>(), state.past[1].row.program)
        assertEquals("/files/sessions/academic-2-thumb.jpg", state.past[0].thumbPath)
        assertEquals(2, state.past[0].row.records)
        assertNull("recordings without a video: their number", state.past[1].thumbPath)
        assertEquals(2, state.past[1].row.records)
        assertNull("no recording: nothing", state.ahead[0].thumbPath)
        assertEquals(0, state.ahead[0].row.records)
    }

    /** Spec 3.36.9, «Пусто»: nothing yet is a state of its own — not a list being read. */
    @Test
    fun `no performance at all is empty and not loading`() = runTest {
        events.events.value = listOf(lesson)
        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.loaded().empty)
    }

    /** Spec 3.35: «Выступление» can be given another colour — its plates and chips follow it. */
    @Test
    fun `the plates take the colour the kind was given`() = runTest {
        events.events.value = listOf(concert)
        val viewModel = viewModel()
        runCurrent()
        assertEquals(config.defaultColorOf(BuiltInKind.PERFORMANCE), viewModel.loaded().look.color)
        events.storedKinds.value = listOf(StoredKind.Recolor(BuiltInKind.PERFORMANCE, 5))
        runCurrent()
        assertEquals(5, viewModel.loaded().look.color)
    }

    /**
     * A press leaves the screen once (the lesson of stage 120): two taps of a row open one event, «Добавить выступление» tapped while the
     * screen is on its way to it opens nothing more; back in sight, the presses count again; a double «назад» goes back once.
     */
    @Test
    fun `a row opens its event and the button the form of a new one - once for a double tap`() = runTest {
        events.events.value = listOf(concert, academic)
        val viewModel = viewModel()
        runCurrent()
        val effects = effectsOf(viewModel)

        viewModel.onIntent(PerformancesIntent.RowClicked(1))
        viewModel.onIntent(PerformancesIntent.RowClicked(1))
        viewModel.onIntent(PerformancesIntent.AddClicked)
        runCurrent()
        assertEquals(listOf<PerformancesEffect>(PerformancesEffect.OpenEvent(1)), effects)

        viewModel.onIntent(PerformancesIntent.Shown)
        viewModel.onIntent(PerformancesIntent.AddClicked)
        viewModel.onIntent(PerformancesIntent.AddClicked)
        runCurrent()
        assertEquals(listOf(PerformancesEffect.OpenEvent(1), PerformancesEffect.OpenNewPerformance), effects)

        viewModel.onIntent(PerformancesIntent.Shown)
        viewModel.onIntent(PerformancesIntent.BackClicked)
        viewModel.onIntent(PerformancesIntent.BackClicked)
        runCurrent()
        assertEquals(listOf(PerformancesEffect.OpenEvent(1), PerformancesEffect.OpenNewPerformance, PerformancesEffect.Close), effects)
    }

    /** Plan D42: the concert of today goes over to «Прошли» at its end while the screen is open, not at the next opening. */
    @Test
    fun `the concert of today goes over at its end by itself`() = runTest {
        clock.nowMs = Instant.parse("2026-10-24T16:59:58Z").toEpochMilliseconds() // 19:59:58 in Moscow
        events.events.value = listOf(concert, academic)
        val viewModel = viewModel()
        runCurrent()
        assertEquals(listOf(1L), viewModel.loaded().ahead.map { it.row.eventId })

        pass(1_000)
        assertEquals("19:59:59 — still ahead", listOf(1L), viewModel.loaded().ahead.map { it.row.eventId })
        pass(1_000)
        assertEquals("20:00 — over", emptyList<Long>(), viewModel.loaded().ahead.map { it.row.eventId })
        assertEquals("the freshest of «Прошли»", listOf(1L, 3L), viewModel.loaded().past.map { it.row.eventId })
        assertNull("over — no term", viewModel.loaded().past[0].row.days)
    }

    /** Plan D42: at midnight the terms move on — «завтра» turns into «сегодня» — with nothing else changed. */
    @Test
    fun `at midnight the term of a performance moves on`() = runTest {
        clock.nowMs = Instant.parse("2026-10-24T20:59:00Z").toEpochMilliseconds() // 23:59 in Moscow
        val tomorrow = performance(6, LocalDate(2026, 10, 25), start = 12 * 60, duration = 60, title = "Утренник")
        events.events.value = listOf(tomorrow)
        val viewModel = viewModel()
        runCurrent()
        assertEquals(1, viewModel.loaded().ahead.single().row.days)

        jump(60_000) // midnight
        assertEquals(0, viewModel.loaded().ahead.single().row.days)
    }
}
