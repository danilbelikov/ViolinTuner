package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.MOSCOW
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }

    @Test
    fun `the program in one line is its composers and the titles of pieces without one`() {
        fun piece(id: Long, title: String, composer: String) =
            Piece(id, title, composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = 0, updatedAtEpochMs = 0)
        assertEquals("Вивальди · Мелодия", Performances.programLine(listOf(piece(1, "Концерт ля минор", "Вивальди"), piece(2, "Мелодия", " "))))
        assertEquals(
            "И. С. Бах · П. Чайковский",
            Performances.programLine(listOf(piece(1, "Менуэт", "И. С. Бах"), piece(2, "Гавот", "И. С. Бах"), piece(3, "Мелодия", "П. Чайковский"))),
            "a name met again is not repeated",
        )
        assertEquals("", Performances.programLine(emptyList()))
    }
}
