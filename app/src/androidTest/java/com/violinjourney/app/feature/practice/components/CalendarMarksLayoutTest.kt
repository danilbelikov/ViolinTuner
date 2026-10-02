package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.testing.TestWindow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The marks of the events in a cell of the calendar (spec 3.36.9, 5.29 R9 «Клетка») where they are drawn, by their pixels: the plate —
 * here painted a red no other part of the grid has — lies inside its own cell, in the middle of it, three mini signs and «+» on it never
 * reach the next one, its top is 38 dp under the top of the cell (36 lying) and its bottom is the bottom of the cell, and the number of
 * the day stays clear above it. On 412, 360 (a column of 47 — signs of 9) and 320 (41 — signs of 8); lying, the grid of 350 at the
 * density of the emulator and of the owner's phone (2.625: cells of 132 and 131 px) and the column of 640 × 360 at 2.0 (cells of 87 and
 * 88 px) — one size of marks in the whole grid, whatever pixels a cell gets. The grid is laid out in a window of its own size
 * ([TestWindow]), at the density named.
 */
@RunWith(AndroidJUnit4::class)
class CalendarMarksLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * A week of September, Monday 14 to Sunday 20: every other day — the 14th, 16th, 18th and 20th — with three events and more, on the
     * fourth tone (the accent circle under the plate); the days between without events — so a plate that reached a neighbour would show
     * there. The 14th stands in the first column, which a row gives a pixel more than the others.
     */
    private fun week(): List<CalendarCell?> = (14..20).map { day ->
        val marked = day % 2 == 0
        CalendarCell(
            date = LocalDate(2026, 9, day), totalMs = 100 * MS_PER_MINUTE, fillLevel = 4, isToday = false, isSelected = false, isFuture = false,
            marks = if (marked) MARKS else emptyList(), more = marked,
        )
    }

    /** The grid in a window [width] wide at [density] (the device's when null): the fields of 16, [grid] wide or as wide as the row. */
    private fun show(width: Dp, metrics: CalendarMetrics, density: Float?, grid: Dp?) {
        compose.setContent {
            ViolinTheme {
                TestWindow(DpSize(width, 892.dp), density = density) {
                    // the fields of the screen, 16 at each side, as on «Занятия»; black around the grid, the plate red
                    Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 16.dp)) {
                        val size = if (grid != null) Modifier.width(grid) else Modifier
                        MonthGrid(week(), metrics, onDaySelected = {}, modifier = size.testTag(GRID), ground = PLATE)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The red of the plate, its edges blended into what is around them left out. */
    private fun isPlate(pixels: PixelMap, x: Int, y: Int): Boolean {
        val color = pixels[x, y]
        return color.red > RED_FROM && color.green < OTHER_BELOW && color.blue < OTHER_BELOW
    }

    /**
     * In every cell the red of its own column: a marked day has its whole plate there — [plate] wide, three signs and «+» of the size
     * the grid has, in the middle of the cell, from [CalendarMetrics.marksTop] under the top of the cell to its bottom, under the number —
     * and a day without events has none: a plate that reached a neighbour would be cut at its own edge and show in the neighbour's
     * column.
     */
    private fun assertMarksStayInTheirCells(
        width: Dp,
        plate: Dp,
        metrics: CalendarMetrics = CalendarMetrics.Portrait,
        density: Float? = null,
        grid: Dp? = null,
    ) {
        show(width, metrics, density, grid)
        val node = compose.onNodeWithTag(GRID)
        val pixels = node.captureToImage().toPixelMap()
        // the pixels of the window laid out at its own density: the bounds of the nodes come in them
        with(Density(density ?: compose.density.density)) {
            val origin = node.fetchSemanticsNode().boundsInRoot.let { it.left to it.top }
            val cells = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
                .map { cell -> cell.boundsInRoot.translate(-origin.first, -origin.second) }
                .sortedBy { it.left }
            assertEquals("seven days in the week", 7, cells.size)
            val what = "$width at ${this.density}"
            cells.forEachIndexed { index, cell ->
                val day = 14 + index
                var left = Int.MAX_VALUE
                var right = Int.MIN_VALUE
                var top = Int.MAX_VALUE
                var bottom = Int.MIN_VALUE
                for (x in cell.left.toInt().coerceAtLeast(0) until cell.right.toInt().coerceAtMost(pixels.width)) {
                    for (y in cell.top.toInt().coerceAtLeast(0) until cell.bottom.toInt().coerceAtMost(pixels.height)) {
                        if (isPlate(pixels, x, y)) {
                            left = minOf(left, x)
                            right = maxOf(right, x)
                            top = minOf(top, y)
                            bottom = maxOf(bottom, y)
                        }
                    }
                }
                if (day % 2 != 0) {
                    assertTrue("$what: no plate reaches the $day, a day without events: red from $left to $right px", left > right)
                    return@forEachIndexed
                }
                assertTrue("$what: the plate of the $day is there", left <= right)
                // cut at the edge of the cell it would be less; of another size than the grid's — more or less
                assertEquals("$what: the plate of the $day in its cell of ${cell.width} px is $plate", plate.toPx(), (right - left + 1).toFloat(), EDGE * 2)
                assertEquals("$what: the plate of the $day in the middle of its cell", (cell.left + cell.right) / 2, (left + right + 1) / 2f, EDGE)
                assertEquals("$what: the plate of the $day ends at the bottom of its cell", cell.bottom, bottom + 1f, EDGE)
                assertEquals(
                    "$what: the plate of the $day begins ${metrics.marksTop} under the top of its cell", cell.top + metrics.marksTop.toPx(), top.toFloat(), EDGE,
                )
                val number = compose.onAllNodesWithText(day.toString(), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
                assertTrue("$what: the number of the $day ends above the plate", number.bottom - origin.second <= top + EDGE)
            }
        }
    }

    @Test
    fun onA412ScreenThreeMarksAndThePlusStayInTheirCell() = assertMarksStayInTheirCells(412.dp, plate = 48.dp)

    @Test
    fun onA360ScreenTheSmallerMarksStayInTheirCell() = assertMarksStayInTheirCells(360.dp, plate = 42.5.dp)

    @Test
    fun onA320ScreenTheSmallestMarksStayInTheirCell() = assertMarksStayInTheirCells(320.dp, plate = 36.dp)

    /**
     * 892 × 412 lying on the emulator and on the owner's Pixel 10a (2.625): the grid of 350 is 919 px, its cells 132, 132 and 131 px —
     * 50.3 and 49.9 dp. The column of the grid is 50: every plate is of the signs of 10 (48 dp), the 14th in the first column and the
     * 16th, 18th and 20th alike, in the middle of their cells; the top of the plate 36 under the top of a row of 50 (the review of stage
     * 97: a cell chose its own size, and the plates of one grid were 48 and 42.5).
     */
    @Test
    fun lyingTheGridOf350HasMarksOfOneSizeAtTheDensityOfThePhone() =
        assertMarksStayInTheirCells(412.dp, plate = 48.dp, metrics = CalendarMetrics.Landscape, density = PHONE_DENSITY, grid = 350.dp)

    /**
     * 640 × 360 lying (the window of 603 × 308 at 2.0): the grid as wide as its column, 307 dp — 614 px, cells of 87, 87 and 88 px (43.5
     * and 44 dp). The column of the grid is 43.9: every plate is of the signs of 8 (36 dp).
     */
    @Test
    fun lyingTheGridOf640By360HasMarksOfOneSize() =
        assertMarksStayInTheirCells(339.dp, plate = 36.dp, metrics = CalendarMetrics.Landscape, density = SMALL_DENSITY, grid = 307.dp)

    private companion object {
        const val GRID = "grid"

        /** The plate of the marks in the test: a colour no other part of the grid has. */
        val PLATE = Color(0xFFFF0000)

        /** Three kinds of the mockups — «Другое», «Оркестр», «Мастер-класс» — and «+» for a fourth. */
        val MARKS = listOf(KindLook(KindSign.OTHER, 7), KindLook(KindSign.ARC, 1), KindLook(KindSign.BOLT, 4))
        const val RED_FROM = 0.8f
        const val OTHER_BELOW = 0.25f

        /** Pixels a dp on the emulator (Pixel_7, 420 dpi) and on the owner's Pixel 10a; and of 640 × 360 at 320 dpi. */
        const val PHONE_DENSITY = 2.625f
        const val SMALL_DENSITY = 2f

        /** Pixels a blended edge may take or give. */
        const val EDGE = 1.5f
    }
}
