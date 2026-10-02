package com.violinjourney.app.core.domain.events

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What laying a repeat ahead adds: the dates of its new events, and how far it has been laid with them. */
data class Laying(val dates: List<LocalDate>, val laidUntil: LocalDate)

/**
 * The end of a repeat in words (spec 3.36.9): its last event and how many there are — «последний урок — пн 28 декабря ·
 * всего 14», «до 31 дек. · 14 уроков». No event at all — [last] is null and [count] 0.
 */
data class UntilSummary(val last: LocalDate?, val count: Int)

/**
 * A repeat (spec 3.35, 5.28): a step of 7 or 14 days from the date of its first event, «до» inclusive. Dates are local
 * days — a change of the clock to summer time moves none of them.
 */
object Recurrence {
    /** The dates of a repeat from [first] that fall within [from]…[to] and not after [until]. */
    fun dates(first: LocalDate, repeat: Repeat, from: LocalDate, to: LocalDate, until: LocalDate?): List<LocalDate> {
        val end = if (until != null && until < to) until else to
        if (end < from) return emptyList()
        val step = repeat.stepDays ?: return if (first in from..end) listOf(first) else emptyList()
        // the first step on or after [from]: a repeat that began long ago does not walk through all its years
        val skipped = if (from <= first) 0 else (first.daysUntil(from) + step - 1) / step
        return generateSequence(first.plus(skipped * step, DateTimeUnit.DAY)) { it.plus(step, DateTimeUnit.DAY) }
            .takeWhile { it <= end }
            .toList()
    }

    /**
     * What is laid ahead (spec 5.28, plan D3): the dates after [EventSeries.laidUntil] up to today and the horizon, not after
     * «до» — weeks the app was not opened in are laid too: a repeat means every week. The repeat is then laid up to that
     * bound and never back: a clock set back lays nothing, and when it comes forward again nothing is laid twice.
     */
    fun toLay(series: EventSeries, today: LocalDate, horizonWeeks: Int): Laying {
        val horizon = today.plus(horizonWeeks * DAYS_PER_WEEK, DateTimeUnit.DAY)
        val bound = series.until?.let { minOf(it, horizon) } ?: horizon
        if (bound <= series.laidUntil) return Laying(emptyList(), series.laidUntil)
        return Laying(dates(series.firstDate, series.repeat, series.laidUntil.plus(1, DateTimeUnit.DAY), bound, series.until), bound)
    }

    /**
     * The end of a stored repeat (plan, «occurrencesUntil»): the events it has in the database and the dates it will still
     * lay after [EventSeries.laidUntil] up to «до». A repeat begun by an edit from a date gone by has no events for the
     * days before the edit, and they are not counted (plan D4); an event deleted «only this» is not counted either. Null
     * for a repeat without an end.
     */
    fun summary(series: EventSeries, seriesEvents: List<CalendarEvent>): UntilSummary? {
        val until = series.until ?: return null
        val laid = seriesEvents.filter { it.seriesId == series.id }.map { it.date }
        val ahead = dates(series.firstDate, series.repeat, series.laidUntil.plus(1, DateTimeUnit.DAY), until, until)
        val all = laid + ahead
        return UntilSummary(all.maxOrNull(), all.size)
    }

    /** The end of a repeat not saved yet — the form's (spec 3.36.9): every date from its first one to «до». Null without an end. */
    fun newSummary(firstDate: LocalDate, repeat: Repeat, until: LocalDate?): UntilSummary? {
        until ?: return null
        val all = dates(firstDate, repeat, firstDate, until, until)
        return UntilSummary(all.lastOrNull(), all.size)
    }

    /** The day of the week a repeat falls on: the one of its first date (spec 3.36.9: «по понедельникам»). */
    fun weekday(date: LocalDate): DayOfWeek = date.dayOfWeek

    /** Whether the repeat still has dates to lay after [EventSeries.laidUntil]: one without an end always has. */
    fun hasDatesAhead(series: EventSeries): Boolean {
        val until = series.until ?: return true
        return dates(series.firstDate, series.repeat, series.laidUntil.plus(1, DateTimeUnit.DAY), until, until).isNotEmpty()
    }

    /** The day before [date]: where a repeat ends when it is cut at [date] (spec 5.28). */
    fun dayBefore(date: LocalDate): LocalDate = date.minus(1, DateTimeUnit.DAY)
}
