package com.violinjourney.app.core.domain.events

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The events of the calendar (spec 3.35): events, kinds, repeats and programs, and the event of each recording. The order
 * of the kinds for the screens is [KindRules.ordered] with the alphabet of the interface — the storage knows no language.
 */
interface EventRepository {
    /** By date and start, «весь день» first in a day. */
    val events: Flow<List<CalendarEvent>>

    /** The four built-in kinds with the colours given to them, then those of one's own by creation ([KindRules.all]). */
    val kinds: Flow<List<EventKind>>

    val series: Flow<List<EventSeries>>

    /** Event → the elements of the repertoire of its program, in their order (spec 3.35). */
    val programs: Flow<Map<Long, List<Long>>>

    /**
     * Event id → what a recording of it is named by: its title, date and kind (plan D11) — only events that have
     * recordings. Keyed by the event, not the recording: a recording finds its event by `SessionSummary.eventId` —
     * `recordEvents[summary.eventId]`. A query of its own, so that an edit of the events and the horizon read only it and
     * not all the recordings with a question to the files for each.
     */
    val recordEvents: Flow<Map<Long, SessionEvent>>

    suspend fun event(id: Long): CalendarEvent?

    /**
     * A new event (spec 3.35) — the first of a repeat when [repeat] is one and its [until] is not before it; created now
     * (plan D49). The repeat is laid ahead up to the horizon from [today] at once — from its first date, weeks gone by too
     * (plan D3). Sends `event_added` (spec 3.34).
     */
    suspend fun add(draft: EventDraft, repeat: Repeat, until: LocalDate?, today: LocalDate): Long

    /**
     * Carries out an edit or a deletion in one transaction ([SeriesEdits]); [frozenTitles] — event → the name its records
     * wore, written into those that kept the default one before they lose their event (plan D12). Lays the horizon from
     * [today] in the same transaction — a repeat split off is laid with the moment of the one it came from (plan D49).
     */
    suspend fun apply(plan: EventPlan, frozenTitles: Map<Long, String>, today: LocalDate)

    suspend fun setNotes(id: Long, notes: String)

    /** The program in this order; elements of the repertoire that are gone are left out. */
    suspend fun setProgram(eventId: Long, pieceIds: List<Long>)

    suspend fun removeFromProgram(eventId: Long, pieceId: Long)

    /** What the sheet «Вид» saves (spec 3.36.9); null — a new kind of one's own that does not fit: there are 20 already. */
    suspend fun saveKind(save: KindSave): KindRef?

    /** Its events and repeats become «Другое» (spec 3.35): nothing else of them is lost. */
    suspend fun deleteOwnKind(id: Long)

    /**
     * Lays every repeat ahead up to [today] and the horizon (spec 5.28, plan D3): at the start of the app and when «Занятия»
     * open. Done twice, or twice at once, it lays nothing the second time.
     */
    suspend fun layAhead(today: LocalDate)
}
