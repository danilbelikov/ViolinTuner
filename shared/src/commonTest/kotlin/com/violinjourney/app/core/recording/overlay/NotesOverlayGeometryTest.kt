package com.violinjourney.app.core.recording.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotesOverlayGeometryTest {
    private val config = NotesVideoConfig()
    private val portrait = NotesOverlayGeometry(1_080f, 1_920f, config)
    private val landscape = NotesOverlayGeometry(1_920f, 1_080f, config)

    @Test
    fun `u is a hundredth of the short side — and the shape picks the layout`() {
        assertEquals(10.8f, portrait.u, EPSILON)
        assertEquals(10.8f, landscape.u, EPSILON)
        assertTrue(portrait.portrait)
        assertFalse(landscape.portrait)
        // a square is laid out as a portrait
        assertTrue(NotesOverlayGeometry(1_080f, 1_080f, config).portrait)
    }

    @Test
    fun `the lane stands at the bottom — lower and flatter lying`() {
        assertEquals(1_920f - 65 * 10.8f, portrait.scrimTop, EPSILON)
        assertEquals(1_920f - 15.2f * 10.8f, portrait.laneBottom, EPSILON)
        assertEquals(portrait.laneBottom - 30 * 10.8f, portrait.laneTop, EPSILON)
        assertEquals(1_080f - 47 * 10.8f, landscape.scrimTop, EPSILON)
        assertEquals(1_080f - 13.6f * 10.8f, landscape.laneBottom, EPSILON)
        assertEquals(landscape.laneBottom - 18 * 10.8f, landscape.laneTop, EPSILON)
    }

    @Test
    fun `notes ride at 11 u a second past the playhead in the middle`() {
        assertEquals(540f, portrait.headX, EPSILON)
        assertEquals(540f, portrait.x(atMs = 10_000, nowMs = 10_000), EPSILON)
        assertEquals(540f + 11 * 10.8f, portrait.x(atMs = 11_000, nowMs = 10_000), EPSILON)
        assertEquals(540f - 11 * 10.8f / 2, portrait.x(atMs = 9_500, nowMs = 10_000), EPSILON)
        // the end of a capsule is cut short by the gap
        assertEquals(540f - 0.6f * 10.8f, portrait.pillRight(endMs = 10_000, nowMs = 10_000), EPSILON)
    }

    @Test
    fun `the span shown covers the frame from edge to edge`() {
        val now = 60_000L
        assertTrue(portrait.x(portrait.shownFromMs(now), now) <= 0f)
        assertTrue(portrait.x(portrait.shownToMs(now), now) >= portrait.width)
        // about nine seconds on a portrait, sixteen lying
        assertEquals(9_092L, portrait.shownToMs(now) - portrait.shownFromMs(now))
        assertEquals(16_162L, landscape.shownToMs(now) - landscape.shownFromMs(now))
    }

    @Test
    fun `the lowest note sits on the bottom of the lane and the highest under its top`() {
        val pill = 4.4f * 10.8f
        assertEquals(portrait.laneBottom - pill / 2, portrait.pillCenterY(66, 66f, 74f), EPSILON)
        assertEquals(portrait.laneTop + pill / 2, portrait.pillCenterY(74, 66f, 74f), EPSILON)
        assertEquals((portrait.laneBottom + portrait.laneTop) / 2, portrait.pillCenterY(70, 66f, 74f), EPSILON)
    }

    @Test
    fun `a name goes in only with its air`() {
        val air = 3.2f * 10.8f
        assertTrue(portrait.labelFits(labelWidth = 40f, pillWidth = 40f + air))
        assertFalse(portrait.labelFits(labelWidth = 40f, pillWidth = 40f + air - 1f))
    }

    @Test
    fun `the tag is never narrower than its minimum and grows with the name`() {
        assertEquals(10.24f * 10.8f, portrait.tagWidth(20f), EPSILON)
        assertEquals(100f + 4 * 10.8f, portrait.tagWidth(100f), EPSILON)
        assertEquals(portrait.laneTop - 1.2f * 10.8f, portrait.tagBottom, EPSILON)
        assertEquals(portrait.tagBottom, portrait.playheadTop, EPSILON)
        assertEquals(portrait.laneBottom + 1.5f * 10.8f, portrait.playheadBottom, EPSILON)
    }

    @Test
    fun `the badge stands 5 u from the corner — 4 u lying`() {
        assertEquals(5 * 10.8f, portrait.badgeLeft, EPSILON)
        assertEquals(1_920f - 5 * 10.8f - 7.2f * 10.8f, portrait.badgeTop, EPSILON)
        assertEquals(4 * 10.8f, landscape.badgeLeft, EPSILON)
        assertEquals(1_080f - 4 * 10.8f - 7.2f * 10.8f, landscape.badgeTop, EPSILON)
        assertEquals((2.6f + 1.8f + 1.4f + 2.8f) * 10.8f + 100f, portrait.badgeWidth(100f), EPSILON)
        assertEquals(portrait.badgeLeft + (2.6f + 1.8f + 1.4f) * 10.8f, portrait.badgeTextX, EPSILON)
    }

    @Test
    fun `the summary is a column of 84 u in the middle of the frame`() {
        assertEquals(84 * 10.8f, portrait.columnWidth, EPSILON)
        assertEquals((1_080f - 84 * 10.8f) / 2, portrait.columnLeft, EPSILON)
        assertEquals(84 * 10.8f, landscape.columnWidth, EPSILON)
        assertEquals((1_920f - 84 * 10.8f) / 2, landscape.columnLeft, EPSILON)
        // title 4.4 + 3 + score 17 + line 4.4 + 4 + strip 2.2 + 3, then the rows of 9
        val head = (4.4f + 3f + 17f + 4.4f + 4f + 2.2f + 3f) * 10.8f
        assertEquals(head + 3 * 9 * 10.8f, portrait.summaryHeight(3), EPSILON)
        assertEquals((1_920f - portrait.summaryHeight(3)) / 2, portrait.summaryTop(3), EPSILON)
        assertEquals(portrait.summaryTop(2) + head + 9 * 10.8f, portrait.rowTop(2, 1), EPSILON)
        assertEquals(portrait.rowTop(2, 1) + (4.5f + 1.3f) * 10.8f, portrait.rowBaseline(2, 1), EPSILON)
        assertEquals(portrait.summaryTop(3) + (4.4f + 3f + 17f + 4.4f + 4f) * 10.8f, portrait.stripTop(3), EPSILON)
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
