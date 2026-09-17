package com.example.violintuner.core.domain.session

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Average score of one calendar week; null when nothing was recorded that week. */
data class WeekScore(val weekStart: LocalDate, val averageScore: Int?)

/** Weekly chart of the history screen (spec 3.11, 5.5): weeks start on Monday, local time. */
object HistoryWeeks {
    /** The last [weeks] weeks, oldest first; the last one contains [today]. */
    fun weekly(sessions: List<SessionSummary>, today: LocalDate, zone: ZoneId, weeks: Int): List<WeekScore> {
        val currentWeek = weekStartOf(today)
        val scoresByWeek = sessions.groupBy(
            keySelector = { weekStartOf(Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate()) },
            valueTransform = { it.scorePercent },
        )
        return (weeks - 1 downTo 0).map { back ->
            val start = currentWeek.minusWeeks(back.toLong())
            WeekScore(start, scoresByWeek[start]?.average()?.roundToInt())
        }
    }

    /** Last week minus the one before; null unless both have sessions. */
    fun delta(weeks: List<WeekScore>): Int? {
        if (weeks.size < 2) return null
        val last = weeks[weeks.size - 1].averageScore ?: return null
        val previous = weeks[weeks.size - 2].averageScore ?: return null
        return last - previous
    }

    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
