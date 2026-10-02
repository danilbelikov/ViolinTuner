package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.BERLIN
import com.violinjourney.app.core.domain.events.TestEvents.LESSON
import com.violinjourney.app.core.domain.events.TestEvents.MOSCOW
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.REHEARSAL
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import com.violinjourney.app.core.domain.events.TestEvents.series
import com.violinjourney.app.core.domain.events.TestEvents.weekly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth

class EventRulesTest {
    private val config = EventsConfig()
    private val day = LocalDate(2026, 9, 28)

    @Test
    fun `texts are cut as a person counts and spaces at the edges go`() {
        val clean = EventRules.clean(
            EventDraft(date = day, title = "  " + "а".repeat(81) + " ", place = " Анна Сергеевна ", notes = "\n Гаммы\nЭтюд №3  \n"),
            config,
        )
        assertEquals("а".repeat(80), clean.title)
        assertEquals("Анна Сергеевна", clean.place)
        assertEquals("Гаммы\nЭтюд №3", clean.notes, "notes keep their inner line breaks")
        // an emoji on the edge is left out whole, never halved into a lone surrogate
        val atEdge = EventRules.clean(EventDraft(date = day, title = "а".repeat(79) + "🎻🎻"), config).title
        assertEquals("а".repeat(79) + "🎻", atEdge)
        assertEquals(EventRules.cleanKindName(" " + "б".repeat(30), config), "б".repeat(24))
    }

    @Test
    fun `a start falls on its step and a length on its steps and limits`() {
        assertEquals(at(17), EventRules.clean(EventDraft(date = day, startMinutes = at(17, 2)), config).startMinutes)
        assertEquals(at(17, 5), EventRules.clean(EventDraft(date = day, startMinutes = at(17, 3)), config).startMinutes)
        assertEquals(at(23, 55), EventRules.snapStart(at(23, 58), config), "the day has no 24:00")
        assertEquals(0, EventRules.snapStart(-7, config))
        assertEquals(45, EventRules.snapDuration(52, config))
        assertEquals(15, EventRules.snapDuration(1, config))
        assertEquals(480, EventRules.snapDuration(600, config))
        // «весь день» has no length (spec 3.36.9)
        assertNull(EventRules.clean(EventDraft(date = day, startMinutes = null, durationMinutes = 45), config).durationMinutes)
        assertEquals(45, EventRules.clean(EventDraft(date = day, startMinutes = at(17), durationMinutes = 45), config).durationMinutes)
    }

    @Test
    fun `the sheet of the length steps by fifteen minutes from an hour within its limits`() {
        assertEquals(75, EventRules.stepDuration(null, 1, config))
        assertEquals(60, EventRules.stepDuration(null, 0, config))
        assertEquals(30, EventRules.stepDuration(45, -1, config))
        assertEquals(15, EventRules.stepDuration(15, -1, config), "not below a quarter of an hour")
        assertEquals(480, EventRules.stepDuration(465, 3, config), "not above eight hours")
    }

    @Test
    fun `an event ends after its length or an hour or with its day`() {
        assertEquals(LocalDateTime(2026, 9, 28, 17, 45), EventRules.endOf(event(1, day, at(17), 45), config))
        assertEquals(LocalDateTime(2026, 9, 28, 18, 0), EventRules.endOf(event(1, day, at(17), null), config), "no length — an hour")
        assertEquals(LocalDateTime(2026, 9, 29, 0, 0), EventRules.endOf(event(1, day, null, null), config), "«весь день» — the midnight after")
        assertEquals(LocalDateTime(2026, 9, 29, 1, 0), EventRules.endOf(event(1, day, at(23, 30), 90), config), "late — on the next day")
        assertEquals(LocalDateTime(2026, 9, 28, 17, 0), EventRules.startOf(event(1, day, at(17), 45)))
    }

    @Test
    fun `an event is over when its end has come`() {
        val lesson = event(1, day, at(17), 45)
        assertFalse(EventRules.isPast(lesson, moment(day, 17, 44), MOSCOW, config))
        assertTrue(EventRules.isPast(lesson, moment(day, 17, 45), MOSCOW, config))
        val allDay = event(2, day)
        assertFalse(EventRules.isPast(allDay, moment(day, 23, 59), MOSCOW, config))
        assertTrue(EventRules.isPast(allDay, moment(day.plusDays(1), 0, 0), MOSCOW, config), "«весь день» is over from the midnight after it")
        // a lesson at 17:00 is at 17:00 wherever the phone is: the same moment is 17:30 in Moscow and 16:30 in Berlin
        val instant = moment(day, 17, 50, MOSCOW)
        assertTrue(EventRules.isPast(lesson, instant, MOSCOW, config))
        assertFalse(EventRules.isPast(lesson, instant, BERLIN, config))
    }

    @Test
    fun `the very first event is a lesson for the whole day`() {
        assertEquals(EventDefaults(LESSON, EventTime(null, null)), EventRules.defaults(emptyList(), emptyList()))
    }

