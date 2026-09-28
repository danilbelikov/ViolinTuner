package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** The arc of the ring of the level (spec 5.29): from twelve o'clock, as far as the level has gone. */
class LevelRingMathTest {
    private val eps = 1e-3f

    @Test
    fun `the arc follows the progress of the level`() {
        assertEquals(0f, LevelRingMath.sweep(0f), eps)
        assertEquals(180f, LevelRingMath.sweep(0.5f), eps)
        assertEquals(338.4f, LevelRingMath.sweep(0.94f), eps)
        assertEquals(360f, LevelRingMath.sweep(1f), eps)
    }

    @Test
    fun `a progress out of range is clamped`() {
        assertEquals(360f, LevelRingMath.sweep(1.2f), eps)
        assertEquals(0f, LevelRingMath.sweep(-0.1f), eps)
    }

    @Test
    fun `no number is no arc`() {
        assertEquals(0f, LevelRingMath.sweep(Float.NaN))
    }
}
