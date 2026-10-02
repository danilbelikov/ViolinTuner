package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.feature.events.EventsDimens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The geometry of a cell (spec 5.29 R2, R9): the circle of a day in its column — Ø 40 (landscape 38) wherever the ring «выбран» keeps off
 * the next circle — the marks of its events by the width of the column, and the legend of the kinds beside the grid or under it.
 */
class CalendarMetricsTest {
    /** The column of a portrait screen [width] wide: the fields of 16 on both sides, seven columns. */
    private fun portraitColumn(width: Dp): Dp = (width - 32.dp) / 7

    @Test
    fun `the circle keeps its size on 412 and 360 and in landscape`() {
        assertEquals(40.dp, CalendarMetrics.Portrait.circleIn(portraitColumn(412.dp)))
        assertEquals(40.dp, CalendarMetrics.Portrait.circleIn(portraitColumn(360.dp)), "a column of 47: the ring takes the empty edge of the next cell")
        assertEquals(40.dp, CalendarMetrics.Portrait.circleIn(portraitColumn(340.dp)), "a column of 44: the ring just touches the next circle")
        assertEquals(38.dp, CalendarMetrics.Landscape.circleIn(350.dp / 7), "the grid of 350")
        assertEquals(38.dp, CalendarMetrics.Landscape.circleIn(344.dp / 7), "640 × 360: the grid as wide as its column")
    }

    @Test
    fun `on a narrow screen the circle shrinks so that the ring never reaches the next circle`() {
        for (width in listOf(339.dp, 320.dp, 300.dp)) {
            val column = portraitColumn(width)
            val circle = CalendarMetrics.Portrait.circleIn(column)
            assertTrue(circle < 40.dp, "a screen of $width")
            // from the middle of the day: where its ring ends, and where the circle of the next day begins
            val ringEnd = circle / 2 + RING_OUTSIDE
            val nextCircle = column - circle / 2
            assertTrue(ringEnd.value <= nextCircle.value + EPSILON, "a screen of $width: the ring ends at $ringEnd, the next circle begins at $nextCircle")
        }
    }

    /**
     * The marks of a cell (spec 5.29 R9, «Клетка»): signs of 10 from a column of 50 — 412 gives 54 — of 9 under it (360 — 47), of 8
     * under 44 (320 — 41); three signs and «+» on their plate are 48, 42.5 and 36.
     */
    @Test
    fun `the marks get smaller with the column and three of them and the plus are 48 then 42 and a half then 36`() {
        val wide = CalendarMetrics.Portrait.marksIn(portraitColumn(412.dp))
        assertEquals(MarkSize.Wide, wide)
        assertEquals(MarkSize(10.dp, 2.dp, 3.dp, 6.dp, 10.dp), wide)
        assertEquals(48.dp, wide.plateWidth(signs = 3, more = true))
        val narrow = CalendarMetrics.Portrait.marksIn(portraitColumn(360.dp))
        assertEquals(MarkSize(9.dp, 1.5.dp, 2.5.dp, 6.dp, 10.dp), narrow)
        assertEquals(42.5.dp, narrow.plateWidth(signs = 3, more = true))
        val narrowest = CalendarMetrics.Portrait.marksIn(portraitColumn(320.dp))
        assertEquals(MarkSize(8.dp, 1.dp, 2.dp, 5.dp, 8.dp), narrowest)
        assertEquals(36.dp, narrowest.plateWidth(signs = 3, more = true))
        assertEquals(MarkSize.Wide, CalendarMetrics.Landscape.marksIn(350.dp / 7), "the grid of 350: a column of exactly 50")
    }

    @Test
    fun `the borders of the sizes are 50 and 44 themselves`() {
        assertEquals(MarkSize.Wide, CalendarMetrics.Portrait.marksIn(50.dp))
        assertEquals(MarkSize.Narrow, CalendarMetrics.Portrait.marksIn(49.9.dp))
        assertEquals(MarkSize.Narrow, CalendarMetrics.Portrait.marksIn(44.dp))
        assertEquals(MarkSize.Narrowest, CalendarMetrics.Portrait.marksIn(43.9.dp))
    }

