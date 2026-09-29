package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.session.RecordDays
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Stored sessions → history screen (spec 3.11). Pure: "today" and the zone come from outside. */
object HistoryReducer {
    /** «Today» of a list still loading: never shown, only there to be a date. */
    private val NOT_LOADED = LocalDate(1970, 1, 1)

    fun stateOf(
        sessions: List<SessionSummary>,
        filter: HistoryFilter,
        today: LocalDate,
        zone: TimeZone,
        config: IntonationConfig,
        /** Titles of the pieces by id: a take is named after its piece (spec 3.15). */
        pieceTitles: Map<Long, String> = emptyMap(),
        /** The takes their players marked as the best of their pieces (spec 3.21). */
        bestTakeIds: Set<Long> = emptySet(),
        /** The takes made under a backing (spec 3.32): their cards carry its sign. */
        underBackingIds: Set<Long> = emptySet(),
    ): HistoryState {
        val days = RecordDays.daily(sessions, today, zone, config.historyChartDays)
        return HistoryState(
            loading = false,
            totalCount = sessions.size,
            days = days,
            chartTop = RecordDays.scaleTop(days, config.historyChartMinTop),
            today = today,
            filter = filter,
            cards = sessions
                .filter { passes(filter, it) }
                .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
                .map {
                    cardOf(it, today, zone, pieceTitle = it.pieceId?.let(pieceTitles::get), best = it.id in bestTakeIds, underBacking = it.id in underBackingIds)
                },
        )
    }

    fun loading(filter: HistoryFilter): HistoryState =
        HistoryState(
            loading = true, totalCount = 0, days = emptyList(), chartTop = 0, today = NOT_LOADED,
            filter = filter, cards = emptyList(),
        )

    /**
     * The chips by kind (spec 3.36.5), from what a recording stores — its piece and its video: a take is bound to a piece (a video
     * take too); a video is one whether its file is there or lost; from Live is bound to nothing and has no video.
     */
    private fun passes(filter: HistoryFilter, session: SessionSummary): Boolean =
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.TAKES -> session.pieceId != null
            HistoryFilter.VIDEO -> session.videoPath != null
            HistoryFilter.LIVE -> session.pieceId == null && session.videoPath == null
        }

    /** Also the card of the "Записи этого дня" list on the practice screen and of a take on the screen of its piece. */
    fun cardOf(
        session: SessionSummary,
        today: LocalDate,
        zone: TimeZone,
        pieceTitle: String? = null,
        best: Boolean = false,
        underBacking: Boolean = false,
    ): HistoryCard =
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
            underBacking = underBacking,
        )
}
