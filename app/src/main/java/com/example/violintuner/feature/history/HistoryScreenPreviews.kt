package com.example.violintuner.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.core.ui.theme.ViolinTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Handoff frame 4c and the states it does not show.

private val Moscow = ZoneId.of("Europe/Moscow")
private val Today = LocalDate.of(2026, 9, 17)
private val G = Zone.IN_TUNE
private val Y = Zone.NEAR
private val R = Zone.OFF

private fun session(id: Long, dateTime: String, score: Int, title: String?, bias: Double, preview: List<Zone>) = SessionSummary(
    id = id, title = title,
    startedAtEpochMs = LocalDateTime.parse(dateTime).atZone(Moscow).toInstant().toEpochMilli(),
    durationMs = 495_000 + id * 61_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
    scorePercent = score, nearPercent = 0, offPercent = 100 - score, maeCents = 6.0, biasCents = bias,
    previewZones = preview, audioPath = null,
)

private val Sessions = listOf(
    session(1, "2026-08-12T10:00:00", 62, "Гаммы G-dur", -7.0, listOf(G, Y, R, G, G, Y, G, Y)),
    session(2, "2026-08-20T10:00:00", 68, null, 3.0, listOf(Y, Y, G, R, Y, G, Y, Y)),
    session(3, "2026-08-27T10:00:00", 71, "Двойные ноты", 5.0, listOf(G, Y, G, G, Y, G, G, R)),
    session(4, "2026-09-10T15:30:00", 78, "Без названия", 9.0, listOf(Y, Y, G, R, Y, G, Y, Y)),
    session(5, "2026-09-13T21:02:00", 79, "Этюд Кайзера №3", -4.0, listOf(G, Y, G, G, Y, G, G, R)),
    session(6, "2026-09-16T08:15:00", 84, "Гаммы D-dur", -6.0, listOf(G, G, Y, G, R, G, Y, G)),
    session(7, "2026-09-17T07:00:00", 54, null, 12.0, listOf(R, Y, R, G)),
)

@Composable
private fun HistoryPreview(state: HistoryState) {
    ViolinTheme { HistoryScreen(state = state, onIntent = {}, zone = Moscow) }
}

private fun stateOf(sessions: List<SessionSummary>, filter: HistoryFilter = HistoryFilter.ALL) =
    HistoryReducer.stateOf(sessions, filter, Today, Moscow, IntonationConfig())

@Preview(name = "History", widthDp = 412, heightDp = 812)
@Composable
private fun HistoryFilledPreview() = HistoryPreview(stateOf(Sessions))

@Preview(name = "History · nothing under the filter", widthDp = 412, heightDp = 812)
@Composable
private fun HistoryEmptyFilterPreview() = HistoryPreview(stateOf(Sessions.take(3), HistoryFilter.THIS_WEEK))

@Preview(name = "History · no sessions yet", widthDp = 412, heightDp = 812)
@Composable
private fun HistoryEmptyPreview() = HistoryPreview(stateOf(emptyList()))

@Preview(name = "History · one session", widthDp = 412, heightDp = 812)
@Composable
private fun HistorySinglePreview() = HistoryPreview(stateOf(Sessions.takeLast(1)))

@Preview(name = "History · landscape", widthDp = 892, heightDp = 332)
@Composable
private fun HistoryLandscapePreview() = HistoryPreview(stateOf(Sessions))
