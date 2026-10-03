package com.violinjourney.app.feature.events.form

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.feature.events.screen.SeriesWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

/**
 * The rules of the form of an event (spec 3.35, 3.36.9, 5.28; plan 7.3) on the days of the mockups: Sunday 27 September 2026; the lessons
 * with Анна Сергеевна on Mondays at 17:00 for 45 minutes.
 */
class EventFormReducerTest {
    private val config = EventsConfig()
    private val today = LocalDate(2026, 9, 27)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
    private val kinds = KindRules.all(emptyList(), config)

    private fun event(id: Long, date: LocalDate, start: Int?, duration: Int?, kind: KindRef = lesson, seriesId: Long? = null, createdAt: Long = id) =
        CalendarEvent(id, kind, date, start, duration, title = "", place = "", notes = "", seriesId = seriesId, detached = false, createdAtEpochMs = createdAt)

    private fun draft(
        date: LocalDate = LocalDate(2026, 9, 28),
        start: Int? = 17 * 60,
        duration: Int? = 45,
        repeat: Repeat = Repeat.NONE,
        until: LocalDate? = null,
        kind: KindRef = lesson,
    ) = FormDraft(kind, date, start, duration, repeat, until, place = "", title = "", notes = "")

    @Test
    fun `the very first event is a lesson for the whole day and the next lesson takes the time of the last one`() {
        val first = EventFormReducer.newDraft(LocalDate(2026, 9, 28), kind = null, events = emptyList(), series = emptyList(), kinds = kinds)
        assertEquals(lesson, first.kind)
        assertNull(first.startMinutes, "spec 5.28: «весь день» while no lesson has a time")
        assertNull(first.durationMinutes)
        assertEquals(Repeat.NONE, first.repeat)
        assertEquals(LocalDate(2026, 9, 28), first.date)

        val second = EventFormReducer.newDraft(LocalDate(2026, 10, 5), kind = null, events = listOf(event(1, LocalDate(2026, 9, 28), 17 * 60, 45)), series = emptyList(), kinds = kinds)
        assertEquals(lesson, second.kind)
        assertEquals(17 * 60, second.startMinutes)
        assertEquals(45, second.durationMinutes)
    }

    @Test
    fun `the way in that names a kind wins over the last one created and takes the time of its own`() {
        val events = listOf(
            event(1, LocalDate(2026, 9, 28), 17 * 60, 45, createdAt = 10),
            event(2, LocalDate(2026, 10, 24), 18 * 60 + 30, 90, kind = performance, createdAt = 5),
        )
        val draft = EventFormReducer.newDraft(today, kind = performance, events = events, series = emptyList(), kinds = kinds)
        assertEquals(performance, draft.kind)
        assertEquals(18 * 60 + 30, draft.startMinutes)
        assertEquals(90, draft.durationMinutes)
    }

    @Test
    fun `a kind of ones own the last event was of that is gone is other`() {
        val own = KindRef.Custom(9)
        val draft = EventFormReducer.newDraft(today, kind = null, events = listOf(event(1, today, 16 * 60, 60, kind = own)), series = emptyList(), kinds = kinds)
        assertEquals(KindRef.OTHER, draft.kind)
    }

    @Test
    fun `an edit starts from the event and its repeat`() {
        val repeat = EventSeries(4, lesson, Repeat.WEEKLY, LocalDate(2026, 9, 28), LocalDate(2026, 12, 31), LocalDate(2026, 12, 20), 17 * 60, 45, "", "")
        val stored = event(7, LocalDate(2026, 10, 19), 17 * 60, 45, seriesId = 4).copy(place = "Анна Сергеевна", notes = "этюд")
        val draft = EventFormReducer.draftOf(stored, repeat)
        assertEquals(Repeat.WEEKLY, draft.repeat)
        assertEquals(LocalDate(2026, 12, 31), draft.until)
        assertEquals("Анна Сергеевна", draft.place)
        assertEquals("этюд", draft.notes)
        assertEquals(Repeat.NONE, EventFormReducer.draftOf(stored.copy(seriesId = null), null).repeat)
    }

