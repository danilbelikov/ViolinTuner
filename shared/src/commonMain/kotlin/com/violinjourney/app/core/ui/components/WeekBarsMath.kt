package com.violinjourney.app.core.ui.components

import kotlin.math.max

/**
 * The rules of the bars of a week (spec 5.29, 01-practice п. 6), pure, with a test: how high a day stands and how it looks.
 *
 * A bar is its day's minutes over the longer of the longest day of the week and the bottom of the scale — the highest step of
 * the calendar (90 min, 5.6), so a short week does not look like a long one; a day with any time stands at least [MIN_FILL_DP].
 */
internal object WeekBarsMath {
    const val MIN_FILL_DP = 4f

    /** 0…1 of the bar; 0 for a day without time. */
    fun share(minutes: Int, weekMax: Int, scaleMinutes: Int): Float {
        if (minutes <= 0) return 0f
        val top = max(max(weekMax, scaleMinutes), minutes)
        return (minutes.toFloat() / top).coerceAtMost(1f)
    }

    /** The height of the fill in dp in a bar of [barDp]: none for none, never lower than [MIN_FILL_DP], never above the bar. */
    fun fillDp(share: Float, barDp: Float): Float = if (share <= 0f) 0f else max(share * barDp, MIN_FILL_DP).coerceAtMost(barDp)

    enum class BarLook {
        /** A past day with time: the track and the fill of primaryContainer. */
        Filled,

        /** A past day without time: a dashed outline, no track, no figures — nobody is shamed for it. */
        Missed,

        /** A day to come: an empty track, even if it has minutes. */
        Future,

        /** Today: the track, the fill in the accent, a frame of the accent — also at zero, the day is not over. */
        Today,

        /** Today while a practice runs: the fill hatched. */
        TodayRunning,
    }

    fun look(day: WeekBarDay, running: Boolean): BarLook = when (day.day) {
        WeekDayWhen.Past -> if (day.minutes > 0) BarLook.Filled else BarLook.Missed
        WeekDayWhen.Today -> if (running) BarLook.TodayRunning else BarLook.Today
        WeekDayWhen.Future -> BarLook.Future
    }
}
