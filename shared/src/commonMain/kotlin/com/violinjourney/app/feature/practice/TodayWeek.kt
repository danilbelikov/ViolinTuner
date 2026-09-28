package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.components.WeekBarDay
import com.violinjourney.app.core.ui.components.WeekDayWhen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The bars of the week of «Сегодня» and of the card «Неделя» (spec 3.36.2, 5.29), pure, with a test: what each day of the week
 * shows and against what scale.
 *
 * A bar is the saved time of its day ([PracticeSummary.weekDaysMs]); the scale is the longest saved day of the week, but never
 * less than the highest step of the calendar — an even week of 30 minutes a day does not look like a record. A practice that
 * runs and began today hatches today's bar: saved and running together, never above the whole track, and the scale stays the
 * scale of what is saved — the running minutes do not move the other bars.
 */
internal object TodayWeek {
    /** The bars and the top of their scale, in minutes. */
    data class Bars(val days: List<WeekBarDay>, val scaleMinutes: Int)

    /** Whole minutes rounded up: a day with any time is never drawn as a day without. */
    fun minutesOf(ms: Long): Int = ((ms.coerceAtLeast(0) + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()

    /** The top of the scale: the longest saved day, never below [floorMinutes]. */
    fun scale(saved: List<Int>, floorMinutes: Int): Int = maxOf(saved.maxOrNull() ?: 0, floorMinutes)

    /**
     * The seven bars from Monday: [weekDaysMs] of the saved time, their [labels] («пн» … «вс»), and today's bar with
     * [runningMinutes] on top when [hatch] — a practice begun today runs ([PracticeState.runningToday]). The running minutes
     * are rounded up by [minutesOf] as the saved ones are: the first minute of the first practice is hatched too.
     */
    fun days(
        weekDaysMs: List<Long>,
        today: LocalDate,
        labels: List<String>,
        runningMinutes: Int,
        hatch: Boolean,
        floorMinutes: Int,
    ): Bars {
        val saved = weekDaysMs.map(::minutesOf)
        val scale = scale(saved, floorMinutes)
        val todayIndex = today.dayOfWeek.isoDayNumber - 1
        val days = saved.mapIndexed { index, minutes ->
            val day = when {
                index < todayIndex -> WeekDayWhen.Past
                index == todayIndex -> WeekDayWhen.Today
                else -> WeekDayWhen.Future
            }
            val shown = if (day == WeekDayWhen.Today && hatch) minOf(minutes + runningMinutes.coerceAtLeast(0), scale) else minutes
            WeekBarDay(labels.getOrElse(index) { "" }, shown, day)
        }
        return Bars(days, scale)
    }
}
