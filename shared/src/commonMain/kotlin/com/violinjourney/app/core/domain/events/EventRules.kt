package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.text.takeCodePoints
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/** The start and the length an event is given by default (spec 5.28): null start — «весь день», null length — none. */
data class EventTime(val startMinutes: Int?, val durationMinutes: Int?)

/** What a new event starts with (spec 3.35, 5.28): the kind of the last event a person created, and its time. */
data class EventDefaults(val kind: KindRef, val time: EventTime)

/**
 * The rules of one event (spec 3.35, 5.28): what is stored, when it starts and ends, when it has passed, and what a new
 * one is given. Dates and times are local and have no zone; a moment is reckoned from them in the zone the phone is in
 * now — a lesson at 17:00 stays at 17:00 after a flight. A time is the first moment the clock shows it or a later time
 * ([momentOf]): in the overlap of the change back to winter time the earlier of the two — an event that has ended does
 * not come back when the clock goes back an hour; in the gap of the change to summer time the clock jumps over a time,
 * and it comes with the jump — the first moment after the gap, as the start of a day does (`atStartOfDayIn`). So an
 * event never starts after it ends: one the gap swallows whole starts and ends with the jump.
 */
object EventRules {
    /** Minutes in a day: the start of an event is a minute of its day. */
    const val MINUTES_PER_DAY = 24 * 60

    private const val MINUTES_PER_HOUR = 60

    /**
     * What is stored of what was typed: spaces at the edges cut, lengths kept as a person counts them (an emoji at the edge
     * is left out whole); notes keep their inner line breaks. The start falls on its step, the length on its steps and
     * within its limits; «весь день» has no length (spec 5.28).
     */
    fun clean(draft: EventDraft, config: EventsConfig): EventDraft {
        val start = draft.startMinutes?.let { snapStart(it, config) }
        return draft.copy(
            startMinutes = start,
            durationMinutes = if (start == null) null else draft.durationMinutes?.let { snapDuration(it, config) },
            title = cut(draft.title, config.maxTitleLength),
            place = cut(draft.place, config.maxPlaceLength),
            notes = cleanNotes(draft.notes, config),
        )
    }

    /** Notes as they are stored: spaces at the edges cut, inner line breaks kept — a teacher's pencil marks (spec 3.15). */
    fun cleanNotes(notes: String, config: EventsConfig): String = notes.trim().takeCodePoints(config.maxNotesLength).trimEnd()

    /** The name of a kind of one's own as it is stored: spaces at the edges cut, at most 24 characters (spec 5.28). */
    fun cleanKindName(name: String, config: EventsConfig): String = cut(name, config.maxKindNameLength)

    /** A start on the step of 5 minutes, the nearest one, within the day. */
    fun snapStart(minutes: Int, config: EventsConfig): Int {
        val step = config.startStepMinutes
        return ((minutes + step / 2).floorDiv(step) * step).coerceIn(0, MINUTES_PER_DAY - step)
    }

    /** A length on the step of 15 minutes, the nearest one, from 15 minutes to 8 hours (spec 5.28). */
    fun snapDuration(minutes: Int, config: EventsConfig): Int {
        val step = config.durationStepMinutes
        return ((minutes + step / 2).floorDiv(step) * step).coerceIn(config.minDurationMinutes, config.maxDurationMinutes)
    }

    /** [steps] steps of the sheet «Длительность» from [current]; no length yet — from an hour (spec 3.36.9). */
    fun stepDuration(current: Int?, steps: Int, config: EventsConfig): Int =
        ((current ?: config.durationSheetStartMinutes) + steps * config.durationStepMinutes)
            .coerceIn(config.minDurationMinutes, config.maxDurationMinutes)

