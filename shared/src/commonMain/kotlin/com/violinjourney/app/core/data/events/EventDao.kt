package com.violinjourney.app.core.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventPlan
import com.violinjourney.app.core.domain.events.EventStep
import com.violinjourney.app.core.domain.events.Recurrence
import com.violinjourney.app.core.domain.events.TemplateField
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The events of the calendar (spec 3.35, 6). Links without foreign keys — an event's repeat and kind, a recording's event
 * (plan D2) — are cleared here in the transaction that deletes what they point at, as `RepertoireDao.deletePiece` does
 * for takes; there is no UPSERT (SQLite 3.18 of API 26 does not have it): a row is found, then updated or inserted.
 */
@Dao
abstract class EventDao {
    @Query("SELECT * FROM calendar_events ORDER BY date, startMinutes, id")
    abstract fun observeEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM event_kinds ORDER BY id")
    abstract fun observeKinds(): Flow<List<EventKindEntity>>

    @Query("SELECT * FROM event_series ORDER BY id")
    abstract fun observeSeries(): Flow<List<EventSeriesEntity>>

    @Query("SELECT * FROM event_pieces ORDER BY eventId, position")
    abstract fun observePrograms(): Flow<List<EventPieceEntity>>

    /**
     * The events recordings belong to (plan D11), each with the name of its kind of one's own while that kind exists. Only
     * events that have recordings, and nothing of the files: an edit of the events and the horizon read this, not all the
     * recordings. Room follows the three tables — an event renamed renames its recordings at once (spec 3.36.9).
     */
    @Query(
        "SELECT e.id AS eventId, e.title AS title, e.date AS date, e.kind AS kind, e.kindId AS kindId, k.name AS ownName " +
            "FROM calendar_events e LEFT JOIN event_kinds k ON k.id = e.kindId AND k.builtIn IS NULL " +
            "WHERE e.id IN (SELECT eventId FROM sessions WHERE eventId IS NOT NULL)",
    )
    abstract fun observeRecordEvents(): Flow<List<RecordEventRow>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    abstract suspend fun event(id: Long): CalendarEventEntity?

    // ---- writing events and repeats

    @Insert
    protected abstract suspend fun insertEventRow(event: CalendarEventEntity): Long

    @Insert
    protected abstract suspend fun insertEventRows(events: List<CalendarEventEntity>)

    @Insert
    protected abstract suspend fun insertSeriesRow(series: EventSeriesEntity): Long

    @Query("SELECT * FROM event_series ORDER BY id")
    protected abstract suspend fun allSeries(): List<EventSeriesEntity>

    @Query("SELECT * FROM event_series WHERE id = :id")
    protected abstract suspend fun seriesRow(id: Long): EventSeriesEntity?

    @Query("SELECT COUNT(*) FROM calendar_events WHERE seriesId = :seriesId")
    protected abstract suspend fun eventsOfSeries(seriesId: Long): Int

    /** The moment a person created a repeat: that of its first event — every event it lays carries it (plan D49). */
    @Query("SELECT createdAtEpochMs FROM calendar_events WHERE seriesId = :seriesId ORDER BY id LIMIT 1")
    protected abstract suspend fun momentOfSeries(seriesId: Long): Long?

    @Query("UPDATE event_series SET laidUntil = :laidUntil WHERE id = :id")
    protected abstract suspend fun setLaidUntil(id: Long, laidUntil: String)

    @Query("UPDATE event_series SET untilDate = :until WHERE id = :id AND (untilDate IS NULL OR untilDate > :until)")
    protected abstract suspend fun endSeriesBy(id: Long, until: String)

    @Query("DELETE FROM event_series WHERE id = :id")
    protected abstract suspend fun deleteSeriesRow(id: Long)

    @Query(
        "UPDATE event_series SET kind = :kind, kindId = :kindId, startMinutes = :startMinutes, durationMinutes = :durationMinutes, " +
            "title = :title, place = :place WHERE id = :id",
    )
    protected abstract suspend fun setSeriesTemplate(id: Long, kind: String, kindId: Long?, startMinutes: Int?, durationMinutes: Int?, title: String, place: String)

    @Query(
        "UPDATE calendar_events SET kind = :kind, kindId = :kindId, date = :date, startMinutes = :startMinutes, " +
            "durationMinutes = :durationMinutes, title = :title, place = :place, notes = :notes, seriesId = :seriesId, " +
            "detached = :detached WHERE id = :id",
    )
    protected abstract suspend fun setEventRow(
        id: Long, kind: String, kindId: Long?, date: String, startMinutes: Int?, durationMinutes: Int?, title: String, place: String,
        notes: String, seriesId: Long?, detached: Boolean,
    )

    @Query("UPDATE calendar_events SET notes = :notes WHERE id = :id")
    abstract suspend fun setNotes(id: Long, notes: String)

