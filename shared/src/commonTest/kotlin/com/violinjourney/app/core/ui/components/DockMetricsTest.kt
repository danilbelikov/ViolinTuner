package com.violinjourney.app.core.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The room of the bottom zone by the height of the window (spec 5.29, «Нижняя зона»). */
class DockMetricsTest {
    @Test
    fun `a tall window has the regular zone of 78`() {
        val metrics = DockMetrics.of(892.dp)
        assertEquals(DockMetrics(top = 10.dp, side = 20.dp, bottom = 12.dp, button = 56.dp), metrics)
        assertEquals(78.dp, metrics.height, "10 + 56 + 12")
        assertFalse(metrics.compact)
    }

    @Test
    fun `a window of exactly 700 is still regular — the spec says lower than 700`() {
        assertEquals(DockMetrics.Regular, DockMetrics.of(700.dp))
    }

    @Test
    fun `a window lower than 700 has the low fields and a zone of 74`() {
        for (height in listOf(699.dp, 640.dp, 412.dp, 361.dp)) {
            val metrics = DockMetrics.of(height)
            assertEquals(DockMetrics(top = 8.dp, side = 16.dp, bottom = 10.dp, button = 56.dp), metrics, "at $height")
            assertEquals(74.dp, metrics.height, "at $height")
            assertFalse(metrics.compact, "the button stays 56 at $height")
        }
    }

    @Test
    fun `a window no higher than 360 has the button of 48 and a zone of 64`() {
        for (height in listOf(360.dp, 320.dp)) {
            val metrics = DockMetrics.of(height)
            assertEquals(DockMetrics(top = 8.dp, side = 16.dp, bottom = 8.dp, button = 48.dp), metrics, "at $height")
            assertEquals(64.dp, metrics.height, "at $height")
            assertTrue(metrics.compact, "at $height")
        }
    }

    @Test
    fun `only the tiny zone is compact`() {
        assertEquals(listOf(false, false, true), listOf(DockMetrics.Regular, DockMetrics.Low, DockMetrics.Tiny).map { it.compact })
    }
}
