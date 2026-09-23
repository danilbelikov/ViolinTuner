package com.violinjourney.app.feature.practice.components

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartButtonMathTest {
    private val lights = StartButtonMath.LIGHTS.indices

    @Test
    fun `the centre of every light stays on the button`() {
        for (index in lights) {
            var t = 0f
            while (t < TEN_MINUTES) {
                val centre = StartButtonMath.centreAt(index, t)
                assertTrue("light $index at $t s: x ${centre.x}", centre.x in 0f..1f)
                assertTrue("light $index at $t s: y ${centre.y}", centre.y in 0f..1f)
                t += STEP
            }
        }
    }

    @Test
    fun `no light crosses a button faster than the limit, however wide the button`() {
        for (widthDp in listOf(LANDSCAPE_DP, PORTRAIT_DP, TABLET_DP)) {
            val pace = StartButtonMath.paceFor(widthDp)
            for (index in lights) {
                var t = 0f
                var fastest = 0f
                var before = StartButtonMath.centreAt(index, 0f)
                while (t < TEN_MINUTES) {
                    t += STEP
                    val now = StartButtonMath.centreAt(index, t * pace)
                    val dp = hypot((now.x - before.x) * widthDp, (now.y - before.y) * HEIGHT_DP)
                    fastest = maxOf(fastest, dp / STEP)
                    before = now
                }
                assertTrue("light $index on $widthDp dp: $fastest dp/s", fastest <= StartButtonMath.MAX_SPEED_DP_S)
            }
        }
    }

    @Test
    fun `the lights keep moving on a phone, a wide button slows its clock`() {
        assertEquals(1f, StartButtonMath.paceFor(LANDSCAPE_DP), 0f)
        assertEquals(1f, StartButtonMath.paceFor(PORTRAIT_DP), 0f)
        assertEquals(0.5f, StartButtonMath.paceFor(StartButtonMath.REFERENCE_WIDTH_DP * 2), 0.0001f)
    }

    @Test
    fun `the pace eases to a stop over the ease time and back as softly, never past its target`() {
        var pace = 1f
        var spent = 0f
        while (pace > 0f) {
            pace = StartButtonMath.eased(pace, target = 0f, dtSeconds = FRAME)
            spent += FRAME
            assertTrue(pace >= 0f)
        }
        assertEquals(StartButtonMath.EASE_S, spent, FRAME)
        assertEquals(0.5f, StartButtonMath.eased(0f, target = 1f, dtSeconds = StartButtonMath.EASE_S / 2), 0.0001f)
        assertEquals(1f, StartButtonMath.eased(0.9f, target = 1f, dtSeconds = 1f), 0f)
    }

    @Test
    fun `at rest the lights stand apart — periwinkle, pearl and rose from left to right`() {
        val (periwinkle, rose, pearl) = lights.map { StartButtonMath.centreAt(it, StartButtonMath.REST_SECONDS) }
        assertTrue(periwinkle.x < pearl.x && pearl.x < rose.x)
        assertTrue(rose.x - periwinkle.x > 0.3f)
    }

    private companion object {
        const val TEN_MINUTES = 600f
        const val STEP = 0.01f
        const val FRAME = 1f / 60
        const val HEIGHT_DP = 56f
        const val LANDSCAPE_DP = 248f
        const val PORTRAIT_DP = 379.4f
        const val TABLET_DP = 528f
    }
}
