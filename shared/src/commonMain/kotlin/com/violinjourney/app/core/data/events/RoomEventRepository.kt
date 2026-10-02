package com.violinjourney.app.core.data.events

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.EventAdded
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventPlan
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventRules
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSave
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The events on Room (spec 3.35, 6). The rules are the domain's — [EventRules], [KindRules], the plans of `SeriesEdits`;
 * here they are stored. Rows are turned into the domain off the main thread ([io]): a calendar holds a year of weekly
 * lessons and more.
 */
class RoomEventRepository(
    private val dao: EventDao,
    private val config: EventsConfig,
    private val clock: WallClock,
    private val analytics: Analytics,
    private val io: CoroutineDispatcher,
) : EventRepository {
    override val events: Flow<List<CalendarEvent>> = dao.observeEvents().map { rows -> rows.map(EventMapper::toEvent) }.flowOn(io)

    override val kinds: Flow<List<EventKind>> =
        dao.observeKinds().map { rows -> KindRules.all(rows.mapNotNull(EventMapper::toStoredKind), config) }.flowOn(io)

    override val series: Flow<List<EventSeries>> = dao.observeSeries().map { rows -> rows.map(EventMapper::toSeries) }.flowOn(io)

    override val programs: Flow<Map<Long, List<Long>>> =
        dao.observePrograms().map { rows -> rows.groupBy({ it.eventId }, { it.pieceId }) }.flowOn(io)

    override val recordEvents: Flow<Map<Long, SessionEvent>> =
        dao.observeRecordEvents().map { rows -> rows.associate { it.eventId to EventMapper.recordEventOf(it) } }.flowOn(io)

    override suspend fun event(id: Long): CalendarEvent? = dao.event(id)?.let(EventMapper::toEvent)

    override suspend fun add(draft: EventDraft, repeat: Repeat, until: LocalDate?, today: LocalDate): Long {
        val clean = EventRules.clean(draft, config)
        val now = clock.millis()
        // a repeat that ends before it begins is one event
        val series = if (repeat == Repeat.NONE || (until != null && until < clean.date)) {
            null
        } else {
            EventSeries(
                id = 0, kind = clean.kind, repeat = repeat, firstDate = clean.date, until = until, laidUntil = clean.date,
                startMinutes = clean.startMinutes, durationMinutes = clean.durationMinutes, title = clean.title, place = clean.place,
            )
        }
        val id = dao.insert(EventMapper.toEntity(clean, seriesId = null, createdAtEpochMs = now), series?.let(EventMapper::toEntity), pieceIds = emptyList())
        analytics.track(EventAdded(clean.kind, series?.repeat ?: Repeat.NONE))
        if (series != null) layAhead(today)
        return id
    }

    override suspend fun apply(plan: EventPlan, frozenTitles: Map<Long, String>, today: LocalDate) =
        dao.apply(plan, frozenTitles, today, config.seriesHorizonWeeks)

    override suspend fun setNotes(id: Long, notes: String) = dao.setNotes(id, EventRules.cleanNotes(notes, config))

    override suspend fun setProgram(eventId: Long, pieceIds: List<Long>) = dao.setProgram(eventId, pieceIds)

    override suspend fun removeFromProgram(eventId: Long, pieceId: Long) = dao.removeFromProgram(eventId, pieceId)

    override suspend fun saveKind(save: KindSave): KindRef? = when (save) {
        is KindSave.BuiltInColor -> {
            dao.saveBuiltInColor(save.kind.name, colorOf(save.color), clock.millis())
            KindRef.BuiltIn(save.kind)
        }
        is KindSave.NewOwn -> {
            val row = EventKindEntity(
                builtIn = null, name = nameOf(save.name), color = colorOf(save.color), sign = keyOf(save.sign), createdAtEpochMs = clock.millis(),
            )
            dao.insertOwnKind(row, config.maxCustomKinds)?.let { KindRef.Custom(it) }
        }
        is KindSave.EditOwn -> {
            dao.updateOwnKind(save.id, nameOf(save.name), colorOf(save.color), keyOf(save.sign))
            KindRef.Custom(save.id)
        }
    }

    override suspend fun deleteOwnKind(id: Long) = dao.deleteOwnKind(id)

    override suspend fun layAhead(today: LocalDate) = dao.layAhead(today, config.seriesHorizonWeeks)

    private fun colorOf(color: Int): Int = color.coerceIn(0, config.colorCount - 1)

    // the sheet «Вид» saves nothing it does not allow (spec 3.36.9): a name, one of the twelve signs of one's own
    private fun nameOf(name: String): String =
        requireNotNull(EventRules.cleanKindName(name, config).takeIf { it.isNotEmpty() }) { "a kind needs a name" }

    private fun keyOf(sign: KindSign): String = requireNotNull(sign.key) { "a kind of one's own takes a sign of its own" }
}
