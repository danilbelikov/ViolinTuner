package com.violinjourney.app.core.domain.events

import kotlinx.datetime.LocalDate

/**
 * The four kinds every calendar has (spec 3.35): their names are words of the interface, so they are not rows — only a
 * colour given to one of them is ([StoredKind.Recolor]). An event keeps its kind by this name; an event of a kind of the
 * player's own keeps [OTHER] beside the id of that kind, so that it is «Другое» once that kind is gone (spec 6).
 */
enum class BuiltInKind { LESSON, REHEARSAL, PERFORMANCE, OTHER }

/** What an event is: one of the four kinds, or a kind of the player's own while it exists. */
sealed interface KindRef {
    data class BuiltIn(val kind: BuiltInKind) : KindRef

    data class Custom(val id: Long) : KindRef

    companion object {
        /** What an event of a kind that is gone, or not known to this build, reads as. */
        val OTHER: KindRef = BuiltIn(BuiltInKind.OTHER)
    }
}

/**
 * The form of a kind: its colour is always doubled by it (principle 5). The signs of the four built-in kinds are theirs
 * alone and are never stored; the twelve a kind of one's own may take are stored by [key] (spec 3.36.9, 5.29 R9).
 */
enum class KindSign(val key: String?) {
    LESSON(null), REHEARSAL(null), PERFORMANCE(null), OTHER(null),
    BOOK("book"), HAT("hat"), KEYS("keys"), MASK("mask"), TICKET("ticket"), CHAT("chat"),
    HEART("heart"), MOON("moon"), LEAF("leaf"), BOLT("bolt"), BOWTIE("bowtie"), ARC("arc");

    companion object {
        /** The signs of the kinds of one's own, in the order the sheet «Вид» shows them. */
        val OWN: List<KindSign> = entries.filter { it.key != null }

        /** The sign of a stored key; null for a key this build does not know. */
        fun of(key: String?): KindSign? = key?.let { stored -> OWN.firstOrNull { it.key == stored } }

        /** The sign of a built-in kind. */
        fun of(kind: BuiltInKind): KindSign = when (kind) {
            BuiltInKind.LESSON -> LESSON
            BuiltInKind.REHEARSAL -> REHEARSAL
            BuiltInKind.PERFORMANCE -> PERFORMANCE
            BuiltInKind.OTHER -> OTHER
        }
    }
}

/** How a kind looks: its sign and its colour — a number of the set of eight (spec 5.28, 5.29 R9), not a colour itself. */
data class KindLook(val sign: KindSign, val color: Int)

/**
 * A kind as the screens know it: a built-in one with the colour it was given, or one of the player's own. [ownName] —
 * the name of a kind of one's own as it was written; null for a built-in one, whose name is a word of the interface.
 */
data class EventKind(val ref: KindRef, val look: KindLook, val ownName: String?, val createdAtEpochMs: Long)

/** A row of `event_kinds`: the colour a built-in kind was given, or a kind of the player's own (spec 6). */
sealed interface StoredKind {
    data class Recolor(val kind: BuiltInKind, val color: Int) : StoredKind

    data class Own(val id: Long, val name: String, val color: Int, val sign: KindSign, val createdAtEpochMs: Long) : StoredKind
}

/**
 * How an event repeats (spec 3.35): every week or every other one, counted from the date of its first event (spec 5.28).
 * The step is what the word means, not a number to tune, so it lives here; a repeat that is stored is never [NONE].
 */
enum class Repeat(val stepDays: Int?) {
    NONE(null),
    WEEKLY(DAYS_PER_WEEK),
    BIWEEKLY(2 * DAYS_PER_WEEK),
}

/** Days in a week: the step of a weekly repeat and the unit of the horizon (spec 5.28). */
const val DAYS_PER_WEEK = 7

/**
 * One event of the calendar (spec 3.35): local date and time without a zone — a lesson at 17:00 stays at 17:00 after a
 * flight. An event belongs to the day it starts on, even when it ends after midnight (spec 5.28).
 */
data class CalendarEvent(
    val id: Long,
    val kind: KindRef,
    val date: LocalDate,
    /** Minutes from midnight; null — «весь день». */
    val startMinutes: Int?,
    /** Null — no length was given; always null for «весь день». */
    val durationMinutes: Int?,
    /** Empty — the name of the kind stands for it (spec 3.35). */
    val title: String,
    /** The teacher of a lesson, the place of anything else; empty when not given. */
    val place: String,
    val notes: String,
    /** The repeat it was laid by, or null for a single event. */
    val seriesId: Long?,
    /** Changed by itself («Только этот»): edits of its repeat leave it alone (spec 3.35). */
    val detached: Boolean,
    /**
     * When a person created it — or created its repeat: an event laid ahead by the horizon carries the moment of its
     * repeat, and one of a repeat split off with «Этот и следующие» the moment of the repeat it was split from, whether
     * the selected event moved into the new one or not; 0 when the repeat has no events left (plan D49). So «the kind of
     * the last event created» stays a person's choice, and not the lesson the horizon laid this morning.
     */
    val createdAtEpochMs: Long,
)

