package com.example.violintuner.feature.history

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.HistoryWeeks
import com.example.violintuner.core.domain.session.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stored sessions → history screen (spec 3.11). Pure: "today" and the zone come from outside. */
object HistoryReducer {
    fun stateOf(
        sessions: List<SessionSummary>,
        filter: HistoryFilter,
        today: LocalDate,
        zone: ZoneId,
        config: IntonationConfig,
    ): HistoryState {
        val weeks = HistoryWeeks.weekly(sessions, today, zone, config.historyWeeks)
        return HistoryState(
            loading = false,
            totalCount = sessions.size,
            weeks = weeks,
            weekDelta = HistoryWeeks.delta(weeks),
            filter = filter,
            cards = sessions
                .filter { passes(filter, dateOf(it, zone), today, config) }
                .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
                .map { cardOf(it, today, zone, config) },
        )
    }

    fun loading(filter: HistoryFilter): HistoryState =
        HistoryState(loading = true, totalCount = 0, weeks = emptyList(), weekDelta = null, filter = filter, cards = emptyList())

    private fun passes(filter: HistoryFilter, date: LocalDate, today: LocalDate, config: IntonationConfig): Boolean =
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.THIS_WEEK -> !date.isBefore(HistoryWeeks.weekStartOf(today))
            HistoryFilter.MONTH -> date.isAfter(today.minusDays(config.historyMonthDays.toLong()))
        }

    /** Also the card of the "Записи этого дня" list on the practice screen. */
    fun cardOf(session: SessionSummary, today: LocalDate, zone: ZoneId, config: IntonationConfig): HistoryCard {
        val date = dateOf(session, zone)
        return HistoryCard(
            id = session.id,
            title = session.title,
            startedAtEpochMs = session.startedAtEpochMs,
            day = when (date) {
                today -> DayLabel.Today
                today.minusDays(1) -> DayLabel.Yesterday
                else -> DayLabel.On(date)
            },
            durationMs = session.durationMs,
            biasCents = session.biasCents,
            scorePercent = session.scorePercent,
            scoreZone = when {
                session.scorePercent >= config.scoreGoodPercent -> Zone.IN_TUNE
                session.scorePercent >= config.scoreFairPercent -> Zone.NEAR
                else -> Zone.OFF
            },
            previewZones = session.previewZones,
        )
    }

    private fun dateOf(session: SessionSummary, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zone).toLocalDate()
}
