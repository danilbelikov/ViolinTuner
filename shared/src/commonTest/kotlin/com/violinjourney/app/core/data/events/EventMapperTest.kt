package com.violinjourney.app.core.data.events

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.events.StoredKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class EventMapperTest {
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)

    @Test
    fun `an event goes to a row and comes back the same`() {
        val draft = EventDraft(KindRef.Custom(4), LocalDate(2026, 11, 14), startMinutes = 660, durationMinutes = 120, title = "", place = "Малый зал", notes = "Сбор в 10:30")
        val row = EventMapper.toEntity(draft, seriesId = 3, createdAtEpochMs = 77)
        assertEquals("OTHER", row.kind, "a kind of one's own is kept as «Другое» beside its id")
        assertEquals(4L, row.kindId)
        assertEquals("2026-11-14", row.date)
        assertEquals(
            CalendarEvent(9, KindRef.Custom(4), LocalDate(2026, 11, 14), 660, 120, "", "Малый зал", "Сбор в 10:30", 3, false, 77),
            EventMapper.toEvent(row.copy(id = 9)),
        )
        assertEquals("LESSON" to null, EventMapper.columnsOf(lesson))
    }

    @Test
    fun `a kind a newer build wrote reads as other`() {
        assertEquals(KindRef.BuiltIn(BuiltInKind.OTHER), EventMapper.kindOf("CONCERT", kindId = null))
        assertEquals(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), EventMapper.kindOf("PERFORMANCE", kindId = null))
        assertEquals(KindRef.Custom(5), EventMapper.kindOf("OTHER", kindId = 5))
    }

    @Test
    fun `a repeat comes back with its step and its end`() {
        val row = EventSeriesEntity(
            id = 2, kind = "LESSON", kindId = null, stepDays = 14, firstDate = "2026-09-28", untilDate = "2026-12-31",
            laidUntil = "2026-12-20", startMinutes = 1020, durationMinutes = 45, title = "", place = "Анна Сергеевна",
        )
        val series = EventMapper.toSeries(row)
        assertEquals(
            EventSeries(2, lesson, Repeat.BIWEEKLY, LocalDate(2026, 9, 28), LocalDate(2026, 12, 31), LocalDate(2026, 12, 20), 1020, 45, "", "Анна Сергеевна"),
            series,
        )
        assertEquals(row, EventMapper.toEntity(series))
        assertEquals(Repeat.WEEKLY, EventMapper.toSeries(row.copy(stepDays = 30)).repeat, "a step this build does not know is a week")
        assertNull(EventMapper.toSeries(row.copy(untilDate = null)).until)
        // what the repeat lays: its template, no notes, its moment
        val laid = EventMapper.laid(row, LocalDate(2026, 10, 12), createdAtEpochMs = 100)
        assertEquals(
            CalendarEventEntity(0, "LESSON", null, "2026-10-12", 1020, 45, "", "Анна Сергеевна", "", 2, false, 100),
            laid,
        )
    }

    @Test
    fun `rows of kinds are colours of the built-in ones and kinds of ones own`() {
        assertEquals(
            StoredKind.Recolor(BuiltInKind.LESSON, 3),
            EventMapper.toStoredKind(EventKindEntity(1, builtIn = "LESSON", name = "", color = 3, sign = null, createdAtEpochMs = 5)),
        )
        assertNull(EventMapper.toStoredKind(EventKindEntity(2, builtIn = "CONCERT", name = "", color = 3, sign = null, createdAtEpochMs = 5)))
        assertEquals(
            StoredKind.Own(3, "Оркестр", 1, KindSign.ARC, 6),
            EventMapper.toStoredKind(EventKindEntity(3, builtIn = null, name = "Оркестр", color = 1, sign = "arc", createdAtEpochMs = 6)),
        )
        assertEquals(
            KindSign.BOOK,
            (EventMapper.toStoredKind(EventKindEntity(4, builtIn = null, name = "Сольфеджио", color = 1, sign = "harp", createdAtEpochMs = 7)) as StoredKind.Own).sign,
            "a sign this build does not know is the book",
        )
    }

    @Test
    fun `the event of a recording knows its kind and what its name is made of`() {
        val untitled = EventMapper.recordEventOf(RecordEventRow(5, title = "", date = "2026-09-21", kind = "LESSON", kindId = null, ownName = null))
        assertEquals(SessionEvent(5, "", LocalDate(2026, 9, 21), lesson, null), untitled)
        assertEquals(EventName.OfKind(lesson, null), untitled.name, "«Урок · 21 сентября»: the name of the kind")
        val own = EventMapper.recordEventOf(RecordEventRow(6, title = "", date = "2026-11-14", kind = "OTHER", kindId = 9, ownName = "Оркестр"))
        assertEquals(EventName.OfKind(KindRef.Custom(9), "Оркестр"), own.name)
        // the kind was deleted meanwhile: the join finds no name — «Другое»
        val gone = EventMapper.recordEventOf(RecordEventRow(7, title = "", date = "2026-11-14", kind = "OTHER", kindId = 9, ownName = null))
        assertEquals(KindRef.BuiltIn(BuiltInKind.OTHER), gone.kind)
        assertEquals(EventName.OfKind(KindRef.BuiltIn(BuiltInKind.OTHER), null), gone.name)
        val titled = EventMapper.recordEventOf(RecordEventRow(8, title = "Осенний концерт", date = "2026-10-24", kind = "PERFORMANCE", kindId = null, ownName = null))
        assertEquals(EventName.Titled("Осенний концерт"), titled.name)
    }
}
