package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.series
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class RecurrenceTest {
    private val monday = LocalDate(2026, 9, 28)
    private val today = LocalDate(2026, 9, 27)
    private val horizon = 12

    private fun mondays(from: LocalDate, to: LocalDate): List<LocalDate> = generateSequence(from) { it.plusDays(7) }.takeWhile { it <= to }.toList()

    @Test
    fun `a weekly repeat until the end of the year has fourteen mondays`() {
        assertEquals(UntilSummary(LocalDate(2026, 12, 28), 14), Recurrence.newSummary(monday, Repeat.WEEKLY, LocalDate(2026, 12, 31)))
        // «до» is inclusive: the last Monday itself counts
        assertEquals(UntilSummary(LocalDate(2026, 12, 28), 14), Recurrence.newSummary(monday, Repeat.WEEKLY, LocalDate(2026, 12, 28)))
        assertEquals(UntilSummary(LocalDate(2026, 12, 21), 7), Recurrence.newSummary(monday, Repeat.BIWEEKLY, LocalDate(2026, 12, 31)))
        assertNull(Recurrence.newSummary(monday, Repeat.WEEKLY, until = null), "no end — no count")
        assertEquals(UntilSummary(null, 0), Recurrence.newSummary(monday, Repeat.WEEKLY, LocalDate(2026, 9, 27)), "ends before it begins")
    }

    @Test
    fun `a stored repeat counts its events and the dates it will still lay`() {
        // laid to the horizon 20.12; the lesson of 12.10 was deleted «only this»: it does not come back and is not counted
        val stored = series(1, first = monday, laidUntil = LocalDate(2026, 12, 20), until = LocalDate(2026, 12, 31))
        val events = mondays(monday, LocalDate(2026, 12, 14)).filterNot { it == LocalDate(2026, 10, 12) }
            .mapIndexed { index, date -> event(index + 1L, date, at(17), 45, seriesId = 1) }
        assertEquals(11, events.size)
        assertEquals(UntilSummary(LocalDate(2026, 12, 28), 13), Recurrence.summary(stored, events), "11 laid and 21.12, 28.12 ahead")
        assertNull(Recurrence.summary(stored.copy(until = null), events))
        // events of another repeat are not this one's
        assertEquals(UntilSummary(LocalDate(2026, 12, 28), 13), Recurrence.summary(stored, events + event(99, monday, seriesId = 2)))
    }

    @Test
    fun `a repeat begun by an edit in the past counts only the days from the cut`() {
        // plan D4: anchored on 07.09, but laid only from the cut 05.10 — the Mondays of September have no events and are not counted
        val split = series(2, first = LocalDate(2026, 9, 7), laidUntil = LocalDate(2026, 10, 4), until = LocalDate(2026, 12, 31))
        assertEquals(UntilSummary(LocalDate(2026, 12, 28), 13), Recurrence.summary(split, emptyList()))
    }

    @Test
    fun `the horizon lays twelve weeks from today and then nothing more the same day`() {
        val fresh = series(1, first = monday, laidUntil = monday, start = at(17), duration = 45)
        val laying = Recurrence.toLay(fresh, today, horizon)
        assertEquals(LocalDate(2026, 12, 20), laying.laidUntil, "27.09 and twelve weeks")
        assertEquals(mondays(LocalDate(2026, 10, 5), LocalDate(2026, 12, 14)), laying.dates)
        val again = Recurrence.toLay(fresh.copy(laidUntil = laying.laidUntil), today, horizon)
        assertEquals(Laying(emptyList(), LocalDate(2026, 12, 20)), again)
        // the next day the horizon moves by a day: Monday 21.12 is laid then
        assertEquals(Laying(listOf(LocalDate(2026, 12, 21)), LocalDate(2026, 12, 21)), Recurrence.toLay(fresh.copy(laidUntil = laying.laidUntil), today.plusDays(1), horizon))
    }

    @Test
    fun `weeks the app was not opened in are laid too`() {
        // spec 5.28: a repeat means every week — half a year away, the Mondays gone by are laid as well
        val away = series(1, first = LocalDate(2026, 4, 6), laidUntil = LocalDate(2026, 6, 29))
        val laying = Recurrence.toLay(away, today, horizon)
        assertEquals(LocalDate(2026, 7, 6), laying.dates.first())
        assertEquals(LocalDate(2026, 12, 14), laying.dates.last())
        assertEquals(mondays(LocalDate(2026, 7, 6), LocalDate(2026, 12, 14)), laying.dates)
    }

    @Test
    fun `a clock set back lays nothing and never moves the bound back`() {
        val laid = series(1, first = monday, laidUntil = LocalDate(2026, 12, 20))
        assertEquals(Laying(emptyList(), LocalDate(2026, 12, 20)), Recurrence.toLay(laid, LocalDate(2026, 8, 1), horizon))
    }

    @Test
    fun `a repeat with an end is laid up to its end and no further`() {
        val short = series(1, first = monday, laidUntil = monday, until = LocalDate(2026, 10, 19))
        assertEquals(Laying(listOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 12), LocalDate(2026, 10, 19)), LocalDate(2026, 10, 19)), Recurrence.toLay(short, today, horizon))
        assertEquals(Laying(emptyList(), LocalDate(2026, 10, 19)), Recurrence.toLay(short.copy(laidUntil = LocalDate(2026, 10, 19)), today, horizon))
        assertFalse(Recurrence.hasDatesAhead(short.copy(laidUntil = LocalDate(2026, 10, 19))))
        assertTrue(Recurrence.hasDatesAhead(short))
        assertTrue(Recurrence.hasDatesAhead(short.copy(until = null, laidUntil = LocalDate(2030, 1, 1))), "no end — always")
    }

    @Test
    fun `a repeat that began long ago steps straight to the range asked for`() {
        assertEquals(
            listOf(LocalDate(2026, 9, 28), LocalDate(2026, 10, 12)),
            Recurrence.dates(LocalDate(2026, 1, 5), Repeat.BIWEEKLY, from = LocalDate(2026, 9, 22), to = LocalDate(2026, 10, 20), until = null),
        )
        assertEquals(listOf(monday), Recurrence.dates(monday, Repeat.NONE, from = today, to = monday, until = null))
        assertEquals(emptyList(), Recurrence.dates(monday, Repeat.WEEKLY, from = monday, to = LocalDate(2026, 12, 31), until = today))
    }

    @Test
    fun `a change to summer time moves no date of a repeat`() {
        // 29.03.2026 — the clocks of Europe go forward; the repeat is of local days, the Mondays stay Mondays
        val dates = Recurrence.dates(LocalDate(2026, 3, 23), Repeat.WEEKLY, LocalDate(2026, 3, 23), LocalDate(2026, 4, 6), until = null)
        assertEquals(listOf(LocalDate(2026, 3, 23), LocalDate(2026, 3, 30), LocalDate(2026, 4, 6)), dates)
        assertEquals(setOf(DayOfWeek.MONDAY), dates.map { Recurrence.weekday(it) }.toSet())
    }
}
