package com.violinjourney.app.feature.history.components

import com.violinjourney.app.core.domain.session.DayCount
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class DailyChartMathTest {
    // 2026-09-17 is a Thursday: the fourteen days before it hold the Mondays of the 7th and the 14th.
    private val today = LocalDate(2026, 9, 17)

    private fun days(vararg counts: Int) = counts.mapIndexed { i, c -> DayCount(today.minus((counts.size - 1 - i).toLong(), DateTimeUnit.DAY), c) }

    @Test
    fun `the strip is a field of 28 — an empty day has no bar - a busy one fills it - one recording is never lower than 3`() {
        assertEquals(28f, DailyChartMath.FIELD, 0f)
        assertNull(DailyChartMath.barHeight(0, top = 4))
        assertEquals(DailyChartMath.FIELD, DailyChartMath.barHeight(4, top = 4)!!, 0f)
        assertEquals(DailyChartMath.FIELD / 2, DailyChartMath.barHeight(6, top = 12)!!, 0f)
        // 28 × 1 / 40 = 0.7: a day with a recording still stands out of the marks of the empty ones
        assertEquals(DailyChartMath.MIN_BAR, DailyChartMath.barHeight(1, top = 40)!!, 0f)
        assertEquals(3f, DailyChartMath.MIN_BAR, 0f)
    }

    @Test
    fun `fourteen bars four apart fill the width exactly — on a narrow card too - never overlapping`() {
        for (width in listOf(348f, 328f, 245f, 200f)) {
            val bar = DailyChartMath.barWidth(width, 14)
            assertEquals(0f, DailyChartMath.barLeft(0, 14, width), 0f)
            assertEquals(width, DailyChartMath.barLeft(13, 14, width) + bar, 0.01f, "the last bar ends at the edge of $width")
            for (i in 1 until 14) {
                assertEquals(DailyChartMath.GAP, DailyChartMath.barLeft(i, 14, width) - (DailyChartMath.barLeft(i - 1, 14, width) + bar), 0.01f, "gap before bar $i on $width")
            }
        }
        // the width of a bar comes from the width of the card: 348 on a phone of 412 — 21.1 (was 17)
        assertEquals((348f - 4f * 13) / 14, DailyChartMath.barWidth(348f, 14), 0.001f)
        assertEquals(0f, DailyChartMath.barWidth(20f, 14), 0f)
    }

    @Test
    fun `Mondays are named under the axis — the last bar is today whatever its weekday`() {
        val fortnight = days(*IntArray(14))
        assertEquals(listOf("2026-09-07", "2026-09-14"), DailyChartMath.labelled(fortnight).map { fortnight[it].date.toString() })
        val endingOnMonday = List(14) { DayCount(LocalDate(2026, 9, 14).minus((13 - it).toLong(), DateTimeUnit.DAY), 0) }
        assertEquals(listOf("2026-09-07"), DailyChartMath.labelled(endingOnMonday).map { endingOnMonday[it].date.toString() })
    }

    @Test
    fun `a Monday of yesterday leaves its label out rather than run into «сегодня»`() {
        // 348 wide: yesterday's bar starts at 12 × (21.14 + 4) ≈ 301.7, «сегодня» (≈ 44) at ≈ 304; a week before — at ≈ 125.7
        val yesterday = DailyChartMath.barLeft(12, 14, 348f)
        assertFalse(DailyChartMath.labelFits(left = yesterday, labelWidth = 32f, todayLeft = 304f))
        assertTrue(DailyChartMath.labelFits(left = DailyChartMath.barLeft(5, 14, 348f), labelWidth = 32f, todayLeft = 304f))
        // four of air before «сегодня»
        assertTrue(DailyChartMath.labelFits(left = 100f, labelWidth = 30f, todayLeft = 134f))
        assertFalse(DailyChartMath.labelFits(left = 100f, labelWidth = 30f, todayLeft = 133.9f))
    }
}