    /** When it starts by the clock: its start, or the midnight its day begins with. */
    fun startOf(date: LocalDate, startMinutes: Int?): LocalDateTime =
        (startMinutes ?: 0).let { date.atTime(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }

    fun startOf(event: CalendarEvent): LocalDateTime = startOf(event.date, event.startMinutes)

    /**
     * When it ends by the clock (spec 5.28): the start and the length; without a length the start and an hour; «весь день»
     * — the midnight after its day. A late event ends on the next day: 23:30 and 1 h 30 min end at 01:00.
     */
    fun endOf(date: LocalDate, startMinutes: Int?, durationMinutes: Int?, config: EventsConfig): LocalDateTime {
        if (startMinutes == null) return date.plus(1, DateTimeUnit.DAY).atTime(0, 0)
        val end = startMinutes + (durationMinutes ?: config.lengthWithoutDurationMinutes)
        val minutes = end % MINUTES_PER_DAY
        return date.plus(end / MINUTES_PER_DAY, DateTimeUnit.DAY).atTime(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

    fun endOf(event: CalendarEvent, config: EventsConfig): LocalDateTime =
        endOf(event.date, event.startMinutes, event.durationMinutes, config)

    /** The moment it starts in [zone] ([momentOf]): the first moment of its day for «весь день». */
    fun startAt(event: CalendarEvent, zone: TimeZone): Instant =
        if (event.startMinutes == null) event.date.atStartOfDayIn(zone) else momentOf(startOf(event), zone)

    /** The moment it ends in [zone] ([momentOf]): the first moment of the next day for «весь день». */
    fun endAt(event: CalendarEvent, zone: TimeZone, config: EventsConfig): Instant =
        if (event.startMinutes == null) {
            event.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        } else {
            momentOf(endOf(event, config), zone)
        }

    /**
     * The first moment the clock of [zone] shows [time] or a later one: the only one of an ordinary time, the earlier of
     * the two in the overlap of the change back to winter time, and for a time the change to summer time skips — the jump,
     * the first moment after the gap. kotlinx-datetime reads a time in the gap with the offset before it, later than the
     * jump by as much as the time is into the gap (02:30 → 03:30 summer time); the jump is between that and the same moment
     * a gap's length earlier, and is found by halves to the second. Never decreasing in [time].
     */
    fun momentOf(time: LocalDateTime, zone: TimeZone): Instant {
        val read = time.toInstant(zone)
        val shown = read.toLocalDateTime(zone)
        if (shown == time) return read
        var after = read.epochSeconds
        var before = after - (shown.toInstant(TimeZone.UTC).epochSeconds - time.toInstant(TimeZone.UTC).epochSeconds)
        while (after - before > 1) {
            val middle = before + (after - before) / 2
            if (Instant.fromEpochSeconds(middle).toLocalDateTime(zone) >= time) after = middle else before = middle
        }
        return Instant.fromEpochSeconds(after)
    }

    /**
     * Its end has come (plan D4): what «Выступления» divide by (spec 3.36.9), what leaves the reminder, and what an edit of
     * a repeat never touches (spec 3.35).
     */
    fun isPast(event: CalendarEvent, now: Instant, zone: TimeZone, config: EventsConfig): Boolean = now >= endAt(event, zone, config)

    /**
     * What a new event is given (spec 3.35, 5.28; plan D28, D49): the kind of the event a person created last — the
     * greatest moment of creation, the smaller id among equals — «Урок» for the very first one; the time of that kind
     * ([timeOf]). An event laid by the horizon carries the moment of its repeat, so it never outranks a concert created
     * after the repeat.
     */
    fun defaults(events: List<CalendarEvent>, series: List<EventSeries>): EventDefaults {
        val kind = events.minWithOrNull(LAST_CREATED_FIRST)?.kind ?: KindRef.BuiltIn(BuiltInKind.LESSON)
        return EventDefaults(kind, timeOf(kind, events, series))
    }

    /**
     * The start and the length of [kind] (spec 5.28: «начало последнего события этого вида»): those of the last event of it a
     * person created — of its repeat, when it repeats: the template is what the repeat is now, after «Этот и следующие»
     * changed it, while its first event keeps the time it had. Of the events created at that moment — a repeat, and the
     * one split off from it with a new day or step, whose events carry its moment (plan D49) — the latest date counts: the
     * repeat that lays them now, not the one that ended. No event of the kind — «весь день» without a length.
     */
    fun timeOf(kind: KindRef, events: List<CalendarEvent>, series: List<EventSeries>): EventTime {
        val last = events.filter { it.kind == kind }.minWithOrNull(LAST_CREATED_LATEST_FIRST) ?: return EventTime(null, null)
        val repeat = last.seriesId?.let { id -> series.firstOrNull { it.id == id } }
        return if (repeat != null && repeat.kind == kind) {
            EventTime(repeat.startMinutes, repeat.durationMinutes)
        } else {
            EventTime(last.startMinutes, last.durationMinutes)
        }
    }

    /** The last month the calendar goes to (spec 5.28): the current one and 12, or the month of the farthest event. */
    fun calendarLastMonth(today: LocalDate, events: List<CalendarEvent>, config: EventsConfig): YearMonth {
        val ahead = today.yearMonth.plus(config.calendarMonthsAhead, DateTimeUnit.MONTH)
        val farthest = events.maxOfOrNull { it.date }?.yearMonth ?: return ahead
        return maxOf(ahead, farthest)
    }

    /** The last month of the sheet «Дата» (spec 3.36.9): the current one and 12, or the month of the date being edited. */
    fun formLastMonth(today: LocalDate, edited: LocalDate?, config: EventsConfig): YearMonth {
        val ahead = today.yearMonth.plus(config.formMonthsAhead, DateTimeUnit.MONTH)
        return edited?.yearMonth?.let { maxOf(ahead, it) } ?: ahead
    }

    /**
     * The chips «До 31 дек. · До 31 мая» (spec 3.36.9, plan D30): each day of [EventsConfig.untilChipDates] at its nearest
     * strictly after the first event, in the order of the config.
     */
    fun untilChips(firstDate: LocalDate, config: EventsConfig): List<LocalDate> = config.untilChipDates.map { it.nextAfter(firstDate) }

    /**
     * «Частое» (spec 3.36.9, plan D27): the starts of a person's events by how many events have them — a lesson of a repeat
     * counts every week, as often as it is — the one used later first among equals; «весь день» is no start. At most [limit].
     */
    fun frequentStarts(events: List<CalendarEvent>, limit: Int): List<Int> = events
        .filter { it.startMinutes != null }
        .groupBy { it.startMinutes!! }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<Int, List<CalendarEvent>>> { it.value.size }
                .thenByDescending { entry -> entry.value.maxOf { it.date } }
                .thenBy { it.key },
        )
        .take(limit)
        .map { it.key }

    /** The greatest moment of creation first, the smaller id among equals (plan D28). */
    private val LAST_CREATED_FIRST: Comparator<CalendarEvent> =
        compareByDescending<CalendarEvent> { it.createdAtEpochMs }.thenBy { it.id }

    /** The greatest moment of creation first, the latest date among equals, then the smaller id ([timeOf]). */
    private val LAST_CREATED_LATEST_FIRST: Comparator<CalendarEvent> =
        compareByDescending<CalendarEvent> { it.createdAtEpochMs }.thenByDescending { it.date }.thenBy { it.id }

    private fun cut(text: String, max: Int): String = text.trim().takeCodePoints(max).trimEnd()
}
