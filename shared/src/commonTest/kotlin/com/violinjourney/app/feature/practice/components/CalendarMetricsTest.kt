package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The circle of a day in its column (spec 5.29 R2): Ø 40 (landscape 38) wherever the ring «выбран» keeps off the next circle. */
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

    private companion object {
        /** A gap of 2 in the colour of the ground and a ring of 2 outside the circle of the selected day (5.29 R2). */
        val RING_OUTSIDE = 4.dp
        const val EPSILON = 0.001f
    }
}
