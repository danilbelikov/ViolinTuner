package com.violinjourney.app.core.domain.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * In-memory events for view model tests; ids count up from 1, the rules are the real ones. A plan is kept in [applied],
 * not carried out — the storage's own tests carry plans out; the horizon is counted in [laidAhead], not laid.
 */
class FakeEventRepository(private val config: EventsConfig = EventsConfig()) : EventRepository {
    override val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    override val series = MutableStateFlow<List<EventSeries>>(emptyList())
    override val programs = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    override val recordEvents = MutableStateFlow<Map<Long, SessionEvent>>(emptyMap())

    /** The rows of `event_kinds`: the colours given to built-in kinds and the kinds of one's own. */
    val storedKinds = MutableStateFlow<List<StoredKind>>(emptyList())
    override val kinds: Flow<List<EventKind>> = storedKinds.map { KindRules.all(it, config) }

    /** The day of every call of [layAhead], in order. */
    val laidAhead = mutableListOf<LocalDate>()

    /** Every plan given to [apply], with its frozen titles. */
    val applied = mutableListOf<Pair<EventPlan, Map<Long, String>>>()

    /** The moment [add] and [saveKind] create things at. */
    var nowEpochMs = 0L

    private var nextId = 1L

    override suspend fun event(id: Long): CalendarEvent? = events.value.firstOrNull { it.id == id }

    override suspend fun add(draft: EventDraft, repeat: Repeat, until: LocalDate?, today: LocalDate): Long {
        val clean = EventRules.clean(draft, config)
        val seriesId = if (repeat == Repeat.NONE || (until != null && until < clean.date)) null else nextId++
        if (seriesId != null) {
            series.update {
                it + EventSeries(
                    seriesId, clean.kind, repeat, clean.date, until, clean.date, clean.startMinutes, clean.durationMinutes, clean.title, clean.place,
                )
            }
        }
        val id = nextId++
        events.update {
            it + CalendarEvent(
                id, clean.kind, clean.date, clean.startMinutes, clean.durationMinutes, clean.title, clean.place, clean.notes,
                seriesId, detached = false, createdAtEpochMs = nowEpochMs,
            )
        }
        return id
    }

    override suspend fun apply(plan: EventPlan, frozenTitles: Map<Long, String>, today: LocalDate) {
        applied += plan to frozenTitles
    }

    override suspend fun setNotes(id: Long, notes: String) {
        events.update { list -> list.map { if (it.id == id) it.copy(notes = EventRules.cleanNotes(notes, config)) else it } }
    }

    override suspend fun setProgram(eventId: Long, pieceIds: List<Long>) {
        programs.update { it + (eventId to pieceIds.distinct()) }
    }

    override suspend fun removeFromProgram(eventId: Long, pieceId: Long) {
        programs.update { all -> all + (eventId to all[eventId].orEmpty().filter { it != pieceId }) }
    }

    override suspend fun saveKind(save: KindSave): KindRef? = when (save) {
        is KindSave.BuiltInColor -> {
            storedKinds.update { list -> list.filterNot { it is StoredKind.Recolor && it.kind == save.kind } + StoredKind.Recolor(save.kind, save.color) }
            KindRef.BuiltIn(save.kind)
        }
        is KindSave.NewOwn -> if (storedKinds.value.count { it is StoredKind.Own } >= config.maxCustomKinds) {
            null
        } else {
            val id = nextId++
            storedKinds.update { it + StoredKind.Own(id, EventRules.cleanKindName(save.name, config), save.color, save.sign, nowEpochMs) }
            KindRef.Custom(id)
        }
        is KindSave.EditOwn -> {
            storedKinds.update { list ->
                list.map { if (it is StoredKind.Own && it.id == save.id) it.copy(name = EventRules.cleanKindName(save.name, config), color = save.color, sign = save.sign) else it }
            }
            KindRef.Custom(save.id)
        }
    }

    override suspend fun deleteOwnKind(id: Long) {
        val gone = KindRef.Custom(id)
        events.update { list -> list.map { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it } }
        series.update { list -> list.map { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it } }
        storedKinds.update { list -> list.filterNot { it is StoredKind.Own && it.id == id } }
    }

    override suspend fun layAhead(today: LocalDate) {
        laidAhead += today
    }
}
