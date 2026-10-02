package com.violinjourney.app.core.data.events

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.violinjourney.app.core.data.repertoire.PieceEntity

/**
 * A kind of the player's own, or the colour a built-in kind was given (spec 6 of `events.md`): [builtIn] — the name of the
 * built-in kind, unique, null for one's own. The built-in kinds that keep their colours are not rows, as the built-in
 * sections of the repertoire are not (spec 3.22). [color] — a number of the set of eight; [sign] — the key of one of the
 * twelve signs of one's own, null for a built-in kind; [name] — empty for a built-in kind.
 */
@Entity(tableName = "event_kinds", indices = [Index(value = ["builtIn"], unique = true)])
data class EventKindEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtIn: String?,
    val name: String,
    val color: Int,
    val sign: String?,
    val createdAtEpochMs: Long,
)

/**
 * A repeat: its step in days (7 or 14), its first date, «до» (inclusive, null — no end) and how far it has been laid
 * ahead; the rest is the template of the events it lays. Dates are local ISO text, as the day of a practice. The kind as
 * an event keeps it; no foreign key — `EventDao.deleteOwnKind` makes the repeats of a kind that goes «Другое».
 */
@Entity(tableName = "event_series", indices = [Index("kindId")])
data class EventSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val kindId: Long?,
    val stepDays: Int,
    val firstDate: String,
    val untilDate: String?,
    val laidUntil: String,
    val startMinutes: Int?,
    val durationMinutes: Int?,
    val title: String,
    val place: String,
)

/**
 * One event (spec 3.35): [kind] — the name of a built-in kind, «OTHER» for one of one's own beside its [kindId]; [date] —
 * local ISO text; [startMinutes] — null for «весь день». [seriesId] and [kindId] have no foreign keys (plan D2): the
 * DAO clears and changes them in the transactions that delete what they point at.
 */
@Entity(tableName = "calendar_events", indices = [Index("date"), Index("seriesId"), Index("kindId")])
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val kindId: Long?,
    val date: String,
    val startMinutes: Int?,
    val durationMinutes: Int?,
    val title: String,
    val place: String,
    val notes: String,
    val seriesId: Long?,
    val detached: Boolean,
    val createdAtEpochMs: Long,
)

/** An element of the repertoire in the program of an event, at [position]; goes with either of them (spec 6). */
@Entity(
    tableName = "event_pieces",
    primaryKeys = ["eventId", "pieceId"],
    indices = [Index("pieceId")],
    foreignKeys = [
        ForeignKey(entity = CalendarEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PieceEntity::class, parentColumns = ["id"], childColumns = ["pieceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EventPieceEntity(val eventId: Long, val pieceId: Long, val position: Int)

/** The event of a recording, with the name of its kind of one's own while that kind exists (`EventDao.observeRecordEvents`). */
data class RecordEventRow(
    val eventId: Long,
    val title: String,
    val date: String,
    val kind: String,
    val kindId: Long?,
    val ownName: String?,
)
