package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
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
 * A row of the list «Выступления» (spec 3.36.9): the plate of [date] — with its year when that is not the year of today, next year's too
 * ([otherYear], plan D41) — the name ([name]: its title, or the name of the kind «Выступление»), the place and the time ([place] — empty
 * when not given; [startMinutes] null — «весь день»), the term of one ahead ([days]: calendar days to it, 0 — «сегодня», 1 — «завтра»,
 * never below 0, D38; null for one over), its programme in one line ([program]), and at its end the thumbnail of the newest of its
 * recordings with a video ([lastVideo] — the name of that video file, D43), or the number of its [records], or nothing.
 */
data class PerformanceRow(
    val eventId: Long,
    val date: LocalDate,
    val otherYear: Boolean,
    val name: EventName,
    val place: String,
    val startMinutes: Int?,
    val days: Int?,
    val program: List<String>,
    val records: Int,
    val lastVideo: String?,
)

/** The rows of «Выступления»: «Впереди» the nearest first, «Прошли» the freshest first ([Performances.split]). */
data class PerformanceRows(val ahead: List<PerformanceRow>, val past: List<PerformanceRow>)

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
     * The rows of the screen «Выступления» (spec 3.36.9) at [now]: what [split] divides, each with what the row shows — the programme of
     * the event ([programs]: event → the ids of its elements in their order; an element gone is not in it), and of its recordings
     * ([sessions] bound to it by `eventId`) their number and the video of the newest one that has a video.
     */
    fun rows(
        events: List<CalendarEvent>,
        programs: Map<Long, List<Long>>,
        pieces: List<Piece>,
        sessions: List<SessionSummary>,
        now: Instant,
        zone: TimeZone,
        config: EventsConfig,
    ): PerformanceRows {
        val (ahead, past) = split(events, now, zone, config)
        val today = now.toLocalDateTime(zone).date
        val byId = pieces.associateBy { it.id }
        val recordings = sessions.filter { it.eventId != null }.groupBy { it.eventId }
        fun rowOf(event: CalendarEvent, isAhead: Boolean): PerformanceRow {
            val own = recordings[event.id].orEmpty()
            val newestVideo = own.filter { it.videoPath != null }.maxWithOrNull(NEWEST)
            return PerformanceRow(
                eventId = event.id,
                date = event.date,
                otherYear = event.date.year != today.year,
                // the kind is the built-in «Выступление»: it is there whether the kinds are read or not
                name = EventName.of(event.title, event.kind, emptyList()),
                place = event.place.trim(),
                startMinutes = event.startMinutes,
                days = if (isAhead) today.daysUntil(event.date).coerceAtLeast(0) else null,
                program = programNames(programs[event.id].orEmpty().mapNotNull(byId::get)),
                records = own.size,
                lastVideo = newestVideo?.videoPath,
            )
        }
        return PerformanceRows(ahead.map { rowOf(it, isAhead = true) }, past.map { rowOf(it, isAhead = false) })
    }

    /**
     * When «Выступления» and the row on «Записи» change by themselves (plan D42): the nearest of the next midnight — the term of a row,
     * «завтра» turning into «сегодня» — and the ends of the performances not over yet — the concert of today goes over to «Прошли».
     * Always after [now]; never at a start, which changes nothing in them.
     */
    fun nextChangeAt(events: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): Instant {
        val midnight = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val ends = events.filter { it.kind == KIND }.map { EventRules.endAt(it, zone, config) }.filter { it > now }
        return (ends + midnight).min()
    }

    /**
     * The programme in one line (spec 3.36.9): the composers as they were written, and the title of an element without one — of a scale
     * always, as the programme of the screen of an event names it — in the order of the programme; a name met again is not repeated —
     * three pieces of Bach read «Бах». The screen joins them.
     */
    fun programNames(pieces: List<Piece>): List<String> = pieces
        .map { piece -> piece.composer.trim().takeIf { it.isNotEmpty() && piece.scale == null } ?: piece.title.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** The newest recording: the latest start, then the larger id — as the lists put recordings of one moment. */
    private val NEWEST = compareBy<SessionSummary> { it.startedAtEpochMs }.thenBy { it.id }
}
