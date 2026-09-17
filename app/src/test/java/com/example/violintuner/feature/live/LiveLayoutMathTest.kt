package com.example.violintuner.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLayoutMathTest {
    private val statusAndSpacing = 64f + 24f

    @Test
    fun `base screen keeps the handoff sizes`() {
        // 412 x 892 portrait: the indicator block gets roughly 412 x 560
        assertEquals(300f, LiveLayoutMath.ringDiameter(300f, 364f, 560f, statusAndSpacing), 0f)
        assertEquals(1f, LiveLayoutMath.noteScale(300f), 0f)
    }

    @Test
    fun `small phone shrinks the ring to the free height`() {
        // 320 x 568: about 300 dp are left for the indicator block
        val ring = LiveLayoutMath.ringDiameter(300f, 272f, 300f, statusAndSpacing)
        assertEquals(212f, ring, 0f)
        assertEquals(212f / 300f, LiveLayoutMath.noteScale(ring), 1e-6f)
    }

    @Test
    fun `narrow screen shrinks the ring to the free width`() {
        assertEquals(250f, LiveLayoutMath.ringDiameter(300f, 250f, 700f, statusAndSpacing), 0f)
    }

    @Test
    fun `ring never grows beyond the design nor collapses`() {
        assertEquals(300f, LiveLayoutMath.ringDiameter(300f, 2_000f, 2_000f, 0f), 0f)
        assertEquals(96f, LiveLayoutMath.ringDiameter(300f, 400f, 120f, statusAndSpacing), 0f)
    }

    @Test
    fun `landscape ring is bigger but the note is not enlarged`() {
        assertEquals(320f, LiveLayoutMath.designRing(landscape = true, tuning = false, noMic = false), 0f)
        assertEquals(1f, LiveLayoutMath.noteScale(320f), 0f)
    }

    @Test
    fun `design ring per state`() {
        assertEquals(300f, LiveLayoutMath.designRing(landscape = false, tuning = false, noMic = false), 0f)
        assertEquals(280f, LiveLayoutMath.designRing(landscape = false, tuning = true, noMic = false), 0f)
        assertEquals(200f, LiveLayoutMath.designRing(landscape = true, tuning = true, noMic = true), 0f)
    }

    @Test
    fun `orientation follows the shape of the screen`() {
        assertTrue(LiveLayoutMath.isLandscape(892f, 412f))
        assertFalse(LiveLayoutMath.isLandscape(412f, 892f))
        assertFalse(LiveLayoutMath.isLandscape(600f, 600f))
    }
}
