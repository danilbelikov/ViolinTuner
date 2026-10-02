package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.BERLIN
import com.violinjourney.app.core.domain.events.TestEvents.LESSON
import com.violinjourney.app.core.domain.events.TestEvents.MOSCOW
import com.violinjourney.app.core.domain.events.TestEvents.OTHER
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class EventReminderTest {
    private val config = EventsConfig()
    private val kinds = KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1)), config)
    private val monday = LocalDate(2026, 9, 28)
    private val tuesday = monday.plusDays(1)

    private fun reminder(events: List<CalendarEvent>, now: Instant, zone: TimeZone = MOSCOW) = EventReminder.of(events, kinds, now, zone, config)

    private fun next(events: List<CalendarEvent>, now: Instant, zone: TimeZone = MOSCOW) = EventReminder.nextRecalcAt(events, now, zone, config)

    @Test
    fun `tomorrow's event comes at midnight of today and leaves when it ends`() {
        val lesson = event(1, tuesday, at(17), 45, place = "Анна Сергеевна")
        assertNull(reminder(listOf(lesson), moment(monday.plusDays(-1), 23, 59)), "two days before — no card")
        val tomorrow = assertNotNull(reminder(listOf(lesson), moment(monday, 0, 0))).rows.single()
        assertEquals(ReminderDay.TOMORROW, tomorrow.day)
        assertEquals(EventName.OfKind(LESSON, null), tomorrow.name, "no title — the word of the kind")
        assertEquals("Анна Сергеевна", tomorrow.place)
        assertEquals(KindLook(KindSign.LESSON, 0), tomorrow.look)
        assertNull(tomorrow.running)
        val going = assertNotNull(reminder(listOf(lesson), moment(tuesday, 17, 44))).rows.single()
        assertEquals(ReminderDay.TODAY, going.day)
        assertEquals(Running.Until(LocalDateTime(2026, 9, 29, 17, 45)), going.running, "· идёт, до 17:45")
        assertNull(reminder(listOf(lesson), moment(tuesday, 17, 45)), "over — the card leaves by itself")
    }

    @Test
    fun `one without a length is going for an hour and one for the whole day never is`() {
        val concert = event(1, tuesday, at(18, 30), null, kind = PERFORMANCE, title = "Осенний концерт")
        val row = assertNotNull(reminder(listOf(concert), moment(tuesday, 19))).rows.single()
        assertEquals(Running.Open, row.running, "· идёт")
        assertEquals(EventName.Titled("Осенний концерт"), row.name)
        assertNull(reminder(listOf(concert), moment(tuesday, 19, 30)), "its end is the start and an hour")
        val allDay = event(2, tuesday, kind = OTHER)
        assertNull(assertNotNull(reminder(listOf(allDay), moment(tuesday, 12))).rows.single().running, "«весь день» has no «идёт»")
        assertNotNull(reminder(listOf(allDay), moment(tuesday, 23, 59)))
        assertNull(reminder(listOf(allDay), moment(tuesday.plusDays(1), 0, 0)), "until the midnight after its day")
    }

    @Test
    fun `yesterday's event past midnight stays until it ends`() {
        val late = event(1, monday, at(23), 120)
        val row = assertNotNull(reminder(listOf(late), moment(tuesday, 0, 30))).rows.single()
        assertEquals(ReminderDay.YESTERDAY, row.day, "«Вчера в 23:00»")
        assertEquals(Running.Until(LocalDateTime(2026, 9, 29, 1, 0)), row.running)
        assertNull(reminder(listOf(late), moment(tuesday, 1, 0)))
    }

    @Test
    fun `the card shows two rows and the compact one one and the rest are more`() {
        val events = listOf(
            event(4, tuesday, at(17), 45),
            event(3, tuesday, at(14), 90, kind = KindRef.Custom(9)),
            event(2, tuesday, null, null, kind = OTHER, title = "Замена струн"),
            event(1, monday, at(19), 60, kind = KindRef.Custom(77)),
        )
        val card = assertNotNull(reminder(events, moment(monday, 12)))
        // by date, «весь день» first in a day, then by start
        assertEquals(listOf(1L, 2L, 3L, 4L), card.rows.map { it.eventId })
        assertEquals(listOf(ReminderDay.TODAY, ReminderDay.TOMORROW, ReminderDay.TOMORROW, ReminderDay.TOMORROW), card.rows.map { it.day })
        assertEquals(EventName.OfKind(OTHER, null), card.rows[0].name, "a kind of one's own that is gone reads as «Другое»")
        assertEquals(KindLook(KindSign.ARC, 1), card.rows[2].look)
        assertEquals(EventName.OfKind(KindRef.Custom(9), "Оркестр"), card.rows[2].name)
        assertEquals(listOf(1L, 2L), card.visible(compact = false).map { it.eventId })
        assertEquals(listOf(3L, 4L), card.hidden(compact = false).map { it.eventId }, "«ещё 2»")
        assertEquals(tuesday, card.firstHiddenDate(compact = false))
        assertEquals(listOf(1L), card.visible(compact = true).map { it.eventId })
        assertEquals(3, card.hidden(compact = true).size, "«ещё 3»")
        assertEquals(tuesday, card.firstHiddenDate(compact = true))
        assertNull(assertNotNull(reminder(events.take(1), moment(tuesday, 12))).firstHiddenDate(compact = false), "one event — no «ещё»")
    }

    /**
     * «ещё N» names the kind of each event it stands for (plan D8): a row knows its kind whatever its title — a kind of one's own with its
     * name as written, one that is gone as «Другое» without a name, a built-in one without a name.
     */
    @Test
    fun `a row knows its kind and the name of a kind of ones own`() {
        val events = listOf(
            event(1, tuesday, at(14), 90, kind = KindRef.Custom(9), title = "Сводная репетиция"),
            event(2, tuesday, at(17), 45, kind = KindRef.Custom(77)),
            event(3, tuesday, at(19), 60, kind = PERFORMANCE, title = "Осенний концерт"),
        )
        val rows = assertNotNull(reminder(events, moment(monday, 12))).rows
        assertEquals(listOf(KindRef.Custom(9), OTHER, PERFORMANCE), rows.map { it.kind }, "a titled event of «Оркестр» is of «Оркестр»")
        assertEquals(listOf("Оркестр", null, null), rows.map { it.ownName })
        assertEquals(EventName.Titled("Сводная репетиция"), rows[0].name, "its title, not the name of its kind, is what its row says")
    }

    @Test
    fun `the card is reckoned again at the start and the end of its events and at midnight`() {
        val lesson = event(1, tuesday, at(17), 45)
        assertEquals(moment(tuesday, 17), next(listOf(lesson), moment(tuesday, 16, 30)), "«идёт» comes at the start")
        assertEquals(moment(tuesday, 17, 45), next(listOf(lesson), moment(tuesday, 17, 10)), "the card leaves at the end")
        assertEquals(moment(tuesday.plusDays(1), 0), next(listOf(lesson), moment(tuesday, 17, 45)), "over — the next midnight")
        assertEquals(moment(tuesday, 0), next(emptyList(), moment(monday, 9)), "no events — midnight")
        assertEquals(moment(tuesday, 0), next(listOf(lesson), moment(monday, 9)), "tomorrow's event begins tomorrow: midnight comes first")
        // the end of yesterday's event past midnight
        assertEquals(moment(tuesday, 1), next(listOf(event(2, monday, at(23), 120)), moment(tuesday, 0, 20)))
    }

    @Test
    fun `an event at seventeen is at seventeen wherever the phone is`() {
        val lesson = event(1, tuesday, at(17), 45)
        val instant = moment(tuesday, 17, 30, MOSCOW) // 16:30 in Berlin
        assertEquals(Running.Until(LocalDateTime(2026, 9, 29, 17, 45)), assertNotNull(reminder(listOf(lesson), instant, MOSCOW)).rows.single().running)
        assertNull(assertNotNull(reminder(listOf(lesson), instant, BERLIN)).rows.single().running, "in Berlin it has not begun yet")
        assertEquals(moment(tuesday, 17, 0, BERLIN), next(listOf(lesson), instant, BERLIN))
    }

    @Test
    fun `when the clock goes back in autumn an event over does not come back`() {
        // 25.10.2026 in Berlin: 03:00 summer time becomes 02:00, the hour from 02:00 to 03:00 is there twice
        val autumn = LocalDate(2026, 10, 25)
        val night = event(1, autumn, at(2), 45)
        val start = Instant.parse("2026-10-25T00:00:00Z") // 02:00 summer time, the first one
        assertEquals(start, next(listOf(night), Instant.parse("2026-10-24T23:50:00Z"), BERLIN))
        assertEquals(Instant.parse("2026-10-25T00:45:00Z"), next(listOf(night), start, BERLIN), "it ends at the first 02:45")
        assertNull(reminder(listOf(night), Instant.parse("2026-10-25T00:50:00Z"), BERLIN))
        assertNull(reminder(listOf(night), Instant.parse("2026-10-25T01:10:00Z"), BERLIN), "02:10 winter time: over, and not back")
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), next(listOf(night), Instant.parse("2026-10-25T01:10:00Z"), BERLIN), "the next midnight, in winter time")
    }

    @Test
    fun `in spring the missing hour is skipped and the day has twenty three hours`() {
        // 29.03.2026 in Berlin: 02:00 winter time becomes 03:00 summer time; an event at 02:30 for half an hour
        val spring = LocalDate(2026, 3, 29)
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), next(emptyList(), Instant.parse("2026-03-28T11:00:00Z"), BERLIN))
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), next(emptyList(), Instant.parse("2026-03-29T10:00:00Z"), BERLIN), "23 hours later")
        val inTheGap = event(1, spring, at(2, 30), 30)
        val justAfterMidnight = Instant.parse("2026-03-28T23:30:00Z")
        assertNotNull(reminder(listOf(inTheGap), justAfterMidnight, BERLIN))
        // it starts and ends at 03:00 summer time, the first moment after the gap; the reckoning never falls behind the clock
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), next(listOf(inTheGap), justAfterMidnight, BERLIN))
        assertNull(reminder(listOf(inTheGap), Instant.parse("2026-03-29T01:00:00Z"), BERLIN))
    }

    @Test
    fun `in spring an event of the missing hour is going from the jump to its end`() {
        // 29.03.2026 in Berlin: at 01:00Z 02:00 winter time becomes 03:00 summer time; a lesson at 02:30 for an hour
        val hour = event(1, LocalDate(2026, 3, 29), at(2, 30), 60)
        val jump = Instant.parse("2026-03-29T01:00:00Z")
        assertNull(assertNotNull(reminder(listOf(hour), Instant.parse("2026-03-29T00:59:00Z"), BERLIN)).rows.single().running, "01:59 — not yet")
        assertEquals(
            Running.Until(LocalDateTime(2026, 3, 29, 3, 30)),
            assertNotNull(reminder(listOf(hour), jump, BERLIN)).rows.single().running,
            "· идёт, до 03:30 — from 03:00, not from 03:30",
        )
        assertEquals(jump, next(listOf(hour), Instant.parse("2026-03-28T23:30:00Z"), BERLIN), "«идёт» comes with the jump")
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next(listOf(hour), jump, BERLIN), "and the card leaves at 03:30")
    }
}
