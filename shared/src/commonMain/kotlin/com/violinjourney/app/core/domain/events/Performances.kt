package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.repertoire.Piece
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** «Выступления» divided (spec 3.36.9): [ahead] — the nearest first, [past] — the freshest first. */
data class PerformanceSplit(val ahead: List<CalendarEvent>, val past: List<CalendarEvent>)

/** The caption of the row «Выступления» on «Записи» (spec 3.36.9): its three cases. */
sealed interface PerformancesLine {
    /**
     * [days] — calendar days to [nearest] (plan D38): 0 — «сегодня», also for one of yesterday still going past midnight
     * (it is «сегодня» until it ends), 1 — «завтра»; never below 0. [pastCount] — how many are over.
     */
    data class Ahead(val nearest: CalendarEvent, val days: Int, val pastCount: Int) : PerformancesLine

    data class OnlyPast(val count: Int, val lastDate: LocalDate) : PerformancesLine

    data object None : PerformancesLine
}

/**
 * The list «Выступления» (spec 3.35, 3.36.9): every event of the kind «Выступление», divided by its end (spec 5.28) — a
 * concert of today is ahead until it is over.
 */
object Performances {
    /** Only the built-in kind collects its events here: «Выступление» is the one kind with a list of its own (spec 3.35). */
    private val KIND = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)

    fun split(events: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): PerformanceSplit {
        val (past, ahead) = events.filter { it.kind == KIND }.partition { EventRules.isPast(it, now, zone, config) }
        return PerformanceSplit(ahead = ahead.sortedWith(CalendarMarks.DAY_ORDER), past = past.sortedWith(CalendarMarks.DAY_ORDER).reversed())
    }

    /** The caption of the row on «Записи»: the nearest one ahead and how many are over; only those over; none at all. */
    fun line(events: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): PerformancesLine {
        val (ahead, past) = split(events, now, zone, config)
        val nearest = ahead.firstOrNull()
        return when {
            nearest != null -> PerformancesLine.Ahead(nearest, now.toLocalDateTime(zone).date.daysUntil(nearest.date).coerceAtLeast(0), past.size)
            past.isNotEmpty() -> PerformancesLine.OnlyPast(past.size, past.first().date)
            else -> PerformancesLine.None
        }
    }

    /**
     * The program in one line (spec 3.36.9): the composers as they were written, and the title of a piece without one, in
     * the order of the program, through « · »; a name met again is not repeated — three pieces of Bach read «Бах».
     */
    fun programLine(pieces: List<Piece>): String = pieces
        .map { piece -> piece.composer.ifBlank { piece.title }.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(SEPARATOR)

    private const val SEPARATOR = " · "
}