    @Test
    fun `a new event takes the kind of the last one created and the time of that kind`() {
        val lesson = event(1, day, at(17), 45, createdAt = 100)
        val rehearsal = event(2, day.plusDays(2), at(19), null, kind = REHEARSAL, createdAt = 200)
        assertEquals(EventDefaults(REHEARSAL, EventTime(at(19), null)), EventRules.defaults(listOf(lesson, rehearsal), emptyList()))
        assertEquals(EventTime(at(17), 45), EventRules.timeOf(LESSON, listOf(lesson, rehearsal), emptyList()))
        assertEquals(EventTime(null, null), EventRules.timeOf(PERFORMANCE, listOf(lesson, rehearsal), emptyList()), "no performance yet")
        // equal moments: the smaller id — the first event of a repeat, not one laid later
        val tied = listOf(event(5, day, kind = REHEARSAL, createdAt = 300), event(4, day, kind = LESSON, createdAt = 300))
        assertEquals(LESSON, EventRules.defaults(tied, emptyList()).kind)
    }

    @Test
    fun `lessons the horizon laid after a concert do not make the next event a lesson`() {
        // plan D49: a weekly lesson created at 100, a concert at 500, then the horizon lays more lessons with the moment 100
        val lessons = weekly(seriesId = 7, dates = (0..11).map { day.plusDays(7 * it) }, start = at(17), duration = 45, firstId = 1, createdAt = 100)
        val concert = event(50, LocalDate(2026, 10, 24), at(18, 30), 90, kind = PERFORMANCE, createdAt = 500)
        val laidLater = weekly(seriesId = 7, dates = (12..13).map { day.plusDays(7 * it) }, start = at(17), duration = 45, firstId = 60, createdAt = 100)
        assertEquals(PERFORMANCE, EventRules.defaults(lessons + concert + laidLater, emptyList()).kind)
    }

    @Test
    fun `the time of a kind is that of its repeat when the last one repeats`() {
        // «Этот и следующие» moved the lessons to 17:30 from October on; the first lesson of September keeps 17:00, and the
        // latest one was moved to 19:00 by itself
        val first = event(1, day, at(17), 45, seriesId = 3, createdAt = 100)
        val later = event(2, day.plusDays(14), at(17, 30), 60, seriesId = 3, createdAt = 100)
        val moved = event(3, day.plusDays(21), at(19), 60, seriesId = 3, detached = true, createdAt = 100)
        val repeat = series(3, first = day, laidUntil = day.plusDays(84), start = at(17, 30), duration = 60)
        assertEquals(EventTime(at(17, 30), 60), EventRules.timeOf(LESSON, listOf(first, later, moved), listOf(repeat)), "the template, not one lesson's")
        assertEquals(EventTime(at(19), 60), EventRules.timeOf(LESSON, listOf(first, later, moved), emptyList()), "a repeat not known — the latest event's own")
    }

    @Test
    fun `after the lessons moved to another day for this and following the time is that of the new repeat`() {
        // Mondays at 17:00 for 45 minutes since 28.09 (repeat 3, created at 100); 19.10 moved to Tuesday 20.10 at 18:00 for this
        // and following: repeat 3 ends on 18.10, repeat 4 lays Tuesdays — all with the moment 100 (plan D49)
        val mondays = weekly(seriesId = 3, dates = (0..2).map { day.plusDays(7 * it) }, start = at(17), duration = 45, firstId = 1, createdAt = 100)
        val moved = event(4, LocalDate(2026, 10, 20), at(18), 45, seriesId = 4, createdAt = 100)
        val tuesdays = weekly(seriesId = 4, dates = (1..9).map { LocalDate(2026, 10, 20).plusDays(7 * it) }, start = at(18), duration = 45, firstId = 13, createdAt = 100)
        val ended = series(3, first = day, laidUntil = LocalDate(2026, 12, 20), until = LocalDate(2026, 10, 18), start = at(17), duration = 45)
        val laying = series(4, first = LocalDate(2026, 10, 20), laidUntil = LocalDate(2026, 12, 22), start = at(18), duration = 45)
        assertEquals(EventTime(at(18), 45), EventRules.timeOf(LESSON, mondays + moved + tuesdays, listOf(ended, laying)))
        assertEquals(EventTime(at(18), 45), EventRules.defaults(mondays + moved + tuesdays, listOf(ended, laying)).time)
        // a lesson gone by moved so: none moves into the new repeat, whose lessons carry the moment of the old one all the same
        assertEquals(EventTime(at(18), 45), EventRules.timeOf(LESSON, mondays + tuesdays, listOf(ended, laying)))
        // «Не повторять» at 19.10 with a new time: the repeat ends, the lesson stays single — the latest one, its own time
        val single = event(4, LocalDate(2026, 10, 19), at(17, 30), 45, createdAt = 100)
        assertEquals(EventTime(at(17, 30), 45), EventRules.timeOf(LESSON, mondays + single, listOf(ended)))
    }

