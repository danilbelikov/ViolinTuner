package com.example.violintuner.feature.history

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.SessionSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryReducerTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val config = IntonationConfig()

    // Thursday; the week started on Monday 2026-09-14.
    private val today = LocalDate.of(2026, 9, 17)

    private fun session(id: Long, dateTime: String, score: Int, title: String? = null, bias: Double = -4.0) = SessionSummary(
        id = id, title = title,
        startedAtEpochMs = LocalDateTime.parse(dateTime).atZone(moscow).toInstant().toEpochMilli(),
        durationMs = 495_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = score, nearPercent = 0, offPercent = 100 - score, maeCents = 5.0, biasCents = bias,
        previewZones = listOf(Zone.IN_TUNE, Zone.NEAR), audioPath = null,
    )

    private val sessions = listOf(
        session(1, "2026-08-12T10:00:00", 62),
        session(2, "2026-08-18T09:40:00", 71), // exactly 30 days back: outside the month
        session(3, "2026-08-19T09:40:00", 66), // 29 days back: inside
        session(4, "2026-09-13T21:02:00", 79, title = "Этюд Кайзера №3"), // Sunday: last week
        session(5, "2026-09-16T08:15:00", 84, title = "Гаммы D-dur"),
        session(6, "2026-09-17T07:00:00", 54, bias = 9.0),
    )

    private fun state(filter: HistoryFilter = HistoryFilter.ALL, list: List<SessionSummary> = sessions) =
        HistoryReducer.stateOf(list.shuffled(), filter, today, moscow, config)

    @Test
    fun `cards are newest first with their day and duration, and nothing about the score`() {
        val cards = state().cards
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), cards.map { it.id })
        assertEquals(today, cards[0].date)
        assertEquals(LocalDate.of(2026, 9, 13), cards[2].date)
        assertEquals("Гаммы D-dur", cards[1].title)
        assertNull(cards[0].title)
        assertEquals(495_000, cards[0].durationMs)
        assertFalse(cards[0].otherYear)
    }

    @Test
    fun `the list is grouped by day, newest day first, and today is told apart`() {
        val twoToday = sessions + session(7, "2026-09-17T19:30:00", 70)
        val groups = state(list = twoToday).groups
        assertEquals(listOf("2026-09-17", "2026-09-16", "2026-09-13", "2026-08-19", "2026-08-18", "2026-08-12"), groups.map { it.date.toString() })
        assertEquals(listOf(7L, 6L), groups[0].cards.map { it.id })
        assertEquals(listOf(true, false), groups.take(2).map { it.today })
        assertEquals(state(HistoryFilter.THIS_WEEK).cards.map { it.id }, state(HistoryFilter.THIS_WEEK).groups.flatMap { g -> g.cards.map { it.id } })
    }

    @Test
    fun `a recording of another year says so`() {
        val old = state(list = listOf(session(1, "2025-09-20T10:00:00", 60))).cards.single()
        assertEquals(true, old.otherYear)
    }

    @Test
    fun `the best take of a piece is marked, wherever it stands`() {
        val list = listOf(session(1, "2026-09-16T10:00:00", 60).copy(pieceId = 7), session(2, "2026-09-17T10:00:00", 90).copy(pieceId = 7))
        val cards = HistoryReducer.stateOf(list, HistoryFilter.ALL, today, moscow, config, bestTakeIds = setOf(1L)).cards
        assertEquals(listOf(2L to false, 1L to true), cards.map { it.id to it.best })
        assertEquals(listOf(true, true), cards.map { it.take })
    }

    @Test
    fun `this week starts on Monday`() {
        assertEquals(listOf(6L, 5L), state(HistoryFilter.THIS_WEEK).cards.map { it.id })
    }

    @Test
    fun `month is the last thirty days, today included`() {
        assertEquals(listOf(6L, 5L, 4L, 3L), state(HistoryFilter.MONTH).cards.map { it.id })
    }

    @Test
    fun `count and chart ignore the filter`() {
        val filtered = state(HistoryFilter.THIS_WEEK)
        assertEquals(6, filtered.totalCount)
        // fourteen days ending today: 13, 16 and 17 September have one recording each
        assertEquals(14, filtered.days.size)
        assertEquals(today, filtered.days.last().date)
        assertEquals(listOf("2026-09-13", "2026-09-16", "2026-09-17"), filtered.days.filter { it.count == 1 }.map { it.date.toString() })
        assertEquals(3, filtered.days.sumOf { it.count })
        assertEquals(config.historyChartMinTop, filtered.chartTop)
        assertEquals(HistoryFilter.THIS_WEEK, filtered.filter)
        assertFalse(filtered.loading)
    }

    @Test
    fun `no sessions, and no sessions under the filter, are different states`() {
        val none = state(list = emptyList())
        assertEquals(0, none.totalCount)
        assertEquals(List(14) { 0 }, none.days.map { it.count })

        val oldOnly = state(HistoryFilter.THIS_WEEK, list = sessions.take(2))
        assertEquals(2, oldOnly.totalCount)
        assertEquals(emptyList<HistoryCard>(), oldOnly.cards)
    }

    @Test
    fun `sessions of the same moment keep a stable order`() {
        val twins = listOf(session(7, "2026-09-17T07:00:00", 50), session(8, "2026-09-17T07:00:00", 60))
        assertEquals(listOf(8L, 7L), state(list = twins).cards.map { it.id })
    }

    @Test
    fun `a take carries the title of its piece, a free session and a take of a deleted piece do not`() {
        val take = session(1, "2026-09-16T18:00:00", 80)
        val sessions = listOf(take.copy(pieceId = 7), take.copy(id = 2), take.copy(id = 3, pieceId = 404))
        val state = HistoryReducer.stateOf(
            sessions, HistoryFilter.ALL, today, moscow, config, HistorySection.REPERTOIRE, pieceTitles = mapOf(7L to "Менуэт"),
        )
        assertEquals(mapOf(1L to "Менуэт", 2L to null, 3L to null), state.cards.associate { it.id to it.pieceTitle })
        assertEquals(HistorySection.REPERTOIRE, state.section)
    }
}
