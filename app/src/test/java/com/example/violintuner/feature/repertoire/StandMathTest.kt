package com.example.violintuner.feature.repertoire

import com.example.violintuner.feature.repertoire.stand.StandMath
import com.example.violintuner.feature.repertoire.stand.StandZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandMathTest {
    @Test
    fun `the left third goes back, the right third goes on, the middle is the panel`() {
        assertEquals(StandZone.PREVIOUS, StandMath.zoneOf(10f, 412f, zoomed = false))
        assertEquals(StandZone.PREVIOUS, StandMath.zoneOf(137f, 412f, zoomed = false))
        assertEquals(StandZone.PANEL, StandMath.zoneOf(138f, 412f, zoomed = false))
        assertEquals(StandZone.PANEL, StandMath.zoneOf(274f, 412f, zoomed = false))
        assertEquals(StandZone.NEXT, StandMath.zoneOf(275f, 412f, zoomed = false))
        assertEquals(StandZone.NEXT, StandMath.zoneOf(411f, 412f, zoomed = false))
    }

    @Test
    fun `a zoomed page does not turn, wherever it is tapped`() {
        assertEquals(StandZone.PANEL, StandMath.zoneOf(10f, 412f, zoomed = true))
        assertEquals(StandZone.PANEL, StandMath.zoneOf(400f, 412f, zoomed = true))
    }

    @Test
    fun `the first and the last sheet have nowhere to turn to`() {
        assertEquals(1, StandMath.target(current = 0, count = 3, zone = StandZone.NEXT))
        assertEquals(1, StandMath.target(current = 2, count = 3, zone = StandZone.PREVIOUS))
        assertNull(StandMath.target(current = 0, count = 3, zone = StandZone.PREVIOUS))
        assertNull(StandMath.target(current = 2, count = 3, zone = StandZone.NEXT))
        assertNull(StandMath.target(current = 0, count = 1, zone = StandZone.NEXT))
        assertNull(StandMath.target(current = 1, count = 3, zone = StandZone.PANEL))
    }

    @Test
    fun `a pinch that ends next to one is not a zoom`() {
        assertFalse(StandMath.isZoomed(1f))
        assertFalse(StandMath.isZoomed(1.01f))
        assertTrue(StandMath.isZoomed(1.5f))
        assertEquals(1f, StandMath.clampScale(0.4f), 0f)
        assertEquals(4f, StandMath.clampScale(9f), 0f)
    }

    @Test
    fun `a sheet can be dragged only as far as its own edge`() {
        assertEquals(0f, StandMath.clampOffset(50f, scale = 1f, size = 400f), 0f)
        assertEquals(200f, StandMath.clampOffset(900f, scale = 2f, size = 400f), 0f)
        assertEquals(-200f, StandMath.clampOffset(-900f, scale = 2f, size = 400f), 0f)
        assertEquals(30f, StandMath.clampOffset(30f, scale = 2f, size = 400f), 0f)
    }

    @Test
    fun `a double tap keeps the tapped point under the finger, as far as the edges allow`() {
        assertEquals(0f, StandMath.offsetToKeep(tap = 200f, size = 400f, scale = 2f), 0f)
        assertEquals(100f, StandMath.offsetToKeep(tap = 100f, size = 400f, scale = 2f), 0f)
        assertEquals(-200f, StandMath.offsetToKeep(tap = 400f, size = 400f, scale = 2f), 0f)
        assertEquals(0f, StandMath.offsetToKeep(tap = 100f, size = 400f, scale = 1f), 0f)
    }

    @Test
    fun `an unzoomed sheet is decoded at half the stored size, a zoomed one in full`() {
        assertEquals(2, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = 936))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = 1836))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = Int.MAX_VALUE))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 600, wantedWidth = 936))
        assertEquals(4, StandMath.sampleSize(sourceWidth = 4000, wantedWidth = 1000))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 0, wantedWidth = 936))
    }
}
