package com.violinjourney.app.feature.history.components

import com.violinjourney.app.core.domain.session.DayCount
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChartMathTest {
    // 2026-09-17 is a Thursday: the fourteen days before it hold the Mondays of the 7th and the 14th.
    private val today = LocalDate.of(2026, 9, 17)

    private fun days(vararg counts: Int) = counts.mapIndexed { i, c -> DayCount(today.minusDays((counts.size - 1 - i).toLong()), c) }

    @Test
    fun `an empty day has no bar, a busy one fills the plot, one recording is still seen`() {
        assertNull(DailyChartMath.barHeight(0, top = 4))
        assertEquals(DailyChartMath.BAR_MAX, DailyChartMath.barHeight(4, top = 4)!!, 0f)
        assertEquals(DailyChartMath.BAR_MAX / 2, DailyChartMath.barHeight(6, top = 12)!!, 0f)
        assertTrue(DailyChartMath.barHeight(1, top = 40)!! >= DailyChartMath.EMPTY_MARK * 2)
    }

    @Test
    fun `the number stands over the busiest day, over each of equals, over none when nothing was recorded`() {
        assertEquals(setOf(2), DailyChartMath.numbered(days(1, 0, 3, 2)))
        assertEquals(setOf(0, 3), DailyChartMath.numbered(days(3, 0, 1, 3)))
        assertEquals(emptySet<Int>(), DailyChartMath.numbered(days(0, 0, 0)))
    }

    @Test
    fun `Mondays are named under the axis, the last bar is today whatever its weekday`() {
        val fortnight = days(*IntArray(14))
        assertEquals(listOf("2026-09-07", "2026-09-14"), DailyChartMath.labelled(fortnight).map { fortnight[it].date.toString() })
        val endingOnMonday = List(14) { DayCount(LocalDate.of(2026, 9, 14).minusDays((13 - it).toLong()), 0) }
        assertEquals(listOf("2026-09-07"), DailyChartMath.labelled(endingOnMonday).map { endingOnMonday[it].date.toString() })
    }

    @Test
    fun `bars run from edge to edge and never overlap, on a narrow card too`() {
        for (width in listOf(348f, 328f, 200f)) {
            val bar = DailyChartMath.barWidth(width, 14)
            assertEquals(0f, DailyChartMath.barLeft(0, 14, width), 0f)
            assertEquals(width, DailyChartMath.barLeft(13, 14, width) + bar, 0.01f)
            assertTrue(DailyChartMath.barLeft(1, 14, width) - DailyChartMath.barLeft(0, 14, width) > bar)
        }
        assertEquals(DailyChartMath.BAR_WIDTH, DailyChartMath.barWidth(400f, 14), 0f)
    }
}
