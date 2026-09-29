package com.violinjourney.app.feature.session

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class PianoRollMathTest {
    @Test
    fun `the labels in sight are the slice of all of them from a label before the viewport to its end`() {
        val short = PianoRollMath(durationMs = 13_500, rowCount = 3, viewportWidth = 300f)
        assertEquals(short.tickTimesMs().filter { short.x(it) <= 300f }, short.visibleTickTimesMs(0f))

        val hour = PianoRollMath(durationMs = 3_600_000, rowCount = 8, viewportWidth = 300f)
        for (scroll in listOf(0f, 79.5f, 1_234f, 30_000f, hour.maxScroll)) {
            val expected = hour.tickTimesMs().filter { hour.x(it) >= scroll - PianoRollMath.MIN_TICK_SPACING && hour.x(it) <= scroll + 300f }
            assertEquals(expected, hour.visibleTickTimesMs(scroll), "scrolled to $scroll")
            assertTrue(expected.size in 2..3, "${expected.size} labels at $scroll")
        }
    }

    @Test
    fun `a short session stretches to the viewport and does not scroll`() {
        val math = PianoRollMath(durationMs = 5_000, rowCount = 3, viewportWidth = 300f)
        assertEquals(300f, math.contentWidth, EPS)
        assertEquals(0f, math.maxScroll, EPS)
        assertEquals(150f, math.x(2_500), EPS)
    }

    @Test
    fun `a long session keeps 30 dp per second and scrolls`() {
        val math = PianoRollMath(durationMs = 760_000, rowCount = 8, viewportWidth = 300f)
        assertEquals(22_800f, math.contentWidth, 0.5f)
        assertEquals(22_500f, math.maxScroll, 0.5f)
        assertEquals(30f, math.x(1_000), EPS)
    }

    @Test
    fun `bars have a minimum width so that short notes can be tapped`() {
        val math = PianoRollMath(durationMs = 600_000, rowCount = 1, viewportWidth = 300f)
        assertEquals(6f, math.barWidth(1_000, 1_100), EPS)
        assertEquals(60f, math.barWidth(1_000, 3_000), EPS)
    }

    @Test
    fun `height follows the rows up to twelve — then the card scrolls inside`() {
        assertEquals(3 * 27f + 4f, PianoRollMath(1_000, 3, 300f).viewportHeight, EPS)
        val many = PianoRollMath(1_000, 20, 300f)
        assertEquals(12 * 27f + 4f, many.viewportHeight, EPS)
        assertEquals(20 * 27f + 4f, many.contentHeight, EPS)
    }

    @Test
    fun `contour goes up for sharp and is clamped at 40 cents`() {
        val math = PianoRollMath(1_000, 1, 300f)
        assertEquals(10f, math.contourY(0.0), EPS)
        assertEquals(10f - 4.4f, math.contourY(20.0), EPS)
        assertEquals(1.2f, math.contourY(90.0), EPS)
        assertEquals(18.8f, math.contourY(-90.0), EPS)
    }

    @Test
    fun `time labels use round steps at least 80 dp apart`() {
        assertEquals(listOf(0L, 5_000L, 10_000L), PianoRollMath(12_000, 1, 300f).tickTimesMs().take(3)) // 30 dp/s
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), PianoRollMath(3_000, 1, 300f).tickTimesMs()) // 100 dp/s
        assertEquals(0L, PianoRollMath(0, 0, 300f).tickTimesMs().single())
    }

    @Test
    fun `tap finds the bar in its row — with a little slop — and nothing elsewhere`() {
        val math = PianoRollMath(durationMs = 600_000, rowCount = 3, viewportWidth = 300f)
        val bars = listOf(Triple(0, 1_000L, 2_000L), Triple(2, 1_500L, 1_600L))
        assertEquals(0, math.hitTest(x = 45f, y = 13f, bars))
        assertEquals(1, math.hitTest(x = 46f, y = 2 * 27f + 5f, bars))
        assertEquals(1, math.hitTest(x = 55f, y = 2 * 27f + 5f, bars)) // 6 dp wide bar + slop
        assertNull(math.hitTest(x = 45f, y = 27f + 5f, bars)) // the empty row between
        assertNull(math.hitTest(x = 200f, y = 13f, bars))
    }

    @Test
    fun `the roll follows the playback cursor only when it leaves the middle band`() {
        val math = PianoRollMath(durationMs = 600_000, rowCount = 1, viewportWidth = 300f) // 30 dp/s
        assertNull(math.scrollToFollow(cursorMs = 5_000, scroll = 0f)) // at 150 of 300
        assertEquals(190f, math.scrollToFollow(cursorMs = 8_333, scroll = 0f)!!, 0.5f) // past 80 %: back to 20 %
        assertEquals(0f, math.scrollToFollow(cursorMs = 1_000, scroll = 500f)!!, EPS) // sought back to the start
        assertEquals(math.maxScroll, math.scrollToFollow(cursorMs = 600_000, scroll = 0f)!!, EPS)
        assertNull(PianoRollMath(5_000, 1, 300f).scrollToFollow(2_500, 0f)) // nothing to scroll
    }

    @Test
    fun `a note of «Что уходит» in view is left where it is and one out of view comes to a fifth of the viewport`() {
        val math = PianoRollMath(durationMs = 600_000, rowCount = 3, viewportWidth = 300f) // 30 dp/s
        assertNull(math.scrollToShow(startMs = 2_000, endMs = 4_000, scroll = 0f), "60…120 of 0…300")
        // off to the right: its start at 20 % — 60 dp — of the viewport
        assertEquals(20 * 30f - 60f, math.scrollToShow(startMs = 20_000, endMs = 22_000, scroll = 0f)!!, EPS)
        // off to the left, scrolled past it
        assertEquals(10 * 30f - 60f, math.scrollToShow(startMs = 10_000, endMs = 11_000, scroll = 1_000f)!!, EPS)
        // cut by the right edge is not in view: 280…340
        assertEquals(280f - 60f, math.scrollToShow(startMs = 9_333, endMs = 11_333, scroll = 0f)!!, 0.5f)
        // near the start and near the end the scroll stops where the roll does
        assertEquals(0f, math.scrollToShow(startMs = 1_000, endMs = 2_000, scroll = 500f)!!, EPS)
        assertEquals(math.maxScroll, math.scrollToShow(startMs = 599_000, endMs = 600_000, scroll = 0f)!!, EPS)
        // a short recording does not scroll at all
        assertNull(PianoRollMath(5_000, 1, 300f).scrollToShow(4_000, 5_000, 0f))
    }

    @Test
    fun `a row out of the twelve in view comes to the middle of the card and one in view stays`() {
        val math = PianoRollMath(durationMs = 60_000, rowCount = 20, viewportWidth = 300f)
        val viewport = math.viewportHeight // 12 rows and the bottom padding
        assertNull(math.rowScrollToShow(row = 3, scroll = 0f))
        assertNull(math.rowScrollToShow(row = 11, scroll = 0f), "the twelfth row is the last in view")
        val middle = 13 * 27f - (viewport - 27f) / 2
        assertEquals(middle, math.rowScrollToShow(row = 13, scroll = 0f)!!, EPS)
        // the last rows stop at the end of the rows, the first at their start
        assertEquals(math.contentHeight - viewport, math.rowScrollToShow(row = 19, scroll = 0f)!!, EPS)
        assertEquals(0f, math.rowScrollToShow(row = 1, scroll = 200f)!!, EPS)
        // twelve rows or fewer never scroll
        assertNull(PianoRollMath(60_000, 5, 300f).rowScrollToShow(row = 4, scroll = 0f))
    }

    private companion object {
        const val EPS = 0.001f
    }
}