    /**
     * «Этот и следующие» at the events of a repeat from [cut] on that were not changed by themselves, but [keepId]: a field
     * whose flag is set takes its new value, the others stay as each event has them (spec 3.35); an event left without a
     * start — «весь день» — keeps no length (spec 5.28). Every column is read as it was before the update.
     */
    @Query(
        "UPDATE calendar_events SET " +
            "kind = CASE WHEN :setKind THEN :kind ELSE kind END, " +
            "kindId = CASE WHEN :setKind THEN :kindId ELSE kindId END, " +
            "startMinutes = CASE WHEN :setStart THEN :startMinutes ELSE startMinutes END, " +
            "durationMinutes = CASE WHEN (CASE WHEN :setStart THEN :startMinutes ELSE startMinutes END) IS NULL THEN NULL " +
            "WHEN :setDuration THEN :durationMinutes ELSE durationMinutes END, " +
            "title = CASE WHEN :setTitle THEN :title ELSE title END, " +
            "place = CASE WHEN :setPlace THEN :place ELSE place END " +
            "WHERE seriesId = :seriesId AND date >= :cut AND detached = 0 AND id != :keepId",
    )
    protected abstract suspend fun setFollowingFields(
        seriesId: Long,
        cut: String,
        keepId: Long,
        setKind: Boolean,
        kind: String,
        kindId: Long?,
        setStart: Boolean,
        startMinutes: Int?,
        setDuration: Boolean,
        durationMinutes: Int?,
        setTitle: Boolean,
        title: String,
        setPlace: Boolean,
        place: String,
    )

    @Query("DELETE FROM calendar_events WHERE seriesId = :seriesId AND date >= :cut AND detached = 0 AND id != :keepId")
    protected abstract suspend fun deleteFollowing(seriesId: Long, cut: String, keepId: Long)

    /** Events changed by themselves stay when their repeat is cut — as single events (spec 5.28). */
    @Query("UPDATE calendar_events SET seriesId = NULL, detached = 0 WHERE seriesId = :seriesId AND date >= :cut AND detached = 1 AND id != :keepId")
    protected abstract suspend fun loosenFollowing(seriesId: Long, cut: String, keepId: Long)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    protected abstract suspend fun deleteEventRow(id: Long)

    // ---- recordings of events that go: no foreign key from sessions (see MIGRATION_13_14)

    /** The name a recording wore while it had none of its own (plan D12): it keeps it once its event is gone. */
    @Query("UPDATE sessions SET title = :name WHERE eventId = :eventId AND title IS NULL")
    protected abstract suspend fun freezeTitle(eventId: Long, name: String)

    @Query("UPDATE sessions SET eventId = NULL WHERE eventId = :eventId")
    protected abstract suspend fun unlinkOne(eventId: Long)

    @Query(
        "UPDATE sessions SET eventId = NULL WHERE eventId IN " +
            "(SELECT id FROM calendar_events WHERE seriesId = :seriesId AND date >= :cut AND detached = 0 AND id != :keepId)",
    )
    protected abstract suspend fun unlinkFollowing(seriesId: Long, cut: String, keepId: Long)

    /**
     * A new event — the first of [series] when there is one — with its program in order; programs name only elements of
     * the repertoire that are still there.
     */
    @Transaction
    open suspend fun insert(event: CalendarEventEntity, series: EventSeriesEntity?, pieceIds: List<Long>): Long {
        val seriesId = series?.let { insertSeriesRow(it) }
        val id = insertEventRow(event.copy(seriesId = seriesId))
        pieceIds.forEachIndexed { position, pieceId -> addToProgram(id, pieceId, position) }
        return id
    }

    /**
     * Lays every repeat ahead (spec 5.28, plan D3): the dates after its `laidUntil` up to today and [horizonWeeks], each an
     * event of its template with the moment of the repeat (plan D49), and the new `laidUntil` — in one transaction for all
     * the repeats, so that a second call, even one running at the same time, finds the first one's bound and lays nothing.
     */
    @Transaction
    open suspend fun layAhead(today: LocalDate, horizonWeeks: Int) = lay(today, horizonWeeks, inherited = emptyMap())

    /**
     * [layAhead] within the caller's transaction. The moment of a repeat is that of its first event; [inherited] — repeat →
     * the moment it has from the repeat it was split from (plan D49), for one that has no event of its own yet: a repeat
     * split off at an event that is over has none until it is laid.
     */
    private suspend fun lay(today: LocalDate, horizonWeeks: Int, inherited: Map<Long, Long>) {
        for (row in allSeries()) {
            val laying = Recurrence.toLay(EventMapper.toSeries(row), today, horizonWeeks)
            if (laying.laidUntil.toString() == row.laidUntil) continue
            if (laying.dates.isNotEmpty()) {
                val moment = momentOfSeries(row.id) ?: inherited[row.id] ?: NO_MOMENT
                insertEventRows(laying.dates.map { EventMapper.laid(row, it, moment) })
            }
            setLaidUntil(row.id, laying.laidUntil.toString())
        }
    }

