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
        assertEquals((2.6f + 2.2f + 1.4f + 2.8f) * 10.8f + 100f, portrait.badgeWidth(100f), EPSILON)
        assertEquals(portrait.badgeLeft + (2.6f + 2.2f + 1.4f) * 10.8f, portrait.badgeTextX, EPSILON)
        // the spinner: its line inside its 2.2 u
        assertEquals(portrait.badgeLeft + (2.6f + 1.1f) * 10.8f, portrait.spinnerCenterX, EPSILON)
        assertEquals((2.2f - 0.45f) / 2 * 10.8f, portrait.spinnerRadius, EPSILON)
    }

    @Test
    fun `the line of the app follows the badge to the inset of the right edge`() {
        val badge = portrait.badgeWidth(200f)
        assertEquals(portrait.badgeLeft + badge + 2.4f * 10.8f, portrait.appLineLeft(badge), EPSILON)
        assertEquals(1_080f - 5 * 10.8f, portrait.appLineLeft(badge) + portrait.appLineMaxWidth(badge), EPSILON)
        assertEquals(1_920f - 4 * 10.8f, landscape.appLineLeft(badge) + landscape.appLineMaxWidth(badge), EPSILON)
    }

    @Test
    fun `the tag is wider than its name by the sign and the number`() {
        val name = 50f
        val number = 30f
        val arrowLine = portrait.tagLineWidth(name, number, inTune = false)
        assertEquals(name + (1.4f + 2.6f + 0.8f) * 10.8f + number, arrowLine, EPSILON)
        // the dot of in tune is narrower than the arrow
        assertEquals(arrowLine - 1f * 10.8f, portrait.tagLineWidth(name, number, inTune = true), EPSILON)
        assertTrue(portrait.tagWidth(arrowLine) > portrait.tagWidth(name))
        // the line is centred on the playhead: the number ends where the line does
        assertEquals(portrait.headX + arrowLine / 2, portrait.tagNumberX(arrowLine, name, inTune = false) + number, EPSILON)
        assertEquals(portrait.headX - arrowLine / 2 + name + 1.4f * 10.8f, portrait.tagSignX(arrowLine, name), EPSILON)
    }

    @Test
    fun `the opening stands at the top — lower in a portrait`() {
        assertEquals(30 * 10.8f, portrait.openingScrimHeight, EPSILON)
        assertEquals(24 * 10.8f, landscape.openingScrimHeight, EPSILON)
        assertEquals(9 * 10.8f, portrait.openingTitleTop, EPSILON)
        assertEquals(6 * 10.8f, landscape.openingTitleTop, EPSILON)
        assertEquals(portrait.openingTitleTop + (6f + 1.6f) * 10.8f, portrait.openingDateTop, EPSILON)
        // by the baseline: the em box's top at 9 u, its share above the baseline under it
        assertEquals(9 * 10.8f + 6 * 10.8f * 0.78f, portrait.openingTitleBaseline(0.78f), EPSILON)
        assertEquals(portrait.openingDateTop + 3.4f * 10.8f * 0.78f, portrait.openingDateBaseline(0.78f), EPSILON)
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
        val top = 300f
        assertEquals(top + head + 9 * 10.8f, portrait.rowTop(top, 1), EPSILON)
        assertEquals(portrait.rowTop(top, 1) + (4.5f + 1.3f) * 10.8f, portrait.rowBaseline(top, 1), EPSILON)
        assertEquals(top + (4.4f + 3f + 17f + 4.4f + 4f) * 10.8f, portrait.stripTop(top), EPSILON)
    }

    @Test
    fun `the signature stands 6 u above the bottom — 4 u lying — as high as its icon at the least`() {
        val icon = 7 * 10.8f
        assertEquals(icon, portrait.signatureHeight(3.9f * 10.8f), EPSILON)
        assertEquals(2 * 3.9f * 10.8f, portrait.signatureHeight(2 * 3.9f * 10.8f), EPSILON)
        assertEquals(1_920f - 6 * 10.8f - icon, portrait.signatureTop(icon), EPSILON)
        assertEquals(1_080f - 4 * 10.8f - icon, landscape.signatureTop(icon), EPSILON)
        // the icon, its gap and the text are no wider than the column, and centred together
        assertEquals(portrait.columnWidth, icon + 2 * 10.8f + portrait.signatureTextMaxWidth, EPSILON)
        val text = 400f
        assertEquals(1_080f - (portrait.signatureTextLeft(text) + text), portrait.iconLeft(text), EPSILON)
    }

    @Test
    fun `the summary is centred above its signature and never touches it`() {
        for (geometry in listOf(portrait, landscape)) {
            for (lines in 1..2) {
                val signature = geometry.signatureHeight(lines * 3.9f * geometry.u)
                for (rows in 1..3) {
                    val top = geometry.summaryTop(rows, signature)
                    val bottom = top + geometry.summaryHeight(rows)
                    // the gap of 4 u at the least, and as much air above the block as between it and the gap
                    assertTrue(bottom + 4 * geometry.u <= geometry.signatureTop(signature) + EPSILON, "rows $rows, lines $lines")
                    assertEquals(top, geometry.signatureTop(signature) - 4 * geometry.u - bottom, EPSILON)
                    assertTrue(top > 0f)
                }
            }
        }
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
