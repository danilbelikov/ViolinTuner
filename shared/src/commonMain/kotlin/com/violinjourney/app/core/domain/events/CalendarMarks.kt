package com.violinjourney.app.core.domain.events

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** The marks of one cell of the calendar (spec 3.36.9): the looks of its first events, and «+» when there are more. */
data class DayMarks(val looks: List<KindLook>, val more: Boolean) {
    companion object {
        val NONE = DayMarks(emptyList(), more = false)
    }
}

/**
 * Events in the calendar of «Занятия» (spec 3.35, 3.36.9): the order of a day, the marks of a cell, the count and the
 * kinds of a month. An event belongs to the day — and the month — it starts on (spec 5.28).
 */
object CalendarMarks {
    /** The order of a day (spec 5.28): «весь день» first, then by start; then by creation and id, so that it never shifts. */
    val DAY_ORDER: Comparator<CalendarEvent> = compareBy<CalendarEvent> { it.date }
        .thenBy { it.startMinutes != null }
        .thenBy { it.startMinutes ?: 0 }
        .thenBy { it.createdAtEpochMs }
        .thenBy { it.id }

    /** The events of [date] in the order of a day. */
    fun dayEvents(events: List<CalendarEvent>, date: LocalDate): List<CalendarEvent> = events.filter { it.date == date }.sortedWith(DAY_ORDER)

    /** The marks of a cell (spec 5.28): the first [EventsConfig.cellMarks] events of the day by their kinds' looks, «+» for the rest. */
    fun marksOf(dayEvents: List<CalendarEvent>, kinds: List<EventKind>, config: EventsConfig): DayMarks {
        if (dayEvents.isEmpty()) return DayMarks.NONE
        return DayMarks(dayEvents.take(config.cellMarks).map { KindRules.lookOf(it.kind, kinds, config) }, more = dayEvents.size > config.cellMarks)
    }

    /** How many events [month] has: the line under the name of a month to come, «17 событий» (spec 3.36.9). */
    fun monthCount(events: List<CalendarEvent>, month: YearMonth): Int = events.count { it.date.yearMonth == month }

    /**
     * The legend of [month] (spec 3.36.9): the kinds its events are of, each once, in the order of [ordered] — the order of
     * the form ([KindRules.ordered]); an event of a kind of one's own that is gone is of «Другое».
     */
    fun kindsOfMonth(events: List<CalendarEvent>, month: YearMonth, ordered: List<EventKind>): List<EventKind> {
        val present = events.filter { it.date.yearMonth == month }.mapTo(mutableSetOf()) { KindRules.resolve(it.kind, ordered) }
        return ordered.filter { it.ref in present }
    }
}
