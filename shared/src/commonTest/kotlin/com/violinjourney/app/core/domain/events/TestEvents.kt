package com.violinjourney.app.core.domain.events

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** [days] later (earlier when negative). */
internal fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

/** Events and moments for the tests of the events: everything but what a test is about has a plain default. */
internal object TestEvents {
    val LESSON: KindRef = KindRef.BuiltIn(BuiltInKind.LESSON)
    val REHEARSAL: KindRef = KindRef.BuiltIn(BuiltInKind.REHEARSAL)
    val PERFORMANCE: KindRef = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
    val OTHER: KindRef = KindRef.BuiltIn(BuiltInKind.OTHER)

    val MOSCOW: TimeZone = TimeZone.of("Europe/Moscow")
    val BERLIN: TimeZone = TimeZone.of("Europe/Berlin")

    fun event(
        id: Long,
        date: LocalDate,
        start: Int? = null,
        duration: Int? = null,
        kind: KindRef = LESSON,
        title: String = "",
        place: String = "",
        notes: String = "",
        seriesId: Long? = null,
        detached: Boolean = false,
        createdAt: Long = id,
    ) = CalendarEvent(id, kind, date, start, duration, title, place, notes, seriesId, detached, createdAt)

    fun series(
        id: Long,
        first: LocalDate,
        laidUntil: LocalDate,
        repeat: Repeat = Repeat.WEEKLY,
        until: LocalDate? = null,
        start: Int? = null,
        duration: Int? = null,
        kind: KindRef = LESSON,
        title: String = "",
        place: String = "",
    ) = EventSeries(id, kind, repeat, first, until, laidUntil, start, duration, title, place)

    /** Minutes from midnight of hh:mm. */
    fun at(hours: Int, minutes: Int = 0): Int = hours * 60 + minutes

    /** The moment of a local [date] and time in [zone]. */
    fun moment(date: LocalDate, hours: Int, minutes: Int = 0, zone: TimeZone = MOSCOW): Instant =
        date.atTime(hours, minutes).toInstant(zone)

    /**
     * A weekly repeat [seriesId] of lessons on [dates] at [start] for [duration]: ids from [firstId] on, created at the moment
     * of the first one, as the horizon lays them (plan D49).
     */
    fun weekly(seriesId: Long, dates: List<LocalDate>, start: Int?, duration: Int?, firstId: Long, createdAt: Long = firstId, place: String = "") =
        dates.mapIndexed { index, date ->
            event(firstId + index, date, start, duration, seriesId = seriesId, createdAt = createdAt, place = place)
        }
}
