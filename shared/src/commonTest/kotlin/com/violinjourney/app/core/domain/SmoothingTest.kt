package com.violinjourney.app.core.domain

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.Test

class SmoothingTest {
    private val config = IntonationConfig()

    @Test
    fun `median ignores up to two outliers in a window of five`() {
        val median = MedianFilter(5)
        listOf(69.0, 69.0, 69.0).forEach(median::add)
        assertEquals(69.0, median.add(81.0), EPS)
        assertEquals(69.0, median.add(81.0), EPS)
    }

    @Test
    fun `median of a partly filled window`() {
        val median = MedianFilter(5)
        assertEquals(1.0, median.add(1.0), EPS)
        assertEquals(2.0, median.add(3.0), EPS)
        assertEquals(3.0, median.add(5.0), EPS)
    }

    @Test
    fun `median window slides`() {
        val median = MedianFilter(3)
        listOf(1.0, 2.0, 3.0).forEach(median::add)
        assertEquals(4.0, median.add(4.0).let { median.add(5.0) }, EPS)
    }

    @Test
    fun `the median sorted in place matches sorting a copy`() {
        val random = Random(5)
        for (window in 1..7) {
            val median = MedianFilter(window)
            val seen = ArrayList<Double>()
            repeat(2_000) { index ->
                if (index == 1_000) {
                    median.reset()
                    seen.clear()
                }
                // few distinct values, so ties are common
                val value = random.nextInt(-6, 7) / 2.0
                seen += value
                val last = seen.takeLast(window).sorted()
                val mid = last.size / 2
                val expected = if (last.size % 2 == 1) last[mid] else (last[mid - 1] + last[mid]) / 2
                assertEquals(expected, median.add(value), 0.0, "window $window, value $index")
            }
        }
    }

    @Test
    fun `ema starts at the first sample and converges`() {
        val ema = EmaFilter(0.3)
        assertEquals(10.0, ema.add(10.0), EPS)
        assertEquals(13.0, ema.add(20.0), EPS)
        var last = 0.0
        repeat(50) { last = ema.add(20.0) }
        assertEquals(20.0, last, 1e-6)
    }

    @Test
    fun `smoother passes a constant through unchanged`() {
        val smoother = PitchSmoother(config)
        repeat(10) { assertEquals(69.02, smoother.add(69.02), EPS) }
    }

    @Test
    fun `smoother starts over after reset`() {
        val smoother = PitchSmoother(config)
        repeat(10) { smoother.add(69.0) }
        smoother.reset()
        assertEquals(71.0, smoother.add(71.0), EPS)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
