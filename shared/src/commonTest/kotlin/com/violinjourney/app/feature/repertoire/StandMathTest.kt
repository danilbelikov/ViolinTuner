package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.feature.repertoire.stand.StandMath
import com.violinjourney.app.feature.repertoire.stand.StandZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class StandMathTest {
    @Test
    fun `the left third goes back — the right third goes on — the middle is the panel`() {
        assertEquals(StandZone.PREVIOUS, StandMath.zoneOf(10f, 412f, zoomed = false))
        assertEquals(StandZone.PREVIOUS, StandMath.zoneOf(137f, 412f, zoomed = false))
        assertEquals(StandZone.PANEL, StandMath.zoneOf(138f, 412f, zoomed = false))
        assertEquals(StandZone.PANEL, StandMath.zoneOf(274f, 412f, zoomed = false))
        assertEquals(StandZone.NEXT, StandMath.zoneOf(275f, 412f, zoomed = false))
        assertEquals(StandZone.NEXT, StandMath.zoneOf(411f, 412f, zoomed = false))
    }

    @Test
    fun `a zoomed page does not turn — wherever it is tapped`() {
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
    fun `a double tap keeps the tapped point under the finger — as far as the edges allow`() {
        assertEquals(0f, StandMath.offsetToKeep(tap = 200f, area = 400f, layer = 400f, scale = 2f), 0f)
        assertEquals(100f, StandMath.offsetToKeep(tap = 100f, area = 400f, layer = 400f, scale = 2f), 0f)
        assertEquals(-200f, StandMath.offsetToKeep(tap = 400f, area = 400f, layer = 400f, scale = 2f), 0f)
        assertEquals(0f, StandMath.offsetToKeep(tap = 100f, area = 400f, layer = 400f, scale = 1f), 0f)
    }

    @Test
    fun `a zoomed sheet inside the margins of the stand stops at its own margin`() {
        // the layer is 300 of an area of 400: at 2× its edge comes to where it stood at 1× after 150, not 200
        assertEquals(150f, StandMath.offsetToKeep(tap = 0f, area = 400f, layer = 300f, scale = 2f), 0f)
        assertEquals(50f, StandMath.offsetToKeep(tap = 150f, area = 400f, layer = 300f, scale = 2f), 0f)
        assertEquals(-150f, StandMath.clampOffset(-900f, scale = 2f, size = 300f), 0f)
    }

    @Test
    fun `a pinch keeps the point under the fingers where it is`() {
        // a point of the sheet under the previous centroid, 80 left of the centre, on a sheet at 1.5× moved by 30
        val scale = 1.5f
        val offset = 30f
        val focus = -80f
        val point = (focus - offset) / scale
        val zoomChange = 1.2f
        val after = StandMath.offsetAfterPinch(offset, focus, zoomChange, pan = 0f)
        assertEquals(focus, scale * zoomChange * point + after, 1e-3f)
        // from 1× a pinch to 2× keeps the point as a double tap there does
        assertEquals(StandMath.offsetToKeep(tap = 100f, area = 400f, layer = 400f, scale = 2f), StandMath.offsetAfterPinch(0f, -100f, 2f, pan = 0f), 1e-3f)
        // and then the point follows the fingers
        assertEquals(after + 15f, StandMath.offsetAfterPinch(offset, focus, zoomChange, pan = 15f), 1e-3f)
        // one finger drags: no zoom, only the move
        assertEquals(offset + 15f, StandMath.offsetAfterPinch(offset, focus, 1f, pan = 15f), 1e-3f)
    }

    @Test
    fun `a swipe lights the edge it went through`() {
        val ids = listOf(5L, 6L, 7L)
        assertEquals(StandZone.NEXT, StandMath.edgeOfSettle(ids, fromId = 5L, toId = 6L, byTap = false))
        assertEquals(StandZone.PREVIOUS, StandMath.edgeOfSettle(ids, fromId = 7L, toId = 6L, byTap = false))
        assertEquals(StandZone.NEXT, StandMath.edgeOfSettle(ids, fromId = 5L, toId = 7L, byTap = false))
    }

    @Test
    fun `a deleted page and a tap turn and the first rest light nothing`() {
        // the page 6 was deleted: 7 stands on its place and did not turn in
        assertNull(StandMath.edgeOfSettle(listOf(5L, 7L), fromId = 6L, toId = 7L, byTap = false))
        assertNull(StandMath.edgeOfSettle(listOf(5L, 6L, 7L), fromId = 5L, toId = 6L, byTap = true))
        assertNull(StandMath.edgeOfSettle(listOf(5L, 6L, 7L), fromId = null, toId = 6L, byTap = false))
        assertNull(StandMath.edgeOfSettle(listOf(5L, 6L, 7L), fromId = 6L, toId = 6L, byTap = false))
    }

    @Test
    fun `an unzoomed sheet is decoded at half the stored size — a zoomed one in full`() {
        assertEquals(2, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = 936))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = 1836))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 1920, wantedWidth = Int.MAX_VALUE))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 600, wantedWidth = 936))
        assertEquals(4, StandMath.sampleSize(sourceWidth = 4000, wantedWidth = 1000))
        assertEquals(1, StandMath.sampleSize(sourceWidth = 0, wantedWidth = 936))
    }
}
