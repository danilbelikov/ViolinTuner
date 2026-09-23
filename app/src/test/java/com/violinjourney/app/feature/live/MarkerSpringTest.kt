package com.violinjourney.app.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerSpringTest {
    private fun spring(start: Float = 0.5f) = MarkerSpring(dampingRatio = 0.8f, stiffness = 600f, start = start)

    @Test
    fun `it reaches a target that stands still, quickly and without flying past it far`() {
        val spring = spring()
        var farthest = 0.5f
        repeat(30) {
            spring.advance(0.8f, 16f)
            farthest = maxOf(farthest, spring.position)
        }
        assertEquals(0.8f, spring.position, 0.002f)
        assertTrue("overshoot was ${farthest - 0.8f}", farthest < 0.8f + 0.3f * 0.03f)
        assertTrue(spring.isAtRest(0.8f))
    }

    @Test
    fun `a target that moves on every frame is followed closely - the reason this class exists`() {
        val spring = spring()
        // A drift of 12 cents a second on a ±50 cent scale, a new target each 16 ms frame.
        var target = 0.5f
        repeat(120) {
            target += 0.12f * 0.016f
            spring.advance(target, 16f)
        }
        assertTrue("lag was ${target - spring.position}", target - spring.position < 0.02f)
        assertFalse(spring.isAtRest(target + 0.1f))
    }

    @Test
    fun `a long frame is as good as several short ones`() {
        val fine = spring()
        repeat(25) { fine.advance(0.9f, 4f) }
        val coarse = spring()
        coarse.advance(0.9f, 100f)
        assertEquals(fine.position, coarse.position, 1e-4f)
    }

    @Test
    fun `snapping is instant and leaves no motion behind`() {
        val spring = spring()
        spring.advance(1f, 30f)
        spring.snapTo(0.2f)
        assertEquals(0.2f, spring.position, 0f)
        spring.advance(0.2f, 50f)
        assertEquals(0.2f, spring.position, 0f)
    }

    @Test
    fun `no time, no move`() {
        val spring = spring()
        spring.advance(1f, 0f)
        assertEquals(0.5f, spring.position, 0f)
    }
}
