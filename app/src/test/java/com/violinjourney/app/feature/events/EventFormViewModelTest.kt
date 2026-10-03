package com.violinjourney.app.feature.events

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventPlan
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventStep
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.time.MutableWallClock
import com.violinjourney.app.feature.events.form.EventFormDialog
import com.violinjourney.app.feature.events.form.EventFormEffect
import com.violinjourney.app.feature.events.form.EventFormIntent
import com.violinjourney.app.feature.events.form.EventFormSheet
import com.violinjourney.app.feature.events.form.EventFormState
import com.violinjourney.app.feature.events.form.EventFormViewModel
import com.violinjourney.app.feature.events.screen.SeriesWord
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form of an event (spec 3.35, 3.36.9; plan 7.4, 7.8): Sunday 27 September 2026, 18:42 in Moscow; the lessons with Анна Сергеевна on
 * Mondays at 17:00 for 45 minutes from 28 September (the repeat 1, laid to 20 December — the events 1 … 12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventFormViewModelTest {
    private val config = EventsConfig()
    private val zone = TimeZone.of("Europe/Moscow")
    private val clock = MutableWallClock(Instant.parse("2026-09-27T15:42:00Z").toEpochMilliseconds(), zone)
    private val events = FakeEventRepository(config)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val first = LocalDate(2026, 9, 28)

    /** The words of an event as the resources would say them: in a JVM test there are none to read. */
    private val words = object : EventWords {
        override suspend fun nameOf(name: EventName): String = when (name) {
            is EventName.Titled -> name.title
            is EventName.OfKind -> when (name.kind) {
                KindRef.BuiltIn(BuiltInKind.LESSON) -> "Урок"
                KindRef.BuiltIn(BuiltInKind.REHEARSAL) -> "Репетиция"
                KindRef.BuiltIn(BuiltInKind.PERFORMANCE) -> "Выступление"
                else -> name.ownName ?: "Другое"
            }
        }

        override suspend fun recordTitleOf(event: SessionEvent): String = "${nameOf(event.name)} · ${event.date}"
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun lessons(): List<CalendarEvent> = (0 until 12).map { week ->
        CalendarEvent(
            id = week + 1L, kind = lesson, date = first.plus(7 * week, DateTimeUnit.DAY), startMinutes = 17 * 60, durationMinutes = 45, title = "",
            place = "Анна Сергеевна", notes = "", seriesId = 1, detached = false, createdAtEpochMs = 100,
        )
    }

    private val repeat = EventSeries(1, lesson, Repeat.WEEKLY, first, null, LocalDate(2026, 12, 20), 17 * 60, 45, "", "Анна Сергеевна")

    private fun withLessons() {
        events.events.value = lessons()
        events.series.value = listOf(repeat)
    }

    /** The lessons, and after them a concert on 24 October at 19:00 for 2 hours — the event created last (D28, D49). */
    private fun withConcertAfterTheLessons() {
        withLessons()
        events.events.value = events.events.value + CalendarEvent(
            id = 50, kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = LocalDate(2026, 10, 24), startMinutes = 19 * 60, durationMinutes = 120,
            title = "Осенний концерт", place = "", notes = "", seriesId = null, detached = false, createdAtEpochMs = 200,
        )
    }

    private fun handleOf(eventId: Long? = null, date: LocalDate? = null, kind: BuiltInKind? = null, focusNotes: Boolean = false) = SavedStateHandle(
        mapOf(
            EventFormViewModel.ARG_EVENT_ID to (eventId ?: EventFormViewModel.NEW_EVENT),
            EventFormViewModel.ARG_DATE to (date?.toString() ?: ""),
            EventFormViewModel.ARG_KIND to (kind?.name ?: ""),
            EventFormViewModel.ARG_FOCUS_NOTES to focusNotes,
        ),
    )

    /**
     * A form and what it sent. [on] — a press, and what it set going done before the next one, as a finger leaves the time of a frame;
     * [tap] — a press with nothing done after it: the second tap of a double tap lands before the first has been answered.
     */
    private class Form(val viewModel: EventFormViewModel, val effects: MutableList<EventFormEffect>, private val scope: TestScope) {
        val state: EventFormState get() = viewModel.state.value

        fun on(intent: EventFormIntent) {
            viewModel.onIntent(intent)
            scope.runCurrent()
        }

        fun tap(intent: EventFormIntent) = viewModel.onIntent(intent)
    }

    /** [saved] shared by two view models stands for a process the system ended and brought back. */
    private fun TestScope.form(saved: SavedStateHandle): Form {
        val viewModel = EventFormViewModel(saved, events, config, clock, words)
        val effects = mutableListOf<EventFormEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return Form(viewModel, effects, this)
    }

    @Test
    fun `a new event of a day of the calendar is saved once and its screen opens with its date`() = runTest {
        withLessons()
        val form = form(handleOf(date = LocalDate(2026, 10, 5)))
        assertFalse(form.state.loading)
        val draft = form.state.draft
        assertEquals("the kind of the last event created", lesson, draft.kind)
        assertEquals("the time of the last lesson — the repeat's", 17 * 60, draft.startMinutes)
        assertEquals(45, draft.durationMinutes)
        assertEquals(LocalDate(2026, 10, 5), draft.date)

        form.on(EventFormIntent.RepeatSelected(Repeat.WEEKLY))
        form.on(EventFormIntent.UntilClicked)
        form.on(EventFormIntent.UntilPicked(LocalDate(2026, 12, 31)))
        form.on(EventFormIntent.SheetDone)
        form.on(EventFormIntent.PlaceChanged("Ирина Петровна"))
        form.tap(EventFormIntent.SaveClicked)
        // a double tap: the second press finds the form leaving
        form.tap(EventFormIntent.SaveClicked)
        runCurrent()

        // the fake counts its ids from 1 whatever it was given: the new event is the one with the new teacher, its repeat the one added
        val created = events.events.value.filter { it.place == "Ирина Петровна" }
        assertEquals("one event, not two", 1, created.size)
        assertEquals("a repeat added beside the one of Анна Сергеевна", 2, events.series.value.size)
        val added = events.series.value.last()
        assertNotNull("the event belongs to its repeat", created.single().seriesId)
        assertEquals(Repeat.WEEKLY, added.repeat)
        assertEquals(LocalDate(2026, 10, 5), added.firstDate)
        assertEquals("the end chosen in «Повторять до»", LocalDate(2026, 12, 31), added.until)
        assertEquals(listOf(EventFormEffect.OpenCreated(created.single().id, LocalDate(2026, 10, 5))), form.effects)
    }

    /**
     * spec 3.36.9 «Недельный урок — четыре касания»: a concert created after the lessons makes the new form a performance at 19:00 for 2
     * hours (D28, D49); the tile «Урок» brings the time of the last lesson — 17:00 for 45 minutes — and the lesson is saved at it.
     */
    @Test
    fun `the tile of a lesson brings the time of the last lesson to a new event`() = runTest {
        withConcertAfterTheLessons()
        val form = form(handleOf(date = LocalDate(2026, 10, 5)))
        assertEquals(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), form.state.draft.kind)
        assertEquals(19 * 60, form.state.draft.startMinutes)
        assertEquals(120, form.state.draft.durationMinutes)

        form.on(EventFormIntent.KindPicked(lesson))
        assertEquals(lesson, form.state.draft.kind)
        assertEquals("the start of the last lesson", 17 * 60, form.state.draft.startMinutes)
        assertEquals(45, form.state.draft.durationMinutes)
        form.on(EventFormIntent.RepeatSelected(Repeat.WEEKLY))
        form.on(EventFormIntent.PlaceChanged("Ирина Петровна"))
        form.on(EventFormIntent.SaveClicked)
        runCurrent()
        val created = events.events.value.single { it.place == "Ирина Петровна" }
        assertEquals(17 * 60, created.startMinutes)
        assertEquals(45, created.durationMinutes)
    }

    @Test
    fun `a time the person chose stays with another tile and an edit keeps its time`() = runTest {
        withConcertAfterTheLessons()
        val form = form(handleOf(date = LocalDate(2026, 10, 5)))
        form.on(EventFormIntent.TimeRowClicked)
        form.on(EventFormIntent.TimeHour(18))
        form.on(EventFormIntent.SheetDone)
        form.on(EventFormIntent.KindPicked(lesson))
        assertEquals("the time chosen stays", 18 * 60, form.state.draft.startMinutes)
        assertEquals(120, form.state.draft.durationMinutes)

        val chip = form(handleOf(date = LocalDate(2026, 10, 5)))
        chip.on(EventFormIntent.DurationPicked(60))
        chip.on(EventFormIntent.KindPicked(lesson))
        assertEquals("a chip of the length is a choice too", 60, chip.state.draft.durationMinutes)
        assertEquals(19 * 60, chip.state.draft.startMinutes)

        val edit = form(handleOf(eventId = 4))
        edit.on(EventFormIntent.KindPicked(KindRef.BuiltIn(BuiltInKind.PERFORMANCE)))
        assertEquals(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), edit.state.draft.kind)
        assertEquals("a lesson made a performance does not move", 17 * 60, edit.state.draft.startMinutes)
        assertEquals(45, edit.state.draft.durationMinutes)
    }

    /** The time chosen is the person's after the system ended the process too: another tile does not take it back. */
    @Test
    fun `a time the person chose outlives the process`() = runTest {
        withConcertAfterTheLessons()
        val saved = handleOf(date = LocalDate(2026, 10, 5))
        val form = form(saved)
        form.on(EventFormIntent.TimeRowClicked)
        form.on(EventFormIntent.TimeHour(18))
        form.on(EventFormIntent.SheetDone)

        val again = form(saved)
        again.on(EventFormIntent.KindPicked(lesson))
        assertEquals(18 * 60, again.state.draft.startMinutes)
        assertEquals(120, again.state.draft.durationMinutes)
    }

    @Test
    fun `a new kind of ones own chosen in a new form has no time yet - the whole day`() = runTest {
        withConcertAfterTheLessons()
        val form = form(handleOf(date = LocalDate(2026, 10, 5)))
        form.on(EventFormIntent.OwnKindClicked)
        form.on(EventFormIntent.KindNameChanged("Сольфеджио"))
        runCurrent()
        form.on(EventFormIntent.SheetDone)
        runCurrent()
        assertTrue(form.state.draft.kind is KindRef.Custom)
        assertNull("no event of the kind — «весь день»", form.state.draft.startMinutes)
        assertNull(form.state.draft.durationMinutes)
    }

    @Test
    fun `a tile chooses its kind and the second tap of the chosen one opens the sheet of the kind`() = runTest {
        withLessons()
        val form = form(handleOf(date = first))
        val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
        form.on(EventFormIntent.KindPicked(performance))
        assertEquals(performance, form.state.draft.kind)
        assertNull("the first tap only chooses", form.state.sheet)
        form.on(EventFormIntent.KindPicked(performance))
        assertEquals(BuiltInKind.PERFORMANCE, (form.state.sheet as EventFormSheet.Kind).builtIn)
    }

    @Test
    fun `a new performance of the list of performances is of that kind on today`() = runTest {
        withLessons()
        val form = form(handleOf(kind = BuiltInKind.PERFORMANCE))
        assertEquals(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), form.state.draft.kind)
        assertEquals(LocalDate(2026, 9, 27), form.state.draft.date)
        assertNull("no performance yet — «весь день»", form.state.draft.startMinutes)
    }

    @Test
    fun `a new time of a lesson of a repeat asks the sheet with this and following filled - the answer is carried out once`() = runTest {
        withLessons()
        val form = form(handleOf(eventId = 4))
        assertTrue(form.state.inSeries)
        form.on(EventFormIntent.TimeRowClicked)
        form.on(EventFormIntent.TimeMinute(30))
        form.on(EventFormIntent.SheetDone)
        assertEquals(17 * 60 + 30, form.state.draft.startMinutes)
        form.on(EventFormIntent.SaveClicked)
        runCurrent()

        val sheet = form.state.sheet as EventFormSheet.Scope
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), sheet.ask.question)
        assertEquals(SeriesWord.LESSON, sheet.ask.word)
        assertEquals(LocalDate(2026, 10, 19), sheet.ask.date)
        assertNull("the date did not change", sheet.ask.moved)
        assertEquals("19, 26 октября and on", listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), sheet.ask.following.dates)
        assertTrue(sheet.ask.following.andOn)
        assertTrue("nothing is carried out before the answer", events.applied.isEmpty())

        form.tap(EventFormIntent.ScopeAnswered(EditScope.FOLLOWING))
        form.tap(EventFormIntent.ScopeAnswered(EditScope.FOLLOWING))
        runCurrent()
        assertEquals("one plan, not two", 1, events.applied.size)
        val step = events.applied.single().first.steps.single() as EventStep.UpdateFollowing
        assertEquals(LocalDate(2026, 10, 19), step.cut)
        assertEquals(4L, step.editedId)
        assertEquals(17 * 60 + 30, step.template.startMinutes)
        assertEquals(listOf(EventFormEffect.Saved(LocalDate(2026, 10, 19))), form.effects)
    }

    @Test
    fun `a new date asks with only this filled and only this detaches the lesson`() = runTest {
        withLessons()
        val form = form(handleOf(eventId = 4))
        form.on(EventFormIntent.DateRowClicked)
        form.on(EventFormIntent.DatePicked(LocalDate(2026, 10, 20)))
        form.on(EventFormIntent.SheetDone)
        form.on(EventFormIntent.SaveClicked)
        runCurrent()
        val sheet = form.state.sheet as EventFormSheet.Scope
        assertEquals(ScopeQuestion.Both(EditScope.ONLY_THIS), sheet.ask.question)
        val moved = sheet.ask.moved!!
        assertEquals(LocalDate(2026, 10, 20), moved.date)
        assertEquals("the rest of the repeat — on Mondays at 17:00", kotlinx.datetime.DayOfWeek.MONDAY, moved.restWeekday)
        assertEquals(17 * 60, moved.restStartMinutes)

        form.on(EventFormIntent.ScopeAnswered(EditScope.ONLY_THIS))
        runCurrent()
        val step = events.applied.single().first.steps.single() as EventStep.UpdateOne
        assertEquals(4L, step.id)
        assertTrue(step.detach)
        assertEquals(listOf(EventFormEffect.Saved(LocalDate(2026, 10, 20))), form.effects)
    }

    @Test
    fun `a new step and no repeat ask this and following alone`() = runTest {
        withLessons()
        val form = form(handleOf(eventId = 4))
        form.on(EventFormIntent.RepeatSelected(Repeat.NONE))
        form.on(EventFormIntent.SaveClicked)
        runCurrent()
        assertEquals(ScopeQuestion.FollowingOnly, (form.state.sheet as EventFormSheet.Scope).ask.question)
        // a swipe is «Отмена»: nothing is carried out, the form keeps its edits
        form.on(EventFormIntent.SheetHidden)
        runCurrent()
        assertTrue(events.applied.isEmpty())
        assertEquals(Repeat.NONE, form.state.draft.repeat)
    }

    /** A double tap of «Сохранить» (the lesson of stage 120): the second press finds the form on its way — one plan, one way back. */
    @Test
    fun `notes alone ask nothing and are written to the event alone - once for a double tap`() = runTest {
        withLessons()
        val form = form(handleOf(eventId = 4, focusNotes = true))
        assertTrue(form.state.focusNotes)
        form.on(EventFormIntent.NotesChanged("Этюд Кайзера № 3"))
        form.tap(EventFormIntent.SaveClicked)
        form.tap(EventFormIntent.SaveClicked)
        runCurrent()
        assertNull("no question", form.state.sheet)
        assertEquals("one plan, not two", 1, events.applied.size)
        assertEquals(EventPlan(EventStep.UpdateNotes(4, "Этюд Кайзера № 3")), events.applied.single().first)
        assertEquals(listOf(EventFormEffect.Saved(LocalDate(2026, 10, 19))), form.effects)
    }

    @Test
    fun `a single event given a repeat asks nothing and starts it from itself - once for a double tap`() = runTest {
        events.events.value = listOf(lessons().first().copy(id = 30, seriesId = null, date = LocalDate(2026, 10, 1)))
        val form = form(handleOf(eventId = 30))
        assertFalse(form.state.inSeries)
        form.on(EventFormIntent.RepeatSelected(Repeat.WEEKLY))
        assertEquals("a single event: the line of its new repeat with the link", kotlinx.datetime.DayOfWeek.THURSDAY, form.state.summary?.weekday)
        form.tap(EventFormIntent.SaveClicked)
        form.tap(EventFormIntent.SaveClicked)
        runCurrent()
        assertNull(form.state.sheet)
        assertEquals("one repeat started, not two", 1, events.applied.size)
        val step = events.applied.single().first.steps.single() as EventStep.StartSeries
        assertEquals(30L, step.eventId)
        assertEquals(listOf(EventFormEffect.Saved(LocalDate(2026, 10, 1))), form.effects)
    }

    @Test
    fun `an edit without changes just closes and the close of an edited form asks first`() = runTest {
        withLessons()
        val form = form(handleOf(eventId = 4))
        form.tap(EventFormIntent.SaveClicked)
        form.tap(EventFormIntent.SaveClicked)
        runCurrent()
        assertEquals("closed once for a double tap", listOf<EventFormEffect>(EventFormEffect.Close), form.effects)
        assertTrue(events.applied.isEmpty())

        val other = form(handleOf(eventId = 5))
        // a stray space is no edit: it closes at once
        other.on(EventFormIntent.PlaceChanged("Анна Сергеевна "))
        other.on(EventFormIntent.CloseClicked)
        runCurrent()
        assertEquals(listOf<EventFormEffect>(EventFormEffect.Close), other.effects)

        val edited = form(handleOf(eventId = 6))
        edited.on(EventFormIntent.TitleChanged("Открытый урок"))
        edited.on(EventFormIntent.CloseClicked)
        assertEquals(EventFormDialog.Discard, edited.state.dialog)
        assertEquals("«Изменения в „Урок“ пропадут»", EventName.OfKind(lesson, null), edited.state.savedName)
        edited.on(EventFormIntent.DialogDismissed)
        assertNull(edited.state.dialog)
        edited.on(EventFormIntent.CloseClicked)
        edited.on(EventFormIntent.DialogConfirmed)
        runCurrent()
        assertEquals(listOf<EventFormEffect>(EventFormEffect.Close), edited.effects)
    }

    @Test
    fun `a new form closes without asking while nothing is typed - once for a double tap`() = runTest {
        val form = form(handleOf(date = first))
        form.tap(EventFormIntent.CloseClicked)
        form.tap(EventFormIntent.CloseClicked)
        runCurrent()
        assertEquals(listOf<EventFormEffect>(EventFormEffect.Close), form.effects)
    }

    @Test
    fun `done of the sheet of a kind stores the colour at once and the form does not take it back`() = runTest {
        withLessons()
        val form = form(handleOf(date = first))
        form.on(EventFormIntent.KindRowClicked)
        val sheet = form.state.sheet as EventFormSheet.Kind
        assertEquals(BuiltInKind.LESSON, sheet.builtIn)
        form.on(EventFormIntent.KindColorPicked(3))
        // a built-in kind keeps its sign
        form.on(EventFormIntent.KindSignPicked(KindSign.BOOK))
        assertEquals(KindSign.LESSON, (form.state.sheet as EventFormSheet.Kind).draft.sign)
        form.tap(EventFormIntent.SheetDone)
        form.tap(EventFormIntent.SheetDone)
        runCurrent()
        assertNull(form.state.sheet)
        assertEquals(listOf(StoredKind.Recolor(BuiltInKind.LESSON, 3)), events.storedKinds.value)
        assertEquals(3, form.state.kind.look.color)

        form.on(EventFormIntent.TitleChanged("Открытый урок"))
        form.on(EventFormIntent.CloseClicked)
        form.on(EventFormIntent.DialogConfirmed)
        runCurrent()
        assertEquals("«Не сохранять» does not take the colour back", listOf(StoredKind.Recolor(BuiltInKind.LESSON, 3)), events.storedKinds.value)
    }

    @Test
    fun `a new kind of ones own needs a name that no kind has and is chosen in the form once done`() = runTest {
        val form = form(handleOf(date = first))
        form.on(EventFormIntent.OwnKindClicked)
        runCurrent()
        var sheet = form.state.sheet as EventFormSheet.Kind
        assertEquals("«Нужно имя» from the first frame", KindNameProblem.Empty, sheet.problem)
        assertEquals("the first free colour", 1, sheet.draft.color)
        assertEquals(KindSign.BOOK, sheet.draft.sign)
        form.on(EventFormIntent.SheetDone)
        runCurrent()
        assertTrue("asleep: nothing is stored", events.storedKinds.value.isEmpty())

        form.on(EventFormIntent.KindNameChanged("  урок "))
        runCurrent()
        sheet = form.state.sheet as EventFormSheet.Kind
        assertEquals("a name of a built-in kind, whatever its case", KindNameProblem.Taken("Урок"), sheet.problem)

        form.on(EventFormIntent.KindNameChanged("Сольфеджио"))
        runCurrent()
        assertNull((form.state.sheet as EventFormSheet.Kind).problem)
        form.on(EventFormIntent.SheetDone)
        runCurrent()
        val own = events.storedKinds.value.single() as StoredKind.Own
        assertEquals("Сольфеджио", own.name)
        assertEquals(KindRef.Custom(own.id), form.state.draft.kind)
        assertNull(form.state.sheet)
    }

    @Test
    fun `a swipe hides the sheet of a kind and its draft comes back with it`() = runTest {
        val form = form(handleOf(date = first))
        form.on(EventFormIntent.OwnKindClicked)
        form.on(EventFormIntent.KindNameChanged("Оркестр"))
        form.on(EventFormIntent.SheetHidden)
        runCurrent()
        assertNull(form.state.sheet)
        assertTrue("a swipe saves nothing", events.storedKinds.value.isEmpty())
        form.on(EventFormIntent.OwnKindClicked)
        assertEquals("Оркестр", (form.state.sheet as EventFormSheet.Kind).draft.name)
    }

    /** «Готово» of a kind of one's own edits it (KindSave.EditOwn): its own name is not «another kind's» (D34), and no kind is added. */
    @Test
    fun `a kind of ones own is edited in its sheet - its own name is no name of another kind`() = runTest {
        events.storedKinds.value = listOf(StoredKind.Own(40, "Сольфеджио", 3, KindSign.BOOK, 1))
        events.events.value = listOf(CalendarEvent(41, KindRef.Custom(40), LocalDate(2026, 10, 1), 16 * 60, 60, "", "ДМШ", "", null, false, 1))
        val form = form(handleOf(eventId = 41))
        form.on(EventFormIntent.KindRowClicked)
        runCurrent()
        val sheet = form.state.sheet as EventFormSheet.Kind
        assertEquals("Сольфеджио", sheet.draft.name)
        assertNull("its own name is free for it", sheet.problem)
        form.on(EventFormIntent.KindNameChanged("Сольфеджио и хор"))
        form.on(EventFormIntent.KindColorPicked(5))
        form.on(EventFormIntent.KindSignPicked(KindSign.ARC))
        runCurrent()
        assertNull((form.state.sheet as EventFormSheet.Kind).problem)
        form.on(EventFormIntent.SheetDone)
        runCurrent()
        assertNull(form.state.sheet)
        assertEquals("the same kind, edited — none added", listOf(StoredKind.Own(40, "Сольфеджио и хор", 5, KindSign.ARC, 1)), events.storedKinds.value)
        assertEquals(KindRef.Custom(40), form.state.draft.kind)
    }

    @Test
    fun `the kind chosen in the form deleted becomes other and is no edit of the event`() = runTest {
        events.storedKinds.value = listOf(StoredKind.Own(40, "Сольфеджио", 3, KindSign.BOOK, 1))
        events.events.value = listOf(
            CalendarEvent(41, KindRef.Custom(40), LocalDate(2026, 10, 1), 16 * 60, 60, "", "ДМШ", "", null, false, 1),
            CalendarEvent(42, KindRef.Custom(40), LocalDate(2026, 10, 8), 16 * 60, 60, "", "ДМШ", "", null, false, 2),
        )
        val form = form(handleOf(eventId = 41))
        form.on(EventFormIntent.KindRowClicked)
        val sheet = form.state.sheet as EventFormSheet.Kind
        assertTrue(sheet.deletable)
        assertEquals(2, sheet.events)
        form.on(EventFormIntent.KindDeleteClicked)
        assertEquals(EventFormDialog.DeleteKind(40, "Сольфеджио", 2), form.state.dialog)
        form.on(EventFormIntent.DialogConfirmed)
        runCurrent()
        assertNull(form.state.dialog)
        assertNull(form.state.sheet)
        assertEquals(KindRef.OTHER, form.state.draft.kind)
        assertTrue(events.storedKinds.value.isEmpty())
        // the event is «Другое» in the storage as well: closing asks nothing
        form.on(EventFormIntent.CloseClicked)
        runCurrent()
        assertEquals(listOf<EventFormEffect>(EventFormEffect.Close), form.effects)
    }

    @Test
    fun `the draft and its sheet outlive the process`() = runTest {
        withLessons()
        val saved = handleOf(date = first)
        val form = form(saved)
        form.on(EventFormIntent.TitleChanged("Открытый урок"))
        form.on(EventFormIntent.RepeatSelected(Repeat.BIWEEKLY))
        form.on(EventFormIntent.DurationOtherClicked)
        form.on(EventFormIntent.DurationStepped(4))

        val again = form(saved)
        assertEquals("Открытый урок", again.state.draft.title)
        assertEquals(Repeat.BIWEEKLY, again.state.draft.repeat)
        assertEquals("the sheet as it was left", 105, (again.state.sheet as EventFormSheet.Duration).minutes)
    }

    /** spec 3.36.9: «черновик листа живёт, пока открыта форма» — a form the system ended and brought back is the same form (D33). */
    @Test
    fun `the draft of a sheet of a kind a swipe hid outlives the process`() = runTest {
        val saved = handleOf(date = first)
        val form = form(saved)
        form.on(EventFormIntent.OwnKindClicked)
        form.on(EventFormIntent.KindNameChanged("Сольфеджио"))
        form.on(EventFormIntent.KindColorPicked(3))
        form.on(EventFormIntent.SheetHidden)
        runCurrent()

        val again = form(saved)
        assertNull("the sheet stays hidden", again.state.sheet)
        again.on(EventFormIntent.OwnKindClicked)
        runCurrent()
        val sheet = again.state.sheet as EventFormSheet.Kind
        assertEquals("Сольфеджио", sheet.draft.name)
        assertEquals(3, sheet.draft.color)
        assertNull(sheet.problem)
    }

    /** The sheet of a repeat is asked again from the draft that outlived the process; the sheet of a kind says its reason again. */
    @Test
    fun `the sheet of a repeat and the reason of the sheet of a kind outlive the process`() = runTest {
        withLessons()
        val edited = handleOf(eventId = 4)
        val form = form(edited)
        form.on(EventFormIntent.TimeRowClicked)
        form.on(EventFormIntent.TimeMinute(30))
        form.on(EventFormIntent.SheetDone)
        form.on(EventFormIntent.SaveClicked)
        runCurrent()
        assertTrue(form.state.sheet is EventFormSheet.Scope)
        val again = form(edited)
        runCurrent()
        assertEquals("asked again", ScopeQuestion.Both(EditScope.FOLLOWING), (again.state.sheet as EventFormSheet.Scope).ask.question)
        assertEquals(17 * 60 + 30, again.state.draft.startMinutes)

        val kindSaved = handleOf(date = first)
        val kindForm = form(kindSaved)
        kindForm.on(EventFormIntent.OwnKindClicked)
        kindForm.on(EventFormIntent.KindNameChanged("урок"))
        runCurrent()
        val back = form(kindSaved)
        runCurrent()
        val sheet = back.state.sheet as EventFormSheet.Kind
        assertEquals("урок", sheet.draft.name)
        assertEquals("reckoned again", KindNameProblem.Taken("Урок"), sheet.problem)
    }

    @Test
    fun `the sheets of the date and the time put their choice into the form only by done`() = runTest {
        withLessons()
        val form = form(handleOf(date = first))
        form.on(EventFormIntent.DateRowClicked)
        // forward no further than the current month and twelve
        repeat(14) { form.on(EventFormIntent.DateMonthStep(1)) }
        assertEquals(kotlinx.datetime.YearMonth(2027, 9), (form.state.sheet as EventFormSheet.Date).month)
        assertFalse((form.state.sheet as EventFormSheet.Date).canForward)
        form.on(EventFormIntent.DatePicked(LocalDate(2026, 10, 24)))
        form.on(EventFormIntent.SheetHidden)
        assertEquals("a swipe changes nothing", first, form.state.draft.date)

        form.on(EventFormIntent.TimeRowClicked)
        form.on(EventFormIntent.AllDayToggled)
        form.on(EventFormIntent.SheetDone)
        assertNull(form.state.draft.startMinutes)
        assertNull(form.state.draft.durationMinutes)
        form.on(EventFormIntent.TimeRowClicked)
        val time = form.state.sheet as EventFormSheet.Time
        assertTrue(time.allDay)
        assertEquals("the wheels stand on the time it had", 17 * 60, time.minutes)
        assertEquals("«Частое» — the start of the lessons", listOf(17 * 60), time.frequent)
        form.on(EventFormIntent.AllDayToggled)
        form.on(EventFormIntent.SheetDone)
        assertEquals(17 * 60, form.state.draft.startMinutes)
        assertEquals("the length comes back with the time", 45, form.state.draft.durationMinutes)
    }

    @Test
    fun `the end of a repeat is chosen in its sheet and none of its days before the event`() = runTest {
        val form = form(handleOf(date = first))
        form.on(EventFormIntent.UntilClicked)
        assertNull("no repeat — no sheet of its end", form.state.sheet)
        form.on(EventFormIntent.RepeatSelected(Repeat.WEEKLY))
        form.on(EventFormIntent.UntilClicked)
        var sheet = form.state.sheet as EventFormSheet.Until
        assertEquals(listOf(LocalDate(2026, 12, 31), LocalDate(2027, 5, 31)), sheet.chips)
        form.on(EventFormIntent.UntilPicked(LocalDate(2026, 9, 21)))
        assertNull("a day before the first event sleeps", (form.state.sheet as EventFormSheet.Until).picked)
        form.on(EventFormIntent.UntilPicked(LocalDate(2026, 12, 31)))
        sheet = form.state.sheet as EventFormSheet.Until
        assertEquals(14, sheet.summary?.count)
        form.on(EventFormIntent.SheetDone)
        assertEquals(LocalDate(2026, 12, 31), form.state.draft.until)
        assertEquals(14, form.state.summary?.count)
    }

    @Test
    fun `this and following of a moved lesson freeze the names of the records of the lessons that go`() = runTest {
        withLessons()
        events.recordEvents.value = mapOf(5L to SessionEvent(5, "", LocalDate(2026, 10, 26), lesson, null))
        val form = form(handleOf(eventId = 4))
        form.on(EventFormIntent.DateRowClicked)
        form.on(EventFormIntent.DatePicked(LocalDate(2026, 10, 20)))
        form.on(EventFormIntent.SheetDone)
        form.on(EventFormIntent.SaveClicked)
        runCurrent()
        form.on(EventFormIntent.ScopeAnswered(EditScope.FOLLOWING))
        runCurrent()
        val (plan, frozen) = events.applied.single()
        assertTrue(plan.steps.single() is EventStep.Split)
        assertEquals("the lesson of 26 October goes and its record keeps its name", mapOf(5L to "Урок · 2026-10-26"), frozen)
    }

    @Test
    fun `an edit of an event gone meanwhile closes`() = runTest {
        val form = form(handleOf(eventId = 99))
        assertEquals(listOf<EventFormEffect>(EventFormEffect.Close), form.effects)
    }
}