    /**
     * One size for a whole grid (spec 5.29 R9, «Клетка»), by its column — its width over seven — not by the whole pixels a row gives
     * each cell: 350 dp lying at 2.625 is 919 px, cells of 132 and 131 px (50.3 and 49.9 dp), and the grid has marks of 10; the
     * column of 640 × 360 is 614 px at 2.0, cells of 87 and 88 px (43.5 and 44 dp), and the grid has marks of 8. The half pixel the
     * rounding of the grid takes off is given back: 350 dp at 2.6125 is 914 px — 349.9 dp — and still a column of 50.
     */
    @Test
    fun `the marks of a grid are of one size chosen by its column and not by the pixels of a cell`() {
        val lying = CalendarMetrics.Landscape
        assertEquals(MarkSize.Wide, lying.marksOfGrid(gridPx = 919, density = 2.625f), "892 × 412: a grid of 350")
        assertEquals(MarkSize.Narrow, lying.marksIn((131 / 2.625f).dp), "a cell of 131 px alone would take the smaller marks")
        assertEquals(MarkSize.Wide, lying.marksOfGrid(gridPx = 914, density = 2.6125f), "350 dp rounded down to the pixel")
        assertEquals(MarkSize.Wide, lying.marksOfGrid(gridPx = 700, density = 2f), "350 dp at 2.0")
        assertEquals(MarkSize.Narrow, lying.marksOfGrid(gridPx = 918, density = 2.625f), "a pixel less than 350 — 349.7 dp — is under 50")
        assertEquals(MarkSize.Narrowest, lying.marksOfGrid(gridPx = 614, density = 2f), "640 × 360: a column of 43.9")
        assertEquals(MarkSize.Narrow, lying.marksIn((88 / 2f).dp), "a cell of 88 px alone would take the larger marks")
        assertEquals(MarkSize.Wide, CalendarMetrics.Portrait.marksOfGrid(gridPx = 1080 - 84, density = 2.625f), "412 upright: a column of 54")
        assertEquals(MarkSize.Narrow, CalendarMetrics.Portrait.marksOfGrid(gridPx = 1080 - 96, density = 3f), "360 upright: a column of 46.9")
    }

    /**
     * The plate of the marks lies on the bottom of its cell (spec 5.29 R9, «Клетка»): from 38 under the top of the cell — 36 lying —
     * to its bottom, 14 high, in both layouts.
     */
    @Test
    fun `the plate of the marks ends at the bottom of the cell in both layouts`() {
        assertEquals(38.dp, CalendarMetrics.Portrait.marksTop)
        assertEquals(36.dp, CalendarMetrics.Landscape.marksTop)
        for (metrics in listOf(CalendarMetrics.Portrait, CalendarMetrics.Landscape)) {
            assertEquals(metrics.rowHeight, metrics.marksTop + EventsDimens.MarksPlateHeight, "a row of ${metrics.rowHeight}")
        }
    }

    @Test
    fun `a plate holds its signs and the plus between its sides and no more`() {
        val wide = MarkSize.Wide
        assertEquals(16.dp, wide.plateWidth(signs = 1, more = false), "one sign: 10 and 3 at each side")
        assertEquals(28.dp, wide.plateWidth(signs = 2, more = false), "two: 10, 2, 10")
        assertEquals(40.dp, wide.plateWidth(signs = 3, more = false))
        assertEquals(0.dp, wide.plateWidth(signs = 0, more = false), "a day without events has no plate")
    }

    /** On no screen from 300 to 480 dp do three signs and «+» reach the next cell: the plate is never wider than its column. */
    @Test
    fun `three marks and the plus stay in their own column on every screen`() {
        for (width in 300..480) {
            val column = portraitColumn(width.dp)
            val plate = CalendarMetrics.Portrait.marksIn(column).plateWidth(signs = 3, more = true)
            assertTrue(plate <= column, "a screen of $width dp: the plate $plate in a column of $column")
        }
        for (width in 280..350) {
            val column = width.dp / 7
            assertTrue(CalendarMetrics.Landscape.marksIn(column).plateWidth(signs = 3, more = true) <= column, "landscape, a grid of $width dp")
        }
    }

    /**
     * The legend beside the grid (spec 3.36.9, 5.29 R9): in landscape where the row leaves 14 and [EventsDimens.LegendBesideMin] (120)
     * beside the grid of 350 — 892 × 412 gives a right column of 596 — else under the grid, as in portrait (640 × 360 — 344).
     */
    @Test
    fun `the legend stands beside the grid where 14 and 120 are left of the row and under it elsewhere`() {
        val lying = CalendarMetrics.Landscape
        assertTrue(lying.legendBeside(596.dp), "892 × 412")
        assertTrue(lying.legendBeside(580.dp))
        assertTrue(lying.legendBeside(484.dp), "exactly 350 + 14 + 120")
        assertFalse(lying.legendBeside(483.dp))
        assertFalse(lying.legendBeside(344.dp), "640 × 360: the grid takes the whole column")
        assertFalse(CalendarMetrics.Portrait.legendBeside(1000.dp), "portrait: the grid is as wide as the screen, the legend under it")
    }

    private companion object {
        /** A gap of 2 in the colour of the ground and a ring of 2 outside the circle of the selected day (5.29 R2). */
        val RING_OUTSIDE = 4.dp
        const val EPSILON = 0.001f
    }
}