    @Test
    fun `a time the change to summer time skips comes with the jump and an event never starts after it ends`() {
        // 29.03.2026 in Berlin: at 01:00Z the clock goes from 02:00 winter time to 03:00 summer time
        val spring = LocalDate(2026, 3, 29)
        val jump = Instant.parse("2026-03-29T01:00:00Z")
        assertEquals(jump, EventRules.momentOf(LocalDateTime(2026, 3, 29, 2, 30), BERLIN), "not 03:30, where kotlinx-datetime reads it")
        assertEquals(jump, EventRules.momentOf(LocalDateTime(2026, 3, 29, 2, 0), BERLIN))
        assertEquals(jump, EventRules.momentOf(LocalDateTime(2026, 3, 29, 3, 0), BERLIN))
        assertEquals(Instant.parse("2026-03-29T00:59:00Z"), EventRules.momentOf(LocalDateTime(2026, 3, 29, 1, 59), BERLIN))
        // the overlap of 25.10.2026 — the earlier of the two
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), EventRules.momentOf(LocalDateTime(2026, 10, 25, 2, 30), BERLIN))
        val hour = event(1, spring, at(2, 30), 60)
        assertEquals(jump, EventRules.startAt(hour, BERLIN))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), EventRules.endAt(hour, BERLIN, config), "03:30 summer time")
        val swallowed = event(2, spring, at(2), 15)
        assertEquals(jump, EventRules.startAt(swallowed, BERLIN), "the gap swallows it whole: it starts and ends with the jump")
        assertEquals(jump, EventRules.endAt(swallowed, BERLIN, config))
        for (start in at(1) until at(4) step 5) {
            for (duration in listOf(null, 15, 30, 45, 60, 90)) {
                val event = event(3, spring, start, duration)
                assertTrue(EventRules.startAt(event, BERLIN) <= EventRules.endAt(event, BERLIN, config), "$start + $duration")
            }
        }
    }

    @Test
    fun `the calendar goes a year ahead or to the farthest event`() {
        val today = LocalDate(2026, 9, 27)
        assertEquals(YearMonth(2027, 9), EventRules.calendarLastMonth(today, emptyList(), config))
        assertEquals(YearMonth(2027, 9), EventRules.calendarLastMonth(today, listOf(event(1, LocalDate(2027, 2, 1))), config))
        assertEquals(YearMonth(2027, 11), EventRules.calendarLastMonth(today, listOf(event(1, LocalDate(2027, 11, 3))), config))
    }

    @Test
    fun `the sheet of the date goes a year ahead or to the date being edited`() {
        val today = LocalDate(2026, 9, 27)
        assertEquals(YearMonth(2027, 9), EventRules.formLastMonth(today, null, config))
        assertEquals(YearMonth(2027, 9), EventRules.formLastMonth(today, LocalDate(2026, 12, 1), config))
        assertEquals(YearMonth(2028, 1), EventRules.formLastMonth(today, LocalDate(2028, 1, 10), config))
    }

    @Test
    fun `the chips of the end are the nearest days strictly after the first event`() {
        assertEquals(listOf(LocalDate(2026, 12, 31), LocalDate(2027, 5, 31)), EventRules.untilChips(LocalDate(2026, 9, 28), config))
        assertEquals(listOf(LocalDate(2027, 12, 31), LocalDate(2027, 5, 31)), EventRules.untilChips(LocalDate(2026, 12, 31), config))
        assertEquals(listOf(LocalDate(2027, 12, 31), LocalDate(2028, 5, 31)), EventRules.untilChips(LocalDate(2027, 5, 31), config))
        // a day a month does not have every year stands on its last day
        assertEquals(LocalDate(2027, 2, 28), YearDay(2, 29).inYear(2027))
        assertEquals(LocalDate(2028, 2, 29), YearDay(2, 29).inYear(2028))
    }

    @Test
    fun `frequent starts go by how many events have them and the later one first`() {
        val events = listOf(
            event(1, day, at(17)), event(2, day.plusDays(7), at(17)), event(3, day.plusDays(14), at(17)),
            event(4, day, at(11)), event(5, day.plusDays(1), at(11)),
            event(6, day.plusDays(3), at(19)), event(7, day.plusDays(2), at(19)),
            event(8, day, at(9)), event(9, day.plusDays(30), at(14)),
            event(10, day.plusDays(40)), event(11, day.plusDays(41)), event(12, day.plusDays(42)), event(13, day.plusDays(43)),
        )
        // 17:00 three times; 11:00 and 19:00 twice — 19:00 was used later; 14:00 once and later than 9:00; «весь день» is no start
        assertEquals(listOf(at(17), at(19), at(11), at(14)), EventRules.frequentStarts(events, limit = 4))
        assertEquals(listOf(at(17)), EventRules.frequentStarts(events, limit = 1))
        assertEquals(emptyList(), EventRules.frequentStarts(listOf(event(1, day), event(2, day)), limit = 4))
    }
}
