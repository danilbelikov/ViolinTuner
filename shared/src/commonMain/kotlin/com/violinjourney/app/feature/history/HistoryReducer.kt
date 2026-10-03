package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.Performances
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.session.RecordDays
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        /** The events of the recordings by the id of the event (spec 3.35, plan D11): a recording of one is named by it. */
        recordEvents: Map<Long, SessionEvent> = emptyMap(),
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
                    cardOf(
                        it, today, zone, pieceTitle = it.pieceId?.let(pieceTitles::get), best = it.id in bestTakeIds, underBacking = it.id in underBackingIds,
                        event = it.eventId?.let(recordEvents::get),
                    )
                },
        )
    }

    /**
     * The row «Выступления» at [now] (spec 3.36.9): its caption — the nearest performance ahead with its term and how many are over,
     * only those over and the date of the last one, or none at all ([Performances.line]) — and the look of the kind «Выступление» from
     * [kinds] (its colour may have been changed). Apart from the list: it changes with the events and the time, the list does not.
     */
    fun performancesOf(events: List<CalendarEvent>, kinds: List<EventKind>, now: Instant, zone: TimeZone, config: EventsConfig): HistoryPerformances =
        HistoryPerformances(
            line = Performances.line(events, now, zone, config),
            look = KindRules.lookOf(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), kinds, config),
            today = now.toLocalDateTime(zone).date,
        )

    fun loading(filter: HistoryFilter): HistoryState =
        HistoryState(
            loading = true, totalCount = 0, days = emptyList(), chartTop = 0, today = NOT_LOADED,
            filter = filter, cards = emptyList(),
        )

    /**
     * The chips by kind (spec 3.36.5, 3.36.9), from what a recording stores — its piece, its event and its video: a take is bound to a
     * piece (a video take too); a video is one whether its file is there or lost, and whoever's it is — of a piece, of an event; from Live
     * is bound to nothing and has no video. The sound of an event is only under «Все».
     */
    private fun passes(filter: HistoryFilter, session: SessionSummary): Boolean =
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.TAKES -> session.pieceId != null
            HistoryFilter.VIDEO -> session.videoPath != null
            HistoryFilter.LIVE -> session.pieceId == null && session.videoPath == null && session.eventId == null
        }

    /** Also the card of the "Записи этого дня" list on the practice screen, of a take on the screen of its piece and of a recording on the screen of its event. */
    fun cardOf(
        session: SessionSummary,
        today: LocalDate,
        zone: TimeZone,
        pieceTitle: String? = null,
        best: Boolean = false,
        underBacking: Boolean = false,
        /** The event of the recording (spec 3.35): it names a recording without a name of its own. */
        event: SessionEvent? = null,
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
            thumbPath = session.thumbPath,
            best = best,
            underBacking = underBacking,
            event = event,
        )
}
