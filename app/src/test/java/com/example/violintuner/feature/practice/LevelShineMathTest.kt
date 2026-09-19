package com.example.violintuner.feature.practice

import com.example.violintuner.feature.practice.components.LevelShineMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelShineMathTest {
    @Test
    fun `an empty bar and a sliver of a fill carry no shine`() {
        assertNull(LevelShineMath.passOf(0f))
        assertNull(LevelShineMath.passOf(11.9f))
        assertNotNull(LevelShineMath.passOf(12f))
    }

    @Test
    fun `the usual fill gets the full band, the full pass and a long rest`() {
        val pass = LevelShineMath.passOf(236f)!!
        assertEquals(56f, pass.bandDp, 0f)
        assertEquals(292f, pass.travelDp, 0f)
        assertTrue(pass.durationMs in 900..1_200)
        assertEquals(4_000L, pass.durationMs + LevelShineMath.pauseAfter(pass))
    }

    @Test
    fun `a short fill gets a narrower band and a quicker pass, never under half a second`() {
        val pass = LevelShineMath.passOf(19f)!!
        assertEquals(12f, pass.bandDp, 0f)
        assertEquals(500, pass.durationMs)
        val middling = LevelShineMath.passOf(60f)!!
        assertEquals(36f, middling.bandDp, 0.001f)
    }

    @Test
    fun `a full bar never takes longer than the full pass`() {
        assertEquals(1_200, LevelShineMath.passOf(380f)!!.durationMs)
        assertEquals(1_200, LevelShineMath.passOf(900f)!!.durationMs)
    }

    @Test
    fun `the band comes in whole and leaves whole`() {
        val pass = LevelShineMath.passOf(236f)!!
        assertEquals(-56f, pass.bandLeftDp(0f), 0f)
        assertEquals(236f, pass.bandLeftDp(1f), 0f)
        assertEquals(236f, pass.bandLeftDp(1.7f), 0f)
    }
}
