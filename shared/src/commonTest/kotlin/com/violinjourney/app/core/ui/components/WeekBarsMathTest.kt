package com.violinjourney.app.core.ui.components

import com.violinjourney.app.core.ui.components.WeekBarsMath.BarLook
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bars of a week (spec 5.29): the height of a day against the week and the scale, the least visible fill, the look of a day. */
class WeekBarsMathTest {
    private val eps = 1e-4f

    @Test
    fun `a short week is measured against the scale of 90 minutes`() {
        assertEquals(0.5f, WeekBarsMath.share(minutes = 45, weekMax = 60, scaleMinutes = 90), eps)
    }

    @Test
    fun `a week longer than the scale is measured against its longest day`() {
        assertEquals(45f / 120f, WeekBarsMath.share(minutes = 45, weekMax = 120, scaleMinutes = 90), eps)
        assertEquals(1f, WeekBarsMath.share(minutes = 120, weekMax = 120, scaleMinutes = 90), eps, "the longest day fills its bar")
    }

    @Test
    fun `a day without time has no share`() {
        assertEquals(0f, WeekBarsMath.share(minutes = 0, weekMax = 120, scaleMinutes = 90))
        assertEquals(0f, WeekBarsMath.share(minutes = -5, weekMax = 120, scaleMinutes = 90))
    }

    @Test
    fun `a share never passes the whole bar`() {
        assertEquals(1f, WeekBarsMath.share(minutes = 200, weekMax = 120, scaleMinutes = 90), eps, "a day longer than the week told")
    }

    @Test
    fun `a tiny share still shows 4 dp and none shows nothing`() {
        assertEquals(WeekBarsMath.MIN_FILL_DP, WeekBarsMath.fillDp(share = 1f / 480f, barDp = 48f), eps)
        assertEquals(0f, WeekBarsMath.fillDp(share = 0f, barDp = 48f))
        assertEquals(24f, WeekBarsMath.fillDp(share = 0.5f, barDp = 48f), eps)
        assertEquals(32f, WeekBarsMath.fillDp(share = 1f, barDp = 32f), eps, "never above the bar")
    }

    @Test
    fun `a past day with time is filled and one without is a dashed outline`() {
        assertEquals(BarLook.Filled, WeekBarsMath.look(WeekBarDay("пн", 45, WeekDayWhen.Past), running = false))
        assertEquals(BarLook.Missed, WeekBarsMath.look(WeekBarDay("ср", 0, WeekDayWhen.Past), running = false))
    }

    @Test
    fun `today without time is still today — the day is not over`() {
        assertEquals(BarLook.Today, WeekBarsMath.look(WeekBarDay("вс", 0, WeekDayWhen.Today), running = false))
    }

    @Test
    fun `today while a practice runs is hatched`() {
        assertEquals(BarLook.TodayRunning, WeekBarsMath.look(WeekBarDay("вс", 45, WeekDayWhen.Today), running = true))
        assertEquals(BarLook.TodayRunning, WeekBarsMath.look(WeekBarDay("вс", 0, WeekDayWhen.Today), running = true))
    }

    @Test
    fun `a day to come is an empty track even with minutes`() {
        assertEquals(BarLook.Future, WeekBarsMath.look(WeekBarDay("пт", 0, WeekDayWhen.Future), running = false))
        assertEquals(BarLook.Future, WeekBarsMath.look(WeekBarDay("пт", 30, WeekDayWhen.Future), running = true))
    }
}