    @Test
    fun `a stray space is no edit and a changed field is`() {
        val initial = draft().copy(title = "Урок", place = "Анна Сергеевна")
        assertFalse(EventFormReducer.isDirty(initial, initial.copy(title = " Урок ", place = "Анна Сергеевна  "), config))
        assertTrue(EventFormReducer.isDirty(initial, initial.copy(startMinutes = 17 * 60 + 30), config))
        assertTrue(EventFormReducer.isDirty(initial, initial.copy(repeat = Repeat.WEEKLY), config))
        assertTrue(EventFormReducer.isDirty(initial, initial.copy(notes = "что задали"), config))
        // the end of a repeat counts while there is one
        assertFalse(EventFormReducer.isDirty(initial, initial.copy(until = LocalDate(2026, 12, 31)), config))
        val weekly = initial.copy(repeat = Repeat.WEEKLY)
        assertTrue(EventFormReducer.isDirty(weekly, weekly.copy(until = LocalDate(2026, 12, 31)), config))
        // what «Весь день» keeps aside is no edit of its own
        assertFalse(EventFormReducer.isDirty(initial, initial.copy(keptStart = 9 * 60), config))
    }

    @Test
    fun `the end is the start and the length - after midnight with its day`() {
        assertEquals(FormEnd(17 * 60 + 45, null), EventFormReducer.endOf(draft()))
        assertEquals(FormEnd(60, LocalDate(2026, 10, 25)), EventFormReducer.endOf(draft(date = LocalDate(2026, 10, 24), start = 23 * 60 + 30, duration = 90)))
        assertNull(EventFormReducer.endOf(draft(duration = null)), "no length — no end to say")
        assertNull(EventFormReducer.endOf(draft(start = null, duration = null)), "«весь день»")
    }

    @Test
    fun `the line of a repeat says its weekday its end and how many lessons it has`() {
        val weekly = draft(repeat = Repeat.WEEKLY, until = LocalDate(2026, 12, 31))
        assertEquals(RepeatSummary(DayOfWeek.MONDAY, LocalDate(2026, 12, 31), 14, SeriesWord.LESSON), EventFormReducer.repeatSummary(weekly, null, emptyList(), SeriesWord.LESSON))
        assertEquals(
            RepeatSummary(DayOfWeek.MONDAY, null, null, SeriesWord.LESSON),
            EventFormReducer.repeatSummary(weekly.copy(until = null), null, emptyList(), SeriesWord.LESSON),
            "«по понедельникам · без конца · до…»",
        )
        assertEquals(7, EventFormReducer.repeatSummary(weekly.copy(repeat = Repeat.BIWEEKLY), null, emptyList(), SeriesWord.LESSON)?.count, "28.09 … 21.12 every other week")
        assertNull(EventFormReducer.repeatSummary(draft(), null, emptyList(), SeriesWord.LESSON))
    }

    @Test
    fun `an event of a repeat says the repeat as it is stored`() {
        val repeat = EventSeries(4, lesson, Repeat.WEEKLY, LocalDate(2026, 9, 28), LocalDate(2026, 12, 31), LocalDate(2026, 12, 20), 17 * 60, 45, "", "")
        val laid = (0 until 12).map { event(10L + it, LocalDate(2026, 9, 28).plus(7 * it, DateTimeUnit.DAY), 17 * 60, 45, seriesId = 4) }
        // one deleted «only this»: the count is what the repeat has
        val stored = laid.filterNot { it.date == LocalDate(2026, 10, 12) }
        val edited = draft(date = LocalDate(2026, 10, 19), repeat = Repeat.WEEKLY, until = LocalDate(2026, 12, 31))
        assertEquals(RepeatSummary(DayOfWeek.MONDAY, LocalDate(2026, 12, 31), 13, SeriesWord.LESSON), EventFormReducer.repeatSummary(edited, repeat, stored, SeriesWord.LESSON))
        // a lesson of it moved to a Tuesday «только этот» — or its date changed in the form: the repeat stays on Mondays (spec 3.36.9: «как
        // есть у этого повтора»), as the screen of the event says it
        assertEquals(DayOfWeek.MONDAY, EventFormReducer.repeatSummary(edited.copy(date = LocalDate(2026, 10, 20)), repeat, stored, SeriesWord.LESSON)?.weekday)
        // a new step: a new repeat from the date of the form with the same end
        val biweekly = EventFormReducer.repeatSummary(edited.copy(repeat = Repeat.BIWEEKLY), repeat, stored, SeriesWord.LESSON)
        assertEquals(LocalDate(2026, 12, 31), biweekly?.until)
        assertEquals(6, biweekly?.count, "19.10, 2.11 … 28.12")
        val fromTuesday = EventFormReducer.repeatSummary(edited.copy(date = LocalDate(2026, 10, 20), repeat = Repeat.BIWEEKLY), repeat, stored, SeriesWord.LESSON)
        assertEquals(DayOfWeek.TUESDAY, fromTuesday?.weekday, "a new repeat — of the date of the form")
    }

