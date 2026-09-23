package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.session.HistoryWeeks
import com.violinjourney.app.core.domain.session.RecordDays
import com.violinjourney.app.core.domain.session.SessionSummary
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
        section: HistorySection = HistorySection.SESSIONS,
        /** Titles of the pieces by id: a take is named after its piece (spec 3.15). */
        pieceTitles: Map<Long, String> = emptyMap(),
        /** The takes their players marked as the best of their pieces (spec 3.21). */
        bestTakeIds: Set<Long> = emptySet(),
    ): HistoryState {
        val days = RecordDays.daily(sessions, today, zone, config.historyChartDays)
        return HistoryState(
            section = section,
            loading = false,
            totalCount = sessions.size,
            days = days,
            chartTop = RecordDays.scaleTop(days, config.historyChartMinTop),
            today = today,
            filter = filter,
            cards = sessions
                .filter { passes(filter, RecordDays.dateOf(it, zone), today, config) }
                .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
                .map { cardOf(it, today, zone, pieceTitle = it.pieceId?.let(pieceTitles::get), best = it.id in bestTakeIds) },
        )
    }

    fun loading(filter: HistoryFilter, section: HistorySection = HistorySection.SESSIONS): HistoryState =
        HistoryState(
            section = section, loading = true, totalCount = 0, days = emptyList(), chartTop = 0, today = LocalDate.MIN,
            filter = filter, cards = emptyList(),
        )

    private fun passes(filter: HistoryFilter, date: LocalDate, today: LocalDate, config: IntonationConfig): Boolean =
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.THIS_WEEK -> !date.isBefore(HistoryWeeks.weekStartOf(today))
            HistoryFilter.MONTH -> date.isAfter(today.minusDays(config.historyMonthDays.toLong()))
        }

    /** Also the card of the "Записи этого дня" list on the practice screen and of a take on the screen of its piece. */
    fun cardOf(session: SessionSummary, today: LocalDate, zone: ZoneId, pieceTitle: String? = null, best: Boolean = false): HistoryCard =
        HistoryCard(
            id = session.id,
            title = session.title,
            startedAtEpochMs = session.startedAtEpochMs,
            date = RecordDays.dateOf(session, zone),
            otherYear = RecordDays.dateOf(session, zone).year != today.year,
            durationMs = session.durationMs,
            pieceTitle = pieceTitle,
            pieceId = session.pieceId,
            hasAudio = session.audioPath != null,
            hasVideo = session.videoPath != null,
            best = best,
        )
}
