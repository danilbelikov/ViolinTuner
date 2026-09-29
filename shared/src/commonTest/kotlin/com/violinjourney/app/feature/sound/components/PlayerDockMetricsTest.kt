package com.violinjourney.app.feature.sound.components

import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.DockMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The panel of the player at the bottom of a recording and of «Звук» by the height of the window (spec 3.36.5, 5.29 R5). */
class PlayerDockMetricsTest {
    @Test
    fun `from 700 the panel is the regular one around «play» 56`() {
        for (height in listOf(892.dp, 700.dp)) {
            val metrics = PlayerDockMetrics.of(height)
            assertEquals(DockMetrics(top = 12.dp, side = 16.dp, bottom = 12.dp, button = 56.dp), metrics, "at $height")
            assertFalse(PlayerDockMetrics.compact(height), "at $height")
            assertFalse(metrics.compact, "«play» 56 at $height")
        }
    }

    @Test
    fun `lower than 700 in any turn of the phone the panel is compact around «play» 48`() {
        // 360 × 640 upright, a phone lying 892 × 412 and 640 × 360
        for (height in listOf(699.dp, 640.dp, 412.dp, 360.dp)) {
            val metrics = PlayerDockMetrics.of(height)
            assertEquals(DockMetrics(top = 8.dp, side = 16.dp, bottom = 10.dp, button = 48.dp), metrics, "at $height")
            assertTrue(PlayerDockMetrics.compact(height), "at $height")
            assertTrue(metrics.compact, "the rows of the panel hear it: «play» 48 at $height")
        }
    }
}