    @Test
    fun `another kind of a new event brings the time of that kind while the time is not the persons own`() {
        val events = listOf(
            event(1, LocalDate(2026, 9, 28), 17 * 60, 45, createdAt = 10),
            event(2, LocalDate(2026, 10, 24), 18 * 60 + 30, 90, kind = performance, createdAt = 20),
        )
        val concert = EventFormReducer.newDraft(LocalDate(2026, 10, 5), kind = null, events = events, series = emptyList(), kinds = kinds)
        assertEquals(performance, concert.kind, "the last created is the concert")
        val lessonNow = EventFormReducer.withKind(concert, lesson, events, emptyList(), isNew = true)
        assertEquals(lesson, lessonNow.kind)
        assertEquals(17 * 60, lessonNow.startMinutes, "spec 3.36.9: «время — начало прошлого урока»")
        assertEquals(45, lessonNow.durationMinutes)
        val own = EventFormReducer.withKind(concert.copy(keptStart = 9 * 60, keptDuration = 30), KindRef.Custom(9), events, emptyList(), isNew = true)
        assertNull(own.startMinutes, "a kind without an event — «весь день»")
        assertNull(own.durationMinutes)
        assertNull(own.keptStart, "nothing kept aside of another kind")
        assertNull(own.keptDuration)
    }

    @Test
    fun `a time the person chose and an edit keep their time whatever the kind`() {
        val events = listOf(event(1, LocalDate(2026, 9, 28), 17 * 60, 45, createdAt = 10))
        val chosen = EventFormReducer.withTime(draft(kind = performance, start = null, duration = null), allDay = false, minutes = 19 * 60, config = config)
        assertTrue(chosen.timeTouched)
        assertEquals(19 * 60, EventFormReducer.withKind(chosen, lesson, events, emptyList(), isNew = true).startMinutes)
        val length = EventFormReducer.withDuration(draft(kind = performance, start = 19 * 60, duration = 120), 60)
        assertTrue(length.timeTouched, "a chip of the length is the person's own too")
        assertEquals(60, EventFormReducer.withKind(length, lesson, events, emptyList(), isNew = true).durationMinutes)
        val allDay = EventFormReducer.withTime(draft(kind = performance, start = null, duration = null), allDay = true, minutes = 0, config = config)
        assertTrue(allDay.timeTouched, "«Весь день» confirmed by «Готово» is a choice")
        assertNull(EventFormReducer.withKind(allDay, lesson, events, emptyList(), isNew = true).startMinutes)
        val edit = draft(kind = performance, start = 19 * 60, duration = 120)
        val kindOfAnEdit = EventFormReducer.withKind(edit, lesson, events, emptyList(), isNew = false)
        assertEquals(lesson, kindOfAnEdit.kind)
        assertEquals(19 * 60, kindOfAnEdit.startMinutes, "a performance made a lesson does not move")
        assertEquals(120, kindOfAnEdit.durationMinutes)
        assertEquals(draft(start = null, duration = null), EventFormReducer.withDuration(draft(start = null, duration = null), 60), "«весь день» has no length")
    }

