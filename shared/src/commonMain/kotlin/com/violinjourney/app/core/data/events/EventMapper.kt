package com.violinjourney.app.core.data.events

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.DAYS_PER_WEEK
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.events.StoredKind
import kotlinx.datetime.LocalDate

/**
 * Entity ↔ domain. What a newer build may have written reads as something this one knows, not as a crash (as
 * `SessionMapper.decodeZones` forgives): a kind it does not know is «Другое», a sign — the book, a step — a week; a colour
 * out of the set is put right by `KindRules.all`.
 */
internal object EventMapper {
    /** A kind of one's own while its id is there; the built-in kind of the name otherwise — «Другое» for a name not known. */
    fun kindOf(kind: String, kindId: Long?): KindRef =
        if (kindId != null) KindRef.Custom(kindId) else KindRef.BuiltIn(builtInOf(kind) ?: BuiltInKind.OTHER)

    /** The two columns a kind is kept in: the name of a built-in one, or «OTHER» beside the id of one's own (plan D1). */
    fun columnsOf(kind: KindRef): Pair<String, Long?> = when (kind) {
        is KindRef.BuiltIn -> kind.kind.name to null
        is KindRef.Custom -> BuiltInKind.OTHER.name to kind.id
    }

    fun builtInOf(name: String?): BuiltInKind? = BuiltInKind.entries.firstOrNull { it.name == name }

    fun repeatOf(stepDays: Int): Repeat = Repeat.entries.firstOrNull { it.stepDays == stepDays } ?: Repeat.WEEKLY

    fun toEvent(entity: CalendarEventEntity) = CalendarEvent(
        id = entity.id,
        kind = kindOf(entity.kind, entity.kindId),
        date = LocalDate.parse(entity.date),
        startMinutes = entity.startMinutes,
        durationMinutes = entity.durationMinutes,
        title = entity.title,
        place = entity.place,
        notes = entity.notes,
        seriesId = entity.seriesId,
        detached = entity.detached,
        createdAtEpochMs = entity.createdAtEpochMs,
    )

    /** A new row of [draft]: no id yet. */
    fun toEntity(draft: EventDraft, seriesId: Long?, createdAtEpochMs: Long): CalendarEventEntity {
        val (kind, kindId) = columnsOf(draft.kind)
        return CalendarEventEntity(
            kind = kind, kindId = kindId, date = draft.date.toString(), startMinutes = draft.startMinutes,
            durationMinutes = draft.durationMinutes, title = draft.title, place = draft.place, notes = draft.notes,
            seriesId = seriesId, detached = false, createdAtEpochMs = createdAtEpochMs,
        )
    }

    fun toSeries(entity: EventSeriesEntity) = EventSeries(
        id = entity.id,
        kind = kindOf(entity.kind, entity.kindId),
        repeat = repeatOf(entity.stepDays),
        firstDate = LocalDate.parse(entity.firstDate),
        until = entity.untilDate?.let(LocalDate::parse),
        laidUntil = LocalDate.parse(entity.laidUntil),
        startMinutes = entity.startMinutes,
        durationMinutes = entity.durationMinutes,
        title = entity.title,
        place = entity.place,
    )

    /** A new row of [series]; a repeat that is not one is kept as a week — the domain never stores it so. */
    fun toEntity(series: EventSeries): EventSeriesEntity {
        val (kind, kindId) = columnsOf(series.kind)
        return EventSeriesEntity(
            id = series.id, kind = kind, kindId = kindId, stepDays = series.repeat.stepDays ?: DAYS_PER_WEEK,
            firstDate = series.firstDate.toString(), untilDate = series.until?.toString(), laidUntil = series.laidUntil.toString(),
            startMinutes = series.startMinutes, durationMinutes = series.durationMinutes, title = series.title, place = series.place,
        )
    }

    /** An event a repeat lays on [date]: its template, no notes, the moment of the repeat (plan D49). */
    fun laid(series: EventSeriesEntity, date: LocalDate, createdAtEpochMs: Long) = CalendarEventEntity(
        kind = series.kind, kindId = series.kindId, date = date.toString(), startMinutes = series.startMinutes,
        durationMinutes = series.durationMinutes, title = series.title, place = series.place, notes = "", seriesId = series.id,
        detached = false, createdAtEpochMs = createdAtEpochMs,
    )

    /** Null for a row of a built-in kind this build does not know: its colour is nobody's. */
    fun toStoredKind(entity: EventKindEntity): StoredKind? {
        if (entity.builtIn != null) return builtInOf(entity.builtIn)?.let { StoredKind.Recolor(it, entity.color) }
        return StoredKind.Own(entity.id, entity.name, entity.color, KindSign.of(entity.sign) ?: KindSign.BOOK, entity.createdAtEpochMs)
    }

    /** The event of a recording: a kind of one's own that is gone — the join found no name — is «Другое». */
    fun recordEventOf(row: RecordEventRow): SessionEvent {
        val kind = if (row.kindId != null && row.ownName != null) KindRef.Custom(row.kindId) else KindRef.BuiltIn(builtInOf(row.kind) ?: BuiltInKind.OTHER)
        return SessionEvent(row.eventId, row.title, LocalDate.parse(row.date), kind, ownName = row.ownName.takeIf { kind is KindRef.Custom })
    }
}