    /**
     * Carries out [plan] (spec 3.35, plan D4, D12, D50) and lays the horizon from [today] after it, in one transaction.
     * Before any event goes, the recordings that wore its name by default get it written in ([frozenTitles] — event →
     * name, reckoned by the screen in the language of the interface; a recording with a name of its own keeps it), then
     * they lose the event, by the same rule that deletes it — so a recording of an event laid meanwhile, or of the selected
     * one deleted whatever its date, is not left pointing at nothing. A repeat split off is laid with the moment of the
     * repeat it came from (plan D49), whether the selected event moved into it or not.
     */
    @Transaction
    open suspend fun apply(plan: EventPlan, frozenTitles: Map<Long, String>, today: LocalDate, horizonWeeks: Int) {
        frozenTitles.forEach { (eventId, name) -> freezeTitle(eventId, name) }
        val inherited = mutableMapOf<Long, Long>()
        for (step in plan.steps) {
            when (step) {
                is EventStep.UpdateOne -> {
                    val row = event(step.id) ?: continue
                    write(step.id, step.fields, row.seriesId, detached = row.detached || step.detach)
                }
                is EventStep.UpdateNotes -> setNotes(step.id, step.notes)
                is EventStep.UpdateFollowing -> updateFollowing(step)
                is EventStep.Split -> inherited += split(step)
                is EventStep.StartSeries -> {
                    if (event(step.eventId) == null) continue
                    val seriesId = insertSeriesRow(EventMapper.toEntity(step.series.copy(id = 0)))
                    write(step.eventId, step.fields, seriesId, detached = false)
                }
                is EventStep.DeleteOne -> {
                    val row = event(step.id) ?: continue
                    unlinkOne(step.id)
                    deleteEventRow(step.id)
                    row.seriesId?.let { dropIfSpent(it) }
                }
                is EventStep.DeleteFollowing -> {
                    val cut = step.cut.toString()
                    unlinkFollowing(step.seriesId, cut, keepId = NO_ID)
                    unlinkOne(step.selectedId)
                    deleteFollowing(step.seriesId, cut, keepId = NO_ID)
                    deleteEventRow(step.selectedId)
                    loosenFollowing(step.seriesId, cut, keepId = NO_ID)
                    endSeriesBy(step.seriesId, Recurrence.dayBefore(step.cut).toString())
                    dropIfSpent(step.seriesId)
                }
            }
        }
        lay(today, horizonWeeks, inherited)
    }

    private suspend fun updateFollowing(step: EventStep.UpdateFollowing) {
        val fields = step.fields
        val values = step.edited
        val (kind, kindId) = EventMapper.columnsOf(values.kind)
        setFollowingFields(
            step.seriesId, step.cut.toString(), keepId = step.editedId ?: NO_ID,
            setKind = TemplateField.KIND in fields, kind = kind, kindId = kindId,
            setStart = TemplateField.START in fields, startMinutes = values.startMinutes,
            setDuration = TemplateField.DURATION in fields, durationMinutes = values.durationMinutes,
            setTitle = TemplateField.TITLE in fields, title = values.title,
            setPlace = TemplateField.PLACE in fields, place = values.place,
        )
        step.editedId?.let { id -> event(id)?.let { row -> write(id, step.edited, row.seriesId, row.detached) } }
        val template = step.template
        val (templateKind, templateKindId) = EventMapper.columnsOf(template.kind)
        setSeriesTemplate(step.seriesId, templateKind, templateKindId, template.startMinutes, template.durationMinutes, template.title, template.place)
    }

    /** Carries out [step]; the repeat it starts → the moment of the one it ends (plan D49), for [lay]. */
    private suspend fun split(step: EventStep.Split): Map<Long, Long> {
        val moment = momentOfSeries(step.oldSeriesId) ?: NO_MOMENT
        val cut = step.cut.toString()
        val keepId = step.editedId ?: NO_ID
        unlinkFollowing(step.oldSeriesId, cut, keepId)
        deleteFollowing(step.oldSeriesId, cut, keepId)
        loosenFollowing(step.oldSeriesId, cut, keepId)
        endSeriesBy(step.oldSeriesId, Recurrence.dayBefore(step.cut).toString())
        val newSeriesId = step.newSeries?.let { insertSeriesRow(EventMapper.toEntity(it.copy(id = 0))) }
        step.editedId?.let { id -> if (event(id) != null) write(id, step.edited, newSeriesId, detached = false) }
        dropIfSpent(step.oldSeriesId)
        newSeriesId?.let { dropIfSpent(it) }
        return newSeriesId?.let { mapOf(it to moment) }.orEmpty()
    }

