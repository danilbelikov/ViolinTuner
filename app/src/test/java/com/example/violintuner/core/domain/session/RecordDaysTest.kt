package com.example.violintuner.core.domain.session

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordDaysTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun session(dateTime: String, zone: ZoneId = moscow) = SessionSummary(
        id = 0, title = null,
        startedAtEpochMs = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli(),
        durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 0, offPercent = 20, maeCents = 0.0, biasCents = 0.0,
        previewZones = emptyList(), audioPath = null,
    )

    // 2026-09-17 is a Thursday; its week starts on Monday 2026-09-14.
    private val today = LocalDate.of(2026, 9, 17)

    @Test
    fun `fourteen days ending with today, empty days included`() {
        val days = RecordDays.daily(emptyList(), today, moscow, days = 14)
        assertEquals(14, days.size)
        assertEquals(LocalDate.of(2026, 9, 4), days.first().date)
        assertEquals(today, days.last().date)
        assertEquals(List(14) { 0 }, days.map { it.count })
    }

    @Test
    fun `recordings are counted on the day they started, older ones fall out`() {
        val sessions = listOf(
            session("2026-09-17T08:00:00"), session("2026-09-17T21:30:00"), session("2026-09-15T10:00:00"),
            session("2026-09-04T00:00:01"), session("2026-09-03T23:59:00"),
        )
        val days = RecordDays.daily(sessions, today, moscow, days = 14)
        assertEquals(2, days.last().count)
        assertEquals(1, days.first { it.date == LocalDate.of(2026, 9, 15) }.count)
        assertEquals(1, days.first().count)
        assertEquals(4, days.sumOf { it.count })
    }

    @Test
    fun `the day depends on the zone of the viewer`() {
        val lateEvening = session("2026-09-16T23:30:00")
        assertEquals(LocalDate.of(2026, 9, 16), RecordDays.dateOf(lateEvening, moscow))
        assertEquals(LocalDate.of(2026, 9, 17), RecordDays.dateOf(lateEvening, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `the scale follows the busiest day but never drops below the floor`() {
        fun days(vararg counts: Int) = counts.mapIndexed { i, c -> DayCount(today.minusDays(i.toLong()), c) }
        assertEquals(4, RecordDays.scaleTop(days(0, 1, 2), minTop = 4))
        assertEquals(12, RecordDays.scaleTop(days(12, 1), minTop = 4))
        assertEquals(4, RecordDays.scaleTop(emptyList(), minTop = 4))
    }

    @Test
    fun `weeks start on Monday`() {
        assertEquals(LocalDate.of(2026, 9, 14), HistoryWeeks.weekStartOf(today))
        assertEquals(LocalDate.of(2026, 9, 14), HistoryWeeks.weekStartOf(LocalDate.of(2026, 9, 14)))
        assertEquals(LocalDate.of(2026, 12, 28), HistoryWeeks.weekStartOf(LocalDate.of(2027, 1, 2)))
    }
}
