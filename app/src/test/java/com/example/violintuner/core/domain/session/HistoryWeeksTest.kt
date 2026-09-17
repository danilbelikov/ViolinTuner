package com.example.violintuner.core.domain.session

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryWeeksTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun session(dateTime: String, score: Int, zone: ZoneId = moscow) = SessionSummary(
        id = 0, title = null,
        startedAtEpochMs = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli(),
        durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = score, nearPercent = 0, offPercent = 100 - score, maeCents = 0.0, biasCents = 0.0,
        previewZones = emptyList(), audioPath = null,
    )

    // 2026-09-17 is a Thursday; its week starts on Monday 2026-09-14.
    private val today = LocalDate.of(2026, 9, 17)

    @Test
    fun `six weeks ending with the current one, starting on Mondays`() {
        val weeks = HistoryWeeks.weekly(emptyList(), today, moscow, weeks = 6)
        assertEquals(
            listOf("2026-08-10", "2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14"),
            weeks.map { it.weekStart.toString() },
        )
        assertEquals(List(6) { null }, weeks.map { it.averageScore })
    }

    @Test
    fun `week score is the rounded mean of its sessions, empty weeks stay empty`() {
        val sessions = listOf(
            session("2026-09-14T00:00:00", 80), // Monday midnight belongs to the new week
            session("2026-09-16T19:30:00", 85),
            session("2026-09-13T23:59:59", 60), // Sunday night: the week before
            session("2026-08-12T10:00:00", 70),
            session("2026-07-01T10:00:00", 10), // older than the chart
        )
        val weeks = HistoryWeeks.weekly(sessions, today, moscow, weeks = 6)
        assertEquals(listOf(70, null, null, null, 60, 83), weeks.map { it.averageScore })
    }

    @Test
    fun `delta compares the last two weeks and needs both`() {
        fun weeks(vararg scores: Int?) = scores.mapIndexed { i, s -> WeekScore(today.plusWeeks(i.toLong()), s) }
        assertEquals(6, HistoryWeeks.delta(weeks(70, 78, 84)))
        assertEquals(-4, HistoryWeeks.delta(weeks(80, 76)))
        assertNull(HistoryWeeks.delta(weeks(80, null)))
        assertNull(HistoryWeeks.delta(weeks(null, 80)))
        assertNull(HistoryWeeks.delta(weeks(80)))
    }

    @Test
    fun `the week of a session depends on the viewer's time zone`() {
        // Sunday 23:30 in Moscow is already Monday 05:30 in Tokyo-ish (+9): a different week.
        val lateSunday = session("2026-09-13T23:30:00", 90)
        val inMoscow = HistoryWeeks.weekly(listOf(lateSunday), today, moscow, 2)
        val inTokyo = HistoryWeeks.weekly(listOf(lateSunday), today, ZoneId.of("Asia/Tokyo"), 2)
        assertEquals(listOf(90, null), inMoscow.map { it.averageScore })
        assertEquals(listOf(null, 90), inTokyo.map { it.averageScore })
    }

    @Test
    fun `weeks cross the year boundary`() {
        val weeks = HistoryWeeks.weekly(emptyList(), LocalDate.of(2027, 1, 2), moscow, weeks = 2)
        assertEquals(listOf("2026-12-21", "2026-12-28"), weeks.map { it.weekStart.toString() })
    }

    @Test
    fun `a daylight saving switch does not move sessions between weeks`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Clocks go back on Sunday 2026-10-25; the evening still belongs to the week of Oct 19.
        val sundayEvening = session("2026-10-25T22:00:00", 75, berlin)
        val weeks = HistoryWeeks.weekly(listOf(sundayEvening), LocalDate.of(2026, 10, 27), berlin, 2)
        assertEquals(listOf(75, null), weeks.map { it.averageScore })
    }
}
