package com.example.violintuner.feature.practice

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.practice.PracticeEntry
import com.example.violintuner.core.domain.session.SessionSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeReducerTest {
    private val config = PracticeConfig()
    private val intonation = IntonationConfig()
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val today: LocalDate = LocalDate.of(2026, 9, 17)

    private fun entry(day: Int, minutes: Int) = PracticeEntry(
        date = LocalDate.of(2026, 9, day), startedAtEpochMs = 0, durationMs = minutes * MS_PER_MINUTE, manual = false,
    )

    private fun epoch(dateTime: String) = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun session(id: Long, startedAt: String) = SessionSummary(
        id = id, title = null, startedAtEpochMs = epoch(startedAt), durationMs = 60_000, a4Hz = 440.0,
        toleranceCents = 8.0, nearCents = 20.0, scorePercent = 80, nearPercent = 15, offPercent = 5,
        maeCents = 5.0, biasCents = -2.0, previewZones = listOf(Zone.IN_TUNE), audioPath = null,
    )

    private fun state(
        entries: List<PracticeEntry> = listOf(entry(1, 30), entry(4, 100), entry(16, 50), entry(17, 45)),
        sessions: List<SessionSummary> = emptyList(),
        runningMs: Long? = null,
        month: YearMonth = YearMonth.of(2026, 9),
        selected: LocalDate = today,
    ) = PracticeReducer.stateOf(entries, sessions, runningMs, month, selected, sheet = null, today, zone, config, intonation)

    @Test
    fun `calendar cells carry fill level, today, selection and future flags`() {
        val state = state()
        assertEquals(35, state.cells.size)
        assertNull(state.cells[0])
        val day4 = state.cells.filterNotNull().single { it.date.dayOfMonth == 4 }
        assertEquals(4, day4.fillLevel)
        assertFalse(day4.isToday)
        val day17 = state.cells.filterNotNull().single { it.date.dayOfMonth == 17 }
        assertEquals(3, day17.fillLevel)
        assertTrue(day17.isToday && day17.isSelected && !day17.isFuture)
        val day18 = state.cells.filterNotNull().single { it.date.dayOfMonth == 18 }
        assertEquals(0, day18.fillLevel)
        assertTrue(day18.isFuture)
        assertEquals(0, state.cells.filterNotNull().single { it.date.dayOfMonth == 3 }.fillLevel)
    }

    @Test
    fun `summary and today follow the entries`() {
        val state = state()
        assertEquals(45 * MS_PER_MINUTE, state.todayMs)
        assertEquals(PracticeSummary(weekMs = 95 * MS_PER_MINUTE, monthMs = 225 * MS_PER_MINUTE, streakDays = 2), state.summary)
        assertTrue(state.hasHistory)
        assertFalse(state.loading)
    }

    @Test
    fun `no entries is the empty state`() {
        val state = state(entries = emptyList())
        assertFalse(state.hasHistory)
        assertEquals(PracticeSummary(0, 0, 0), state.summary)
        assertEquals(0L, state.selected.totalMs)
    }

    @Test
    fun `the calendar cannot go past the current month`() {
        assertFalse(state().canGoForward)
        assertTrue(state(month = YearMonth.of(2026, 8)).canGoForward)
        assertEquals(0L, state(month = YearMonth.of(2026, 8)).summary.monthMs)
    }

    @Test
    fun `selected day lists its sessions newest first`() {
        val sessions = listOf(
            session(1, "2026-09-16T10:00:00"), session(2, "2026-09-16T23:30:00"), session(3, "2026-09-17T09:00:00"),
        )
        val state = state(sessions = sessions, selected = LocalDate.of(2026, 9, 16))
        assertEquals(listOf(2L, 1L), state.selected.sessions.map { it.id })
        assertEquals(50 * MS_PER_MINUTE, state.selected.totalMs)
        assertFalse(state.selected.isToday)
        assertTrue(state().selected.isToday)
    }

    @Test
    fun `summary sheet rounds to the minute and trims down to five minutes`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 47 * MS_PER_MINUTE + 30_000, config)
        assertEquals(48, sheet.minutes)
        assertEquals(5, sheet.minMinutes)
        assertEquals(48, sheet.maxMinutes)
        assertFalse(sheet.edited)
        assertEquals(47 * MS_PER_MINUTE + 30_000, PracticeReducer.durationToSave(sheet))

        val down = PracticeReducer.step(sheet, -1, config)
        assertEquals(43, down.minutes)
        assertTrue(down.edited)
        assertEquals(43 * MS_PER_MINUTE, PracticeReducer.durationToSave(down))
        assertEquals(48, PracticeReducer.step(down, +2, config).minutes)
        assertEquals(5, PracticeReducer.step(sheet, -20, config).minutes)
    }

    @Test
    fun `a practice shorter than five minutes cannot be trimmed`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 3 * MS_PER_MINUTE, config)
        assertEquals(3, sheet.minMinutes)
        assertEquals(3, sheet.maxMinutes)
        assertEquals(3, PracticeReducer.step(sheet, -1, config).minutes)
    }

    @Test
    fun `edit sheet steps, adds and clamps between zero and twelve hours`() {
        val sheet = PracticeReducer.editSheet(today, 85 * MS_PER_MINUTE, config)
        assertEquals(85, sheet.minutes)
        assertEquals(720, sheet.maxMinutes)
        assertEquals(90, PracticeReducer.step(sheet, +1, config).minutes)
        assertEquals(145, PracticeReducer.add(sheet, 60).minutes)
        assertEquals(720, PracticeReducer.add(sheet, 700).minutes)
        assertEquals(0, PracticeReducer.step(PracticeReducer.editSheet(today, 0, config), -1, config).minutes)
        assertEquals(720, PracticeReducer.editSheet(today, 13 * 60 * MS_PER_MINUTE, config).minutes)
    }
}
