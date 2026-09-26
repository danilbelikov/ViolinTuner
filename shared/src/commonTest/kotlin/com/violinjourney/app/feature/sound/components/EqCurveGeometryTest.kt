package com.violinjourney.app.feature.sound.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where the bubble «частота · усиление» over a dragged point stands — in a curve 900 × 440 px, lifted 150 px. */
class EqCurveGeometryTest {
    private val width = 900
    private val height = 440
    private val lift = 150f
    private val aside = 80f
    private val bubble = 300

    private fun at(x: Float, y: Float) = EqCurveGeometry.bubbleAt(Offset(x, y), bubble, width, height, lift, aside)

    @Test
    fun `with room above the bubble stands centred over the finger`() {
        assertEquals(IntOffset(450 - bubble / 2, 250), at(450f, 400f))
    }

    @Test
    fun `at either edge it is pushed back into the curve`() {
        assertEquals(IntOffset(width - bubble, 250), at(880f, 400f), "«Воздух» far right")
        assertEquals(IntOffset(0, 250), at(20f, 400f), "the low cut far left")
    }

    @Test
    fun `a point held high puts the bubble at the top beside the finger - on the side with more room`() {
        val left = at(200f, 60f)
        assertEquals(0, left.y)
        assertTrue(left.x > 200, "beside the finger to its right: ${left.x}")
        val right = at(700f, 60f)
        assertEquals(0, right.y)
        assertTrue(right.x + bubble < 700, "beside the finger to its left: ${right.x}")
    }

    @Test
    fun `a finger dragged out of the curve keeps the bubble in it`() {
        val below = at(450f, height + 200f)
        assertEquals(height - lift.toInt(), below.y)
        val beyond = at(width + 300f, -100f)
        assertEquals(0, beyond.y)
        assertTrue(beyond.x in 0..(width - bubble))
    }
}
