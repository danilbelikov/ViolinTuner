package com.violinjourney.app.feature.live.venue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueFramingTest {
    private fun unitAt(f: Framing, y: Float) = f.top + y / f.scale

    @Test
    fun `upright the room is seen by its width and the ring stands on the window and the music stand`() {
        // the Live screen of the 412 × 892 frame: above the tab bar, below the status bar
        val f = VenueFraming.of(PictureKind.ROOM, 412f, 788f, landscape = false)
        assertEquals(412f / 397f, f.scale, 1e-4f)
        assertEquals(110f, unitAt(f, 788f * VenueFraming.RING_AT), 0.5f)
        // the handoff's frame starts at −260 with the tab bar under the picture; ours is near
        assertTrue(f.top in -270f..-220f)
        assertEquals(206f, f.left + 412f / f.scale / 2, 1e-3f)
    }

    @Test
    fun `a small phone sees the room whole across and a little lower`() {
        val f = VenueFraming.of(PictureKind.ROOM, 360f, 536f, landscape = false)
        assertTrue(f.top in -190f..-140f)
        assertTrue(unitAt(f, 536f) < 600f)
    }

    @Test
    fun `a hall is seen closer - its rows are the point - and never past its boards or its ceiling`() {
        val f = VenueFraming.of(PictureKind.HALL, 412f, 788f, landscape = false)
        assertEquals(412f / 305f, f.scale, 1e-4f)
        assertTrue(f.top >= -240f)
        assertTrue(unitAt(f, 788f) <= 480f)
        assertEquals(100f, unitAt(f, 788f * VenueFraming.RING_AT), 0.5f)
    }

    @Test
    fun `lying down the room covers the screen with the wall, the window and the edge of the desk`() {
        val f = VenueFraming.of(PictureKind.ROOM, 892f, 412f, landscape = true)
        assertEquals(412f / 190f, f.scale, 1e-3f)
        assertEquals(10f, f.top, 0.5f)
        assertEquals(200f, unitAt(f, 412f), 0.5f)
        val hall = VenueFraming.of(PictureKind.HALL, 892f, 412f, landscape = true)
        assertEquals(-60f, hall.top, 0.5f)
    }

    @Test
    fun `a very tall screen is zoomed rather than shown past the floor`() {
        val f = VenueFraming.of(PictureKind.ROOM, 412f, 1400f, landscape = false)
        assertTrue(unitAt(f, 1400f) <= 600f + 1e-3f)
        assertTrue(f.top >= -420f)
    }
}
