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
    fun `cards are newest first with day, duration, bias and a colored score`() {
        val cards = state().cards
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), cards.map { it.id })
        assertEquals(DayLabel.Today, cards[0].day)
        assertEquals(DayLabel.Yesterday, cards[1].day)
        assertEquals(DayLabel.On(LocalDate.of(2026, 9, 13)), cards[2].day)
        assertEquals(listOf(Zone.OFF, Zone.IN_TUNE, Zone.IN_TUNE, Zone.NEAR), cards.take(4).map { it.scoreZone })
        assertEquals("Гаммы D-dur", cards[1].title)
        assertNull(cards[0].title)
        assertEquals(495_000, cards[0].durationMs)
        assertEquals(9.0, cards[0].biasCents, 0.0)
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR), cards[0].previewZones)
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
        assertEquals(listOf(62, 69, null, null, 79, 69), filtered.weeks.map { it.averageScore }) // (71+66)/2, (84+54)/2
        assertEquals(-10, filtered.weekDelta)
        assertEquals(HistoryFilter.THIS_WEEK, filtered.filter)
        assertFalse(filtered.loading)
    }

    @Test
    fun `no sessions, and no sessions under the filter, are different states`() {
        val none = state(list = emptyList())
        assertEquals(0, none.totalCount)
        assertEquals(List(6) { null }, none.weeks.map { it.averageScore })
        assertNull(none.weekDelta)

        val oldOnly = state(HistoryFilter.THIS_WEEK, list = sessions.take(2))
        assertEquals(2, oldOnly.totalCount)
        assertEquals(emptyList<HistoryCard>(), oldOnly.cards)
    }

    @Test
    fun `sessions of the same moment keep a stable order`() {
        val twins = listOf(session(7, "2026-09-17T07:00:00", 50), session(8, "2026-09-17T07:00:00", 60))
        assertEquals(listOf(8L, 7L), state(list = twins).cards.map { it.id })
    }
}