/**
 * A repeat (spec 3.35): its events are rows of their own, laid ahead up to today + 12 weeks ([laidUntil] — every date of
 * the progression up to it has had its chance, and is never laid again: one deleted «only this» stays deleted). The
 * fields from [startMinutes] on are the template of what is laid next.
 */
data class EventSeries(
    val id: Long,
    val kind: KindRef,
    val repeat: Repeat,
    val firstDate: LocalDate,
    /** The last day it may fall on, inclusive (spec 5.28); null — no end. */
    val until: LocalDate?,
    val laidUntil: LocalDate,
    val startMinutes: Int?,
    val durationMinutes: Int?,
    val title: String,
    val place: String,
)

/** What a form holds and what an edit writes: raw text as typed — [EventRules.clean] makes it storable. */
data class EventDraft(
    val kind: KindRef = KindRef.BuiltIn(BuiltInKind.LESSON),
    val date: LocalDate,
    val startMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val title: String = "",
    val place: String = "",
    val notes: String = "",
)

/** The fields of an event a person edits — everything but who laid it and when. */
fun CalendarEvent.draft(): EventDraft = EventDraft(kind, date, startMinutes, durationMinutes, title, place, notes)

/** What a repeat lays: the fields of its events that «Этот и следующие» changes together (spec 3.35). */
data class SeriesTemplate(
    val kind: KindRef,
    val startMinutes: Int?,
    val durationMinutes: Int?,
    val title: String,
    val place: String,
)

fun EventDraft.template(): SeriesTemplate = SeriesTemplate(kind, startMinutes, durationMinutes, title, place)

fun EventSeries.template(): SeriesTemplate = SeriesTemplate(kind, startMinutes, durationMinutes, title, place)

/** A field of the template: what «Этот и следующие» changes at the events of a repeat when an edit changed it (spec 3.35). */
enum class TemplateField { KIND, START, DURATION, TITLE, PLACE }

/** The fields in which [this] differs from [other]. */
fun SeriesTemplate.fieldsChangedFrom(other: SeriesTemplate): Set<TemplateField> = buildSet {
    if (kind != other.kind) add(TemplateField.KIND)
    if (startMinutes != other.startMinutes) add(TemplateField.START)
    if (durationMinutes != other.durationMinutes) add(TemplateField.DURATION)
    if (title != other.title) add(TemplateField.TITLE)
    if (place != other.place) add(TemplateField.PLACE)
}

/**
 * [this] with [fields] taken [from] another template and the rest its own — what «Этот и следующие» makes of the template
 * of a repeat (spec 3.35: what the edit changed changes, nothing else). «Весь день» has no length (spec 5.28): a template
 * left without a start keeps none.
 */
fun SeriesTemplate.taking(fields: Set<TemplateField>, from: SeriesTemplate): SeriesTemplate {
    val start = if (TemplateField.START in fields) from.startMinutes else startMinutes
    return SeriesTemplate(
        kind = if (TemplateField.KIND in fields) from.kind else kind,
        startMinutes = start,
        durationMinutes = when {
            start == null -> null
            TemplateField.DURATION in fields -> from.durationMinutes
            else -> durationMinutes
        },
        title = if (TemplateField.TITLE in fields) from.title else title,
        place = if (TemplateField.PLACE in fields) from.place else place,
    )
}

/**
 * What is written in place of an empty title (spec 3.35): the title, or the name of the kind — a word of the interface
 * for a built-in one, the name as written for one of the player's own. The words themselves are the screens'.
 */
sealed interface EventName {
    data class Titled(val title: String) : EventName

    data class OfKind(val kind: KindRef, val ownName: String?) : EventName

    companion object {
        /** The name of an event: its title, or its kind — a kind of one's own that is gone reads as «Другое». */
        fun of(title: String, kind: KindRef, kinds: List<EventKind>): EventName {
            if (title.isNotBlank()) return Titled(title)
            val resolved = KindRules.resolve(kind, kinds)
            return OfKind(resolved, kinds.firstOrNull { it.ref == resolved }?.ownName)
        }
    }
}

/**
 * The event of a recording (plan D11, the separate query `EventDao.observeRecordEvents`): what its default name and the
 * word of its kind are made of. [kind] is resolved already — a kind of one's own that is gone is «Другое».
 */
data class SessionEvent(val id: Long, val title: String, val date: LocalDate, val kind: KindRef, val ownName: String?) {
    val name: EventName get() = if (title.isNotBlank()) EventName.Titled(title) else EventName.OfKind(kind, ownName)
}

/** What the sheet «Вид» saves (spec 3.36.9): the colour of a built-in kind, a new kind of one's own, or an edit of one. */
sealed interface KindSave {
    data class BuiltInColor(val kind: BuiltInKind, val color: Int) : KindSave

    data class NewOwn(val name: String, val color: Int, val sign: KindSign) : KindSave

    data class EditOwn(val id: Long, val name: String, val color: Int, val sign: KindSign) : KindSave
}
