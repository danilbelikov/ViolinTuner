package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.TestEvents.MOSCOW
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class PerformancesTest {
    private val config = EventsConfig()
    private val today = LocalDate(2026, 10, 24)
    private val tonight = event(1, today, at(18, 30), 90, kind = PERFORMANCE, title = "Осенний концерт")
    private val tomorrow = event(2, today.plusDays(1), kind = PERFORMANCE, title = "Отборочный тур")
    private val november = event(3, LocalDate(2026, 11, 20), at(19), null, kind = PERFORMANCE)
    private val spring = event(4, LocalDate(2026, 5, 16), at(15), 60, kind = PERFORMANCE)
    private val summer = event(5, LocalDate(2026, 6, 20), at(12), 60, kind = PERFORMANCE)
    private val lesson = event(6, today, at(10), 45)
    private val all = listOf(november, spring, lesson, tomorrow, summer, tonight)

    private fun piece(id: Long, title: String, composer: String, scale: ScaleSpec? = null) =
        Piece(id, title, composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = 0, updatedAtEpochMs = 0, scale = scale)

    private fun recording(id: Long, eventId: Long?, startedAt: Long, video: String? = null) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 220_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 4.0, biasCents = 1.0, previewZones = listOf(Zone.IN_TUNE),
        audioPath = "$id.m4a", videoPath = video, eventId = eventId,
    )

    @Test
    fun `performances are divided by their end and only they are listed`() {
        val split = Performances.split(all, moment(today, 19), MOSCOW, config)
        assertEquals(listOf(1L, 2L, 3L), split.ahead.map { it.id }, "tonight's concert is ahead until it is over; the nearest first")
        assertEquals(listOf(5L, 4L), split.past.map { it.id }, "the freshest first; the lesson is not a performance")
        val after = Performances.split(all, moment(today, 20), MOSCOW, config)
        assertEquals(listOf(2L, 3L), after.ahead.map { it.id })
        assertEquals(listOf(1L, 5L, 4L), after.past.map { it.id })
    }

    @Test
    fun `the row on the records says the nearest and how many are over`() {
        assertEquals(PerformancesLine.Ahead(tonight, days = 0, pastCount = 2), Performances.line(all, moment(today, 12), MOSCOW, config))
        assertEquals(PerformancesLine.Ahead(tomorrow, days = 1, pastCount = 3), Performances.line(all, moment(today, 20), MOSCOW, config))
        // the all-day round of tomorrow is over at the midnight after it: from 26.10 to 20.11 — 25 days (plan D38)
        assertEquals(PerformancesLine.Ahead(tomorrow, days = 0, pastCount = 3), Performances.line(all, moment(today.plusDays(1), 23, 59), MOSCOW, config))
        assertEquals(PerformancesLine.Ahead(november, days = 25, pastCount = 4), Performances.line(all, moment(today.plusDays(2), 0), MOSCOW, config))
        assertEquals(PerformancesLine.OnlyPast(count = 2, lastDate = LocalDate(2026, 6, 20)), Performances.line(listOf(spring, summer, lesson), moment(today, 12), MOSCOW, config))
        assertEquals(PerformancesLine.None, Performances.line(listOf(lesson), moment(today, 12), MOSCOW, config))
    }

    @Test
    fun `a performance of yesterday going past midnight is today until it ends`() {
        // 24.10 at 23:00 for two hours: at 00:30 on 25.10 it is still going — «сегодня», not «через −1 дн.» (plan D38)
        val late = event(7, today, at(23), 120, kind = PERFORMANCE, title = "Ночной концерт")
        assertEquals(PerformancesLine.Ahead(late, days = 0, pastCount = 2), Performances.line(listOf(late, spring, summer), moment(today.plusDays(1), 0, 30), MOSCOW, config))
        assertEquals(PerformancesLine.OnlyPast(count = 3, lastDate = today), Performances.line(listOf(late, spring, summer), moment(today.plusDays(1), 1), MOSCOW, config))
        // the row of the screen too: «сегодня» on the day after its start, then over
        assertEquals(0, Performances.rows(listOf(late), emptyMap(), emptyList(), emptyList(), moment(today.plusDays(1), 0, 30), MOSCOW, config).ahead.single().days)
    }

    @Test
    fun `the programme in one line is its composers and the titles of elements without one`() {
        assertEquals(listOf("Вивальди", "Мелодия"), Performances.programNames(listOf(piece(1, "Концерт ля минор", "Вивальди"), piece(2, "Мелодия", " "))))
        assertEquals(
            listOf("И. С. Бах", "П. Чайковский"),
            Performances.programNames(listOf(piece(1, "Менуэт", "И. С. Бах"), piece(2, "Гавот", "И. С. Бах"), piece(3, "Мелодия", "П. Чайковский"))),
            "a name met again is not repeated",
        )
        // a scale is named by its title, as the programme of the screen of an event names it (spec 3.22)
        val scale = piece(4, "Соль мажор, 2 октавы", "Гаммы Галамяна", scale = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, octaves = 2))
        assertEquals(listOf("Соль мажор, 2 октавы"), Performances.programNames(listOf(scale)))
        assertEquals(emptyList(), Performances.programNames(emptyList()))
    }

    /**
     * Spec 3.36.9: «Впереди» the nearest first with the term — «сегодня» until the concert is over, «завтра», «через N дн.» — «Прошли» the
     * freshest first without one; the place and the time; the programme in its order; at the end the newest video of the event, the
     * number of its recordings, or nothing; the year of a date not of this year, next year's too (plan D41).
     */
    @Test
    fun `the rows of the screen say the term the place the programme and what was recorded`() {
        val placed = tonight.copy(place = "  Малый зал музыкальной школы ")
        val nextYear = event(8, LocalDate(2027, 1, 15), at(17), 60, kind = PERFORMANCE, title = "Рождественский концерт")
        val lastYear = event(9, LocalDate(2025, 12, 27), null, null, kind = PERFORMANCE, place = "ДК «Строитель»")
        val events = listOf(placed, tomorrow, nextYear, spring, lastYear, lesson)
        // the programme of the concert in an order other than that of the repertoire: a row says it in its own order
        val programs = mapOf(1L to listOf(12L, 99L, 11L), 4L to listOf(12L))
        val pieces = listOf(piece(11, "Концерт ля минор, 1 ч.", "А. Вивальди"), piece(12, "Мелодия", "П. Чайковский"))
        val evening = 1_792_854_000_000L
        val sessions = listOf(
            recording(21, eventId = 4, startedAt = evening, video = "older.mp4"),
            recording(22, eventId = 4, startedAt = evening + 60_000, video = "newer.mp4"),
            recording(23, eventId = 4, startedAt = evening + 120_000),
            recording(24, eventId = 9, startedAt = evening),
            recording(25, eventId = 9, startedAt = evening + 1),
            recording(26, eventId = null, startedAt = evening),
        )
        val rows = Performances.rows(events, programs, pieces, sessions, moment(today, 12), MOSCOW, config)

        assertEquals(listOf(1L, 2L, 8L), rows.ahead.map { it.eventId }, "the nearest first; the lesson is not one")
        assertEquals(listOf(4L, 9L), rows.past.map { it.eventId }, "the freshest first")
        val concert = rows.ahead[0]
        assertEquals(EventName.Titled("Осенний концерт"), concert.name)
        assertEquals("Малый зал музыкальной школы", concert.place)
        assertEquals(at(18, 30), concert.startMinutes)
        assertEquals(0, concert.days, "today until it is over")
        assertEquals(listOf("П. Чайковский", "А. Вивальди"), concert.program, "in its order, not the repertoire's; an element gone is not in it")
        assertEquals(0, concert.records)
        assertNull(concert.lastVideo, "no recording — nothing at the end")
        assertEquals(1, rows.ahead[1].days, "завтра")
        assertNull(rows.ahead[1].startMinutes, "весь день")
        assertEquals(83, rows.ahead[2].days, "from 24 October 2026 to 15 January 2027")
        assertEquals(listOf(false, false, true), rows.ahead.map { it.otherYear }, "the year of next year too")
        assertEquals(listOf(emptyList(), emptyList<String>()), rows.ahead.drop(1).map { it.program }, "no programme — no line, not another's")

        val exam = rows.past[0]
        assertNull(exam.days, "no term for one over")
        assertEquals(listOf("П. Чайковский"), exam.program, "its own programme")
        assertEquals(3, exam.records)
        assertEquals("newer.mp4", exam.lastVideo, "the video of the newest recording that has one")
        val newYear = rows.past[1]
        assertEquals(2, newYear.records)
        assertNull(newYear.lastVideo, "recordings without a video — their number")
        assertEquals(true, newYear.otherYear)
        assertEquals(EventName.OfKind(PERFORMANCE, null), newYear.name, "an empty title — the name of the kind")
        assertEquals(emptyList(), newYear.program)

        val after = Performances.rows(events, programs, pieces, sessions, moment(today, 20), MOSCOW, config)
        assertEquals(listOf(1L, 4L, 9L), after.past.map { it.eventId }, "over at 20:00 — the freshest of «Прошли»")
        assertNull(after.past[0].days)
    }

    /** Plan D42: the list changes by itself at the end of a performance not over yet and at midnight — never at a start. */
    @Test
    fun `the list changes at the end of a performance ahead and at midnight`() {
        // 18:00, a concert until 20:00 (18:30 + 90): its end
        assertEquals(moment(today, 20), Performances.nextChangeAt(all, moment(today, 18), MOSCOW, config))
        // 20:00 — the concert is over; the round of tomorrow is all day: the midnight first
        assertEquals(moment(today.plusDays(1), 0), Performances.nextChangeAt(all, moment(today, 20), MOSCOW, config))
        // no performance today — the midnight; a lesson ending sooner changes nothing here
        assertEquals(moment(today.plusDays(1), 0), Performances.nextChangeAt(listOf(lesson, november), moment(today, 9), MOSCOW, config))
        // yesterday's concert going past midnight: its end, 01:00
        val late = event(7, today, at(23), 120, kind = PERFORMANCE)
        assertEquals(moment(today.plusDays(1), 1), Performances.nextChangeAt(listOf(late), moment(today.plusDays(1), 0, 30), MOSCOW, config))
        assertEquals(moment(today.plusDays(1), 0), Performances.nextChangeAt(emptyList(), moment(today, 12), MOSCOW, config))
    }
}