    private suspend fun write(id: Long, fields: EventDraft, seriesId: Long?, detached: Boolean) {
        val (kind, kindId) = EventMapper.columnsOf(fields.kind)
        setEventRow(
            id, kind, kindId, fields.date.toString(), fields.startMinutes, fields.durationMinutes, fields.title, fields.place,
            fields.notes, seriesId, detached,
        )
    }

    /** A repeat with no events left and no dates to lay goes (plan 5.2): nothing would ever show it again. */
    private suspend fun dropIfSpent(seriesId: Long) {
        val row = seriesRow(seriesId) ?: return
        if (eventsOfSeries(seriesId) == 0 && !Recurrence.hasDatesAhead(EventMapper.toSeries(row))) deleteSeriesRow(seriesId)
    }

    // ---- programs

    /** One element of a program, if both the event and the element are still there (a foreign key would refuse it). */
    @Query(
        "INSERT OR IGNORE INTO event_pieces (eventId, pieceId, position) " +
            "SELECT e.id, p.id, :position FROM calendar_events e, pieces p WHERE e.id = :eventId AND p.id = :pieceId",
    )
    protected abstract suspend fun addToProgram(eventId: Long, pieceId: Long, position: Int)

    @Query("DELETE FROM event_pieces WHERE eventId = :eventId")
    protected abstract suspend fun clearProgram(eventId: Long)

    /** The program anew, in this order (spec 3.35: the order of adding). */
    @Transaction
    open suspend fun setProgram(eventId: Long, pieceIds: List<Long>) {
        clearProgram(eventId)
        pieceIds.distinct().forEachIndexed { position, pieceId -> addToProgram(eventId, pieceId, position) }
    }

    @Query("DELETE FROM event_pieces WHERE eventId = :eventId AND pieceId = :pieceId")
    abstract suspend fun removeFromProgram(eventId: Long, pieceId: Long)

    // ---- kinds

    @Insert
    protected abstract suspend fun insertKindRow(kind: EventKindEntity): Long

    @Query("SELECT * FROM event_kinds WHERE builtIn = :builtIn")
    protected abstract suspend fun builtInRow(builtIn: String): EventKindEntity?

    @Query("UPDATE event_kinds SET color = :color WHERE id = :id")
    protected abstract suspend fun setColor(id: Long, color: Int)

    @Query("SELECT COUNT(*) FROM event_kinds WHERE builtIn IS NULL")
    protected abstract suspend fun ownKindsCount(): Int

    /** The colour of a built-in kind: its row is found and changed, or made (plan D2: no UPSERT on API 26). */
    @Transaction
    open suspend fun saveBuiltInColor(builtIn: String, color: Int, nowEpochMs: Long) {
        val row = builtInRow(builtIn)
        if (row != null) {
            setColor(row.id, color)
        } else {
            insertKindRow(EventKindEntity(builtIn = builtIn, name = "", color = color, sign = null, createdAtEpochMs = nowEpochMs))
        }
    }

    /** A new kind of one's own, unless there are [max] already (spec 5.28): null then. */
    @Transaction
    open suspend fun insertOwnKind(kind: EventKindEntity, max: Int): Long? = if (ownKindsCount() >= max) null else insertKindRow(kind)

    @Query("UPDATE event_kinds SET name = :name, color = :color, sign = :sign WHERE id = :id AND builtIn IS NULL")
    abstract suspend fun updateOwnKind(id: Long, name: String, color: Int, sign: String)

    @Query("UPDATE calendar_events SET kind = :other, kindId = NULL WHERE kindId = :id")
    protected abstract suspend fun eventsBecome(id: Long, other: String)

    @Query("UPDATE event_series SET kind = :other, kindId = NULL WHERE kindId = :id")
    protected abstract suspend fun seriesBecome(id: Long, other: String)

    @Query("DELETE FROM event_kinds WHERE id = :id AND builtIn IS NULL")
    protected abstract suspend fun deleteOwnKindRow(id: Long)

    /** A kind of one's own goes; its events and the templates of its repeats become «Другое» (spec 3.35, 3.36.9). */
    @Transaction
    open suspend fun deleteOwnKind(id: Long) {
        eventsBecome(id, BuiltInKind.OTHER.name)
        seriesBecome(id, BuiltInKind.OTHER.name)
        deleteOwnKindRow(id)
    }

    private companion object {
        /** No event: ids of `calendar_events` begin at 1. */
        const val NO_ID = 0L

        /** The moment of a repeat with no events left (plan D49): its events never outrank one a person created. */
        const val NO_MOMENT = 0L
    }
}
