package com.example.violintuner.feature.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PianoRollMathTest {
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
    fun `height follows the rows up to twelve, then the card scrolls inside`() {
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
    fun `tap finds the bar in its row, with a little slop, and nothing elsewhere`() {
        val math = PianoRollMath(durationMs = 600_000, rowCount = 3, viewportWidth = 300f)
        val bars = listOf(Triple(0, 1_000L, 2_000L), Triple(2, 1_500L, 1_600L))
        assertEquals(0, math.hitTest(x = 45f, y = 13f, bars))
        assertEquals(1, math.hitTest(x = 46f, y = 2 * 27f + 5f, bars))
        assertEquals(1, math.hitTest(x = 55f, y = 2 * 27f + 5f, bars)) // 6 dp wide bar + slop
        assertNull(math.hitTest(x = 45f, y = 27f + 5f, bars)) // the empty row between
        assertNull(math.hitTest(x = 200f, y = 13f, bars))
    }

    private companion object {
        const val EPS = 0.001f
    }
}