    @Test
    fun `the end under the wheels is of the length the event has`() {
        fun timeSheet(draft: FormDraft) = EventFormReducer.sheetOf(
            FormSheet.Time(draft.startMinutes ?: draft.keptStart ?: 0, allDay = draft.startMinutes == null), draft, KindRules.kindOf(lesson, kinds, config),
            kinds, emptyList(), kinds, today, draft.date, config,
        ) as EventFormSheet.Time
        // 17:00 · 45 → «Весь день» → back to the time → «Без длительности»
        val allDay = EventFormReducer.withTime(draft(), allDay = true, minutes = 17 * 60, config = config)
        assertEquals(45, timeSheet(allDay).duration, "«весь день» shows the length it keeps aside: a time gives it back")
        val back = EventFormReducer.withTime(allDay, allDay = false, minutes = 17 * 60, config = config)
        assertNull(back.keptStart, "a time spends what was kept aside")
        assertNull(back.keptDuration)
        val noLength = EventFormReducer.withDuration(back, null)
        assertNull(timeSheet(noLength).end, "spec 3.36.9: «без длительности — строки нет»")
        assertNull(timeSheet(noLength).duration)
        // whatever is aside, an event with a time says its own length
        val aside = draft(duration = null).copy(keptStart = 9 * 60, keptDuration = 45)
        assertNull(timeSheet(aside).end)
        assertEquals(FormEnd(17 * 60 + 45, null), timeSheet(draft()).end)
    }

    @Test
    fun `the whole day keeps the time aside and gives it back`() {
        val timed = draft()
        val allDay = EventFormReducer.withTime(timed, allDay = true, minutes = 17 * 60, config = config)
        assertNull(allDay.startMinutes)
        assertNull(allDay.durationMinutes, "spec 5.28: «весь день» has no length")
        assertEquals(17 * 60, allDay.keptStart)
        assertEquals(45, allDay.keptDuration)
        assertEquals(17 * 60, EventFormReducer.wheelStart(allDay, LocalTime(18, 42), config), "the wheels come back to the time it had")
        val back = EventFormReducer.withTime(allDay, allDay = false, minutes = 17 * 60 + 30, config = config)
        assertEquals(17 * 60 + 30, back.startMinutes)
        assertEquals(45, back.durationMinutes, "the length «Весь день» took comes back with a time")
        // a time of its own changes nothing else
        assertEquals(45, EventFormReducer.withTime(timed, allDay = false, minutes = 18 * 60, config = config).durationMinutes)
    }

    @Test
    fun `the wheels open on the start of the event or on the next whole hour`() {
        assertEquals(17 * 60, EventFormReducer.wheelStart(draft(), LocalTime(9, 10), config))
        assertEquals(19 * 60, EventFormReducer.wheelStart(draft(start = null, duration = null), LocalTime(18, 42), config))
        assertEquals(0, EventFormReducer.wheelStart(draft(start = null, duration = null), LocalTime(23, 50), config), "after 23 — midnight")
    }

    @Test
    fun `the wheels pick a time on the step of five minutes`() {
        assertEquals(18 * 60 + 30, EventFormReducer.pickTime(18, 30, config))
        assertEquals(18 * 60 + 35, EventFormReducer.pickTime(18, 33, config))
        assertEquals(18, EventFormReducer.hourOf(18 * 60 + 35))
        assertEquals(35, EventFormReducer.minuteOf(18 * 60 + 35))
    }

    @Test
    fun `the date goes forward to the current month and twelve or to the month of the date the form opened with`() {
        assertEquals(YearMonth(2027, 9), EventFormReducer.dateLimit(today, LocalDate(2026, 9, 28), config))
        assertEquals(YearMonth(2028, 1), EventFormReducer.dateLimit(today, LocalDate(2028, 1, 10), config))
    }

