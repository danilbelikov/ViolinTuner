package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.components.WeekDayWhen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class TodayWeekTest {
    private val labels = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val floor = PracticeConfig().fillLevelMinutes.last()

    /** Thursday 17 September 2026: Monday to Wednesday are past, Friday to Sunday to come. */
    private val thursday = LocalDate(2026, 9, 17)

    private fun ms(vararg minutes: Int) = minutes.map { it * MS_PER_MINUTE }

    @Test
    fun `minutes are rounded up so a day with any time is never a missed one`() {
        assertEquals(0, TodayWeek.minutesOf(0))
        assertEquals(1, TodayWeek.minutesOf(30_000))
        assertEquals(1, TodayWeek.minutesOf(MS_PER_MINUTE))
        assertEquals(2, TodayWeek.minutesOf(MS_PER_MINUTE + 1))
    }

    @Test
    fun `an even week of thirty minutes is scaled to ninety - it is not a record one`() {
        val bars = TodayWeek.days(ms(30, 30, 30, 30, 0, 0, 0), thursday, labels, runningMinutes = 0, hatch = false, floorMinutes = floor)
        assertEquals(90, bars.scaleMinutes)
        assertEquals(listOf(30, 30, 30, 30, 0, 0, 0), bars.days.map { it.minutes })
    }

    @Test
    fun `a long day sets the scale of the week`() {
        assertEquals(125, TodayWeek.scale(listOf(30, 125, 45), floor))
        assertEquals(90, TodayWeek.scale(emptyList(), floor))
    }

    @Test
    fun `the running practice adds to today but never moves the scale`() {
        // saved 30 today, 200 minutes running: today reaches the whole track, the scale stays the 90 of what is saved
        val bars = TodayWeek.days(ms(0, 0, 0, 30, 0, 0, 0), thursday, labels, runningMinutes = 200, hatch = true, floorMinutes = floor)
        assertEquals(90, bars.scaleMinutes)
        assertEquals(90, bars.days[3].minutes)
        assertEquals(WeekDayWhen.Today, bars.days[3].day)

        val short = TodayWeek.days(ms(0, 0, 0, 30, 0, 0, 0), thursday, labels, runningMinutes = 15, hatch = true, floorMinutes = floor)
        assertEquals(45, short.days[3].minutes)
    }

    @Test
    fun `without a hatch the running minutes are not added`() {
        // hatch is PracticeState.runningToday, false for a practice begun yesterday (PracticeReducerTest): today shows what is saved today only
        val bars = TodayWeek.days(ms(0, 0, 0, 30, 0, 0, 0), thursday, labels, runningMinutes = 40, hatch = false, floorMinutes = floor)
        assertEquals(30, bars.days[3].minutes)
    }

    @Test
    fun `the first seconds of the first practice already hatch today`() {
        // nothing saved today, 30 s running: the running minutes are rounded up as saved ones are — the bar is not empty
        val running = TodayWeek.minutesOf(30_000)
        val bars = TodayWeek.days(ms(0, 0, 0, 0, 0, 0, 0), thursday, labels, runningMinutes = running, hatch = true, floorMinutes = floor)
        assertEquals(1, bars.days[3].minutes)
        assertEquals(90, bars.scaleMinutes)
        val notRead = TodayWeek.days(ms(0, 0, 0, 0, 0, 0, 0), thursday, labels, runningMinutes = TodayWeek.minutesOf(0), hatch = true, floorMinutes = floor)
        assertEquals(0, notRead.days[3].minutes, "the clock not read yet adds nothing")
    }

    @Test
    fun `each day knows where it stands against today`() {
        val bars = TodayWeek.days(ms(35, 0, 20, 0, 0, 0, 0), thursday, labels, runningMinutes = 0, hatch = false, floorMinutes = floor)
        assertEquals(
            listOf(WeekDayWhen.Past, WeekDayWhen.Past, WeekDayWhen.Past, WeekDayWhen.Today, WeekDayWhen.Future, WeekDayWhen.Future, WeekDayWhen.Future),
            bars.days.map { it.day },
        )
        // today without practice is still today — not a missed day; a past day without practice is
        assertEquals(0, bars.days[3].minutes)
        assertEquals(0, bars.days[1].minutes)
        assertEquals(labels, bars.days.map { it.label })
    }

    @Test
    fun `on a sunday the whole week is past but today`() {
        val sunday = LocalDate(2026, 9, 27)
        val bars = TodayWeek.days(ms(35, 50, 20, 65, 40, 90, 45), sunday, labels, runningMinutes = 0, hatch = false, floorMinutes = floor)
        assertEquals(List(6) { WeekDayWhen.Past } + WeekDayWhen.Today, bars.days.map { it.day })
        assertEquals(90, bars.scaleMinutes)
    }
}
