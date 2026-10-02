package com.violinjourney.app.core.domain.events

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** The day of a row of the reminder, as its words say it (spec 3.36.9): «Завтра в 17:00», «Сегодня в 18:30», «Вчера в 23:00». */
enum class ReminderDay { YESTERDAY, TODAY, TOMORROW }

/** An event of the reminder that is going on (spec 3.36.9): «· идёт, до 17:45», or «· идёт» for one without a length. */
sealed interface Running {
    data class Until(val end: LocalDateTime) : Running

    data object Open : Running
}

/**
 * One row of the reminder: what its two lines and its sign are made of. [running] — null when it has not begun, and for «весь день».
 * [kind] — the kind it is of, a kind of one's own that is gone read as «Другое», with [ownName]: «ещё N» names the kind of each event it
 * stands for (plan D8), a titled one too.
 */
data class ReminderRow(
    val eventId: Long,
    val date: LocalDate,
    val day: ReminderDay,
    val startMinutes: Int?,
    val look: KindLook,
    val name: EventName,
    val place: String,
    val running: Running?,
    val kind: KindRef,
    val ownName: String?,
)

/**
 * The card on «Занятия» (spec 3.35, 3.36.9): every event in its window, in order. The full card shows [shownRows] of them
 * and «ещё N» for the rest, the compact one [compactShownRows]; which one stands is the screen's choice.
 */
data class Reminder(val rows: List<ReminderRow>, val shownRows: Int, val compactShownRows: Int) {
    fun visible(compact: Boolean): List<ReminderRow> = rows.take(if (compact) compactShownRows else shownRows)

    fun hidden(compact: Boolean): List<ReminderRow> = rows.drop(if (compact) compactShownRows else shownRows)

    /** The day «ещё N» opens (spec 3.36.9): that of the first event it stands for; null without «ещё». */
    fun firstHiddenDate(compact: Boolean): LocalDate? = hidden(compact).firstOrNull()?.date
}

/**
 * The reminder on «Занятия» (spec 3.35, 5.28, 3.36.9): an event is in it from 00:00 of the day before its own until its
 * end — one of yesterday that runs past midnight too, until it ends (plan D7). Ordered by date, «весь день» first in a
 * day, then by start. Moments are reckoned in the zone the phone is in now (see [EventRules]).
 */
object EventReminder {
    /** The card at [now]; null when no event is in its window — then there is no card at all. */
    fun of(events: List<CalendarEvent>, kinds: List<EventKind>, now: Instant, zone: TimeZone, config: EventsConfig): Reminder? {
        val today = now.toLocalDateTime(zone).date
        val rows = inWindow(events, now, zone, config)
            .sortedWith(CalendarMarks.DAY_ORDER)
            .mapNotNull { event ->
                val day = when (today.daysUntil(event.date)) {
                    -1 -> ReminderDay.YESTERDAY
                    0 -> ReminderDay.TODAY
                    1 -> ReminderDay.TOMORROW
                    else -> return@mapNotNull null
                }
                val running = when {
                    event.startMinutes == null || now < EventRules.startAt(event, zone) -> null
                    event.durationMinutes == null -> Running.Open
                    else -> Running.Until(EventRules.endOf(event, config))
                }
                val kind = KindRules.resolve(event.kind, kinds)
                ReminderRow(
                    eventId = event.id, date = event.date, day = day, startMinutes = event.startMinutes,
                    look = KindRules.lookOf(event.kind, kinds, config), name = EventName.of(event.title, event.kind, kinds),
                    place = event.place, running = running, kind = kind, ownName = kinds.firstOrNull { it.ref == kind }?.ownName,
                )
            }
        return if (rows.isEmpty()) null else Reminder(rows, config.reminderRows, config.compactReminderRows)
    }

    /**
     * When the card has to be reckoned again while the screen is open (spec 3.36.9): the nearest of the next midnight (a
     * new day brings tomorrow's events and the day's own words) and of the starts and ends of the events in the card — so
     * «идёт» comes at the start, and the card leaves at the end, by itself. Always after [now].
     */
    fun nextRecalcAt(events: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): Instant {
        val midnight = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val moments = inWindow(events, now, zone, config).flatMap { event ->
            listOfNotNull(
                EventRules.startAt(event, zone).takeIf { event.startMinutes != null && it > now },
                EventRules.endAt(event, zone, config),
            )
        }
        return (moments + midnight).min()
    }

    private fun inWindow(events: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): List<CalendarEvent> =
        events.filter { event ->
            now >= event.date.minus(config.reminderFromDaysBefore, DateTimeUnit.DAY).atStartOfDayIn(zone) &&
                now < EventRules.endAt(event, zone, config)
        }
}
