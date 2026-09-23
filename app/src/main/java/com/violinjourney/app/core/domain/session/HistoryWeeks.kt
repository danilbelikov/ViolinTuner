package com.violinjourney.app.core.domain.session

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Weeks of the app start on Monday, local time (spec 5.5, 5.6). */
object HistoryWeeks {
    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/** How many recordings were started on one local day. */
data class DayCount(val date: LocalDate, val count: Int)

/** The chart of «Записи» (spec 3.21, 5.15): recordings per day. The day of a recording is the local date of its start, in the viewer's zone. */
object RecordDays {
    fun dateOf(session: SessionSummary, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zone).toLocalDate()

    /** The last [days] days, oldest first; the last one is [today]. Sound, takes and video alike. */
    fun daily(sessions: List<SessionSummary>, today: LocalDate, zone: ZoneId, days: Int): List<DayCount> {
        val byDate = sessions.groupingBy { dateOf(it, zone) }.eachCount()
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            DayCount(date, byDate[date] ?: 0)
        }
    }

    /** What the tallest bar stands for: the busiest day, but never less than [minTop] — one recording is not a record. */
    fun scaleTop(days: List<DayCount>, minTop: Int): Int = maxOf(days.maxOfOrNull { it.count } ?: 0, minTop)
}
