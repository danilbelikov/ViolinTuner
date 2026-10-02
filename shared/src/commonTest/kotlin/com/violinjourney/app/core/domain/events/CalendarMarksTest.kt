package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.LESSON
import com.violinjourney.app.core.domain.events.TestEvents.OTHER
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.REHEARSAL
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class CalendarMarksTest {
    private val config = EventsConfig()
    private val kinds = KindRules.ordered(
        KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1), StoredKind.Own(8, "Мастер-класс", 4, KindSign.BOLT, 2)), config),
    )
    private val saturday = LocalDate(2026, 11, 14)

    @Test
    fun `a day goes from the whole day events to the latest start`() {
        val events = listOf(
            event(4, saturday, at(17), 45),
            event(3, saturday, at(14), 90, kind = KindRef.Custom(8)),
            event(1, saturday, at(11), 120, kind = KindRef.Custom(9)),
            event(2, saturday, null, null, kind = OTHER, title = "Замена струн"),
            event(5, saturday.plusDays(1), at(9), 60),
        )
        assertEquals(listOf(2L, 1L, 3L, 4L), CalendarMarks.dayEvents(events, saturday).map { it.id })
        // created earlier first among equal starts: the order never shifts
        val equal = listOf(event(7, saturday, at(17), createdAt = 20), event(6, saturday, at(17), createdAt = 30))
        assertEquals(listOf(7L, 6L), CalendarMarks.dayEvents(equal, saturday).map { it.id })
    }

    @Test
    fun `a cell shows three marks in the order of the day and a plus for the rest`() {
        val events = listOf(
            event(4, saturday, at(17), 45),
            event(3, saturday, at(14), 90, kind = KindRef.Custom(8)),
            event(1, saturday, at(11), 120, kind = KindRef.Custom(9)),
            event(2, saturday, null, null, kind = OTHER),
        )
        val marks = CalendarMarks.marksOf(CalendarMarks.dayEvents(events, saturday), kinds, config)
        assertEquals(listOf(KindLook(KindSign.OTHER, 7), KindLook(KindSign.ARC, 1), KindLook(KindSign.BOLT, 4)), marks.looks)
        assertEquals(true, marks.more)
        val three = CalendarMarks.marksOf(CalendarMarks.dayEvents(events.drop(1), saturday), kinds, config)
        assertEquals(3, three.looks.size)
        assertEquals(false, three.more, "three — no plus")
        assertEquals(DayMarks.NONE, CalendarMarks.marksOf(emptyList(), kinds, config))
    }

    @Test
    fun `a month counts its events and names its kinds once in the order of the form`() {
        val events = listOf(
            event(1, LocalDate(2026, 11, 2), at(17), 45),
            event(2, LocalDate(2026, 11, 9), at(17), 45),
            event(3, saturday, at(11), 120, kind = KindRef.Custom(9)),
            event(4, saturday, at(14), 90, kind = KindRef.Custom(8)),
            event(5, LocalDate(2026, 11, 21), at(18, 30), 90, kind = PERFORMANCE),
            event(6, LocalDate(2026, 11, 25), kind = KindRef.Custom(77)),
            // on the edge of the months: it starts on 31.10 and belongs to October
            event(7, LocalDate(2026, 10, 31), at(23), 120, kind = REHEARSAL),
        )
        val november = YearMonth(2026, 11)
        assertEquals(6, CalendarMarks.monthCount(events, november))
        assertEquals(1, CalendarMarks.monthCount(events, YearMonth(2026, 10)))
        assertEquals(
            listOf(LESSON, PERFORMANCE, OTHER, KindRef.Custom(8), KindRef.Custom(9)),
            CalendarMarks.kindsOfMonth(events, november, kinds).map { it.ref },
            "a kind of one's own that is gone is «Другое»; «Мастер-класс» before «Оркестр»",
        )
        assertEquals(listOf(REHEARSAL), CalendarMarks.kindsOfMonth(events, YearMonth(2026, 10), kinds).map { it.ref })
        assertEquals(emptyList(), CalendarMarks.kindsOfMonth(events, YearMonth(2026, 12), kinds))
    }
}