    @Test
    fun `a new date drops an end of the repeat before it`() {
        val weekly = draft(repeat = Repeat.WEEKLY, until = LocalDate(2026, 10, 31))
        assertEquals(LocalDate(2026, 10, 31), EventFormReducer.withDate(weekly, LocalDate(2026, 10, 5)).until)
        assertNull(EventFormReducer.withDate(weekly, LocalDate(2026, 11, 2)).until)
        assertEquals(LocalDate(2026, 10, 31), EventFormReducer.withDate(weekly, LocalDate(2026, 10, 31)).until, "«до» is inclusive")
    }

    @Test
    fun `the sheet of the length opens on the length or on an hour`() {
        assertEquals(45, EventFormReducer.durationSheetStart(draft(), config))
        assertEquals(60, EventFormReducer.durationSheetStart(draft(duration = null), config))
    }

    @Test
    fun `the third chip of the date is the chosen one a week on`() {
        assertEquals(LocalDate(2026, 10, 5), EventFormReducer.weekLater(LocalDate(2026, 9, 28)))
    }

    @Test
    fun `the days of the sheet of the date mark every event and the chosen day without the time`() {
        val events = listOf(event(1, LocalDate(2026, 10, 24), 11 * 60, 120), event(2, LocalDate(2026, 10, 24), null, null, kind = performance))
        val cells = EventFormReducer.dateCells(YearMonth(2026, 10), picked = LocalDate(2026, 10, 24), today = today, events = events, kinds = kinds, config = config)
        val day = cells.filterNotNull().first { it.date == LocalDate(2026, 10, 24) }
        assertTrue(day.isSelected)
        assertEquals(listOf(KindLook(KindSign.PERFORMANCE, 2), KindLook(KindSign.LESSON, 0)), day.marks, "«весь день» first")
        assertEquals(0, day.fillLevel)
        assertTrue(cells.filterNotNull().none { it.isToday }, "today is not in October")
    }

    @Test
    fun `the days of the end of a repeat mark only its dates up to the end and the days before it sleep`() {
        val look = KindLook(KindSign.LESSON, 0)
        val cells = EventFormReducer.untilCells(YearMonth(2026, 9), first = LocalDate(2026, 9, 14), repeat = Repeat.WEEKLY, until = LocalDate(2026, 9, 21), today = today, look = look)
        val days = cells.filterNotNull().associateBy { it.date }
        assertFalse(days.getValue(LocalDate(2026, 9, 13)).enabled)
        assertTrue(days.getValue(LocalDate(2026, 9, 14)).enabled)
        assertEquals(listOf(look), days.getValue(LocalDate(2026, 9, 14)).marks)
        assertEquals(listOf(look), days.getValue(LocalDate(2026, 9, 21)).marks)
        assertTrue(days.getValue(LocalDate(2026, 9, 21)).isSelected, "the end, inclusive")
        assertTrue(days.getValue(LocalDate(2026, 9, 28)).marks.isEmpty(), "after the end")
        // no end: every date of the month
        val open = EventFormReducer.untilCells(YearMonth(2026, 9), LocalDate(2026, 9, 14), Repeat.WEEKLY, until = null, today = today, look = look)
        assertEquals(3, open.filterNotNull().count { it.marks.isNotEmpty() })
    }

    @Test
    fun `a new kind of ones own takes the first colour and sign no kind has`() {
        val own = KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1)), config)
        assertEquals(KindDraft(ref = null, name = "", color = 3, sign = KindSign.BOOK), EventFormReducer.newKindDraft(own, config))
        val orchestra = own.first { it.ref == KindRef.Custom(9) }
        assertEquals(KindDraft(KindRef.Custom(9), "Оркестр", 1, KindSign.ARC), EventFormReducer.kindDraftOf(orchestra))
        assertEquals(KindDraft(lesson, "", 0, KindSign.LESSON), EventFormReducer.kindDraftOf(own.first()))
    }

    @Test
    fun `the events of a kind of ones own are counted`() {
        val own = KindRef.Custom(9)
        assertEquals(2, EventFormReducer.eventsOf(9, listOf(event(1, today, null, null, kind = own), event(2, today, null, null, kind = own), event(3, today, null, null))))
    }
}
