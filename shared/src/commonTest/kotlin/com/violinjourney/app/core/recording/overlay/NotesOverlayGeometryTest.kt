package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesOverlayGeometryTest {
    private val config = NotesVideoConfig()
    /** A portrait of 3 : 4 — not tall: the numbers of a portrait. */
    private val portrait = NotesOverlayGeometry(1_080f, 1_440f, config)
    private val landscape = NotesOverlayGeometry(1_920f, 1_080f, config)
    private val square = NotesOverlayGeometry(1_080f, 1_080f, config)

    /** 9 : 16, what a phone films standing: for Shorts, Reels and TikTok (since 0.91). */
    private val tall = NotesOverlayGeometry(1_080f, 1_920f, config)

    @Test
    fun `u is a hundredth of the short side — and the shape picks the layout`() {
        assertEquals(10.8f, portrait.u, EPSILON)
        assertEquals(10.8f, landscape.u, EPSILON)
        assertTrue(portrait.portrait)
        assertFalse(landscape.portrait)
        // a square is laid out as a portrait
        assertTrue(square.portrait)
    }

    @Test
    fun `the lane stands at the bottom — lower and flatter lying`() {
        assertEquals(1_440f - 65 * 10.8f, portrait.scrimTop, EPSILON)
        assertEquals(1_440f - 15.2f * 10.8f, portrait.laneBottom, EPSILON)
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
        assertEquals(1_440f - 5 * 10.8f - 7.2f * 10.8f, portrait.badgeTop, EPSILON)
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
        assertEquals(1_440f - 6 * 10.8f - icon, portrait.signatureTop(icon), EPSILON)
        assertEquals(1_080f - 4 * 10.8f - icon, landscape.signatureTop(icon), EPSILON)
        // the icon, its gap and the text are no wider than the column, and centred together
        assertEquals(portrait.columnWidth, icon + 2 * 10.8f + portrait.signatureTextMaxWidth, EPSILON)
        val text = 400f
        assertEquals(1_080f - (portrait.signatureTextLeft(text) + text), portrait.iconLeft(text), EPSILON)
    }

    @Test
    fun `the summary is centred above its signature and never touches it`() {
        for (geometry in listOf(portrait, landscape, square, tall)) {
            for (lines in 1..2) {
                val signature = geometry.signatureHeight(lines * 3.9f * geometry.u)
                for (rows in 1..3) {
                    val top = geometry.summaryTop(rows, signature)
                    val bottom = top + geometry.summaryHeight(rows)
                    // the gap of 4 u at the least, and as much air above the block as between it and the gap
                    assertTrue(bottom + 4 * geometry.u <= geometry.signatureTop(signature) + EPSILON, "rows $rows, lines $lines")
                    val above = top - (geometry.safe?.top ?: 0f)
                    assertEquals(above, geometry.signatureTop(signature) - 4 * geometry.u - bottom, EPSILON)
                    assertTrue(above > 0f)
                }
            }
        }
    }

    @Test
    fun `a frame half as high again as it is wide is tall — a 3 by 4 portrait a square and a landscape are not`() {
        assertTrue(tall.tall)
        assertTrue(NotesOverlayGeometry(1_080f, 1_620f, config).tall)
        assertFalse(NotesOverlayGeometry(1_080f, 1_619f, config).tall)
        for (geometry in listOf(portrait, square, landscape)) {
            assertFalse(geometry.tall)
            assertNull(geometry.safe)
        }
        assertEquals(Rect(8 * 10.8f, 32 * 10.8f, 1_080f - 18 * 10.8f, 1_920f - 36 * 10.8f), tall.safe)
    }

    /** What 0.91 changes is the tall frame alone: a portrait of 3 : 4, a square and a landscape draw where they drew. */
    @Test
    fun `a 3 by 4 portrait a square and a landscape keep their numbers`() {
        val badge = 200f
        val expected = mapOf(
            portrait to listOf(1_440f - 65 * 10.8f, 1_440f - 15.2f * 10.8f, 5 * 10.8f, 1_440f - 12.2f * 10.8f, 1_080f - 5 * 10.8f, 30 * 10.8f, 9 * 10.8f, 84 * 10.8f, 8 * 10.8f, 1_440f - 6 * 10.8f),
            square to listOf(1_080f - 65 * 10.8f, 1_080f - 15.2f * 10.8f, 5 * 10.8f, 1_080f - 12.2f * 10.8f, 1_080f - 5 * 10.8f, 30 * 10.8f, 9 * 10.8f, 84 * 10.8f, 8 * 10.8f, 1_080f - 6 * 10.8f),
            landscape to listOf(1_080f - 47 * 10.8f, 1_080f - 13.6f * 10.8f, 4 * 10.8f, 1_080f - 11.2f * 10.8f, 1_920f - 4 * 10.8f, 24 * 10.8f, 6 * 10.8f, 84 * 10.8f, (1_920f - 84 * 10.8f) / 2, 1_080f - 4 * 10.8f),
        )
        expected.forEach { (geometry, numbers) ->
            val actual = listOf(
                geometry.scrimTop, geometry.laneBottom, geometry.badgeLeft, geometry.badgeTop, geometry.appLineLeft(badge) + geometry.appLineMaxWidth(badge),
                geometry.openingScrimHeight, geometry.openingTitleTop, geometry.columnWidth, geometry.columnLeft, geometry.signatureBottom,
            )
            numbers.zip(actual).forEachIndexed { index, (want, was) -> assertEquals(want, was, EPSILON, "${geometry.width} × ${geometry.height}, number $index") }
            // the shade of the lane is the top of what the lane draws, as it was
            assertEquals(geometry.scrimTop, geometry.laneBandTop, EPSILON)
            // text centred in the column is centred in the frame
            assertEquals(geometry.width / 2, geometry.centredLeft(100f) + 50f, EPSILON)
        }
    }

    @Test
    fun `a tall frame lifts the lane over the interface below and the badge over the tag`() {
        val u = 10.8f
        assertEquals(1_920f - 36 * u, tall.laneBottom, EPSILON)
        assertEquals(tall.laneBottom - 30 * u, tall.laneTop, EPSILON)
        assertEquals(1_920f - 92 * u, tall.scrimTop, EPSILON)
        // the tag and the playhead keep to the lane as in any portrait; the lane spans the width, the playhead in the middle
        assertEquals(tall.laneTop - 1.2f * u, tall.tagBottom, EPSILON)
        assertEquals(540f, tall.headX, EPSILON)
        assertEquals(9_092L, tall.shownToMs(60_000) - tall.shownFromMs(60_000))
        // the badge 8 u from the left, its bottom 2 u over the top of the tag
        assertEquals(8 * u, tall.badgeLeft, EPSILON)
        assertEquals(tall.tagBottom - tall.tagHeight - 2 * u, tall.badgeTop + tall.badgeHeight, EPSILON)
        // the line of the app stops 18 u short of the right edge: the buttons
        val badge = tall.badgeWidth(200f)
        assertEquals(1_080f - 18 * u, tall.appLineLeft(badge) + tall.appLineMaxWidth(badge), EPSILON)
        // the shade still lies under the badge, and the band Media3 is handed reaches it
        assertTrue(tall.scrimTop <= tall.badgeTop)
        assertEquals(tall.scrimTop, tall.laneBandTop, EPSILON)
    }

    @Test
    fun `a tall frame lowers the opening and lifts the signature into the safe zone`() {
        val u = 10.8f
        assertEquals(34 * u, tall.openingTitleTop, EPSILON)
        assertEquals(52 * u, tall.openingScrimHeight, EPSILON)
        assertEquals(1_920f - 36 * u, tall.signatureBottom, EPSILON)
        // the column keeps its left edge of 8 u and stops at the buttons: 74 u, centred in the safe zone
        assertEquals(8 * u, tall.columnLeft, EPSILON)
        assertEquals(74 * u, tall.columnWidth, EPSILON)
        assertEquals(tall.columnLeft + tall.columnWidth / 2, tall.centredLeft(100f) + 50f, EPSILON)
    }

    /**
     * Everything a tall frame draws is in the safe zone — the lane alone spans the whole width: its notes ride in from under
     * the buttons on the right, the notes still to come. The boxes are the widest each may grow to.
     */
    @Test
    fun `everything a tall frame draws lies inside the safe zone`() {
        for (height in listOf(1_620f, 1_920f, 2_400f)) {
            val g = NotesOverlayGeometry(1_080f, height, config)
            val safe = assertNotNull(g.safe)
            val u = g.u
            val badge = g.badgeWidth(30 * u)
            val tagWidth = g.tagWidth(g.tagLineWidth(nameWidth = 9 * u, numberWidth = 6 * u, inTune = false))
            val signature = g.signatureHeight(2 * g.signatureLineHeight)
            val boxes = mapOf(
                "the lane" to Rect(safe.left, g.laneTop, safe.right, g.laneBottom),
                "the tag" to Rect(g.headX - tagWidth / 2, g.tagBottom - g.tagHeight, g.headX + tagWidth / 2, g.tagBottom),
                "the badge" to Rect(g.badgeLeft, g.badgeTop, g.badgeLeft + badge, g.badgeTop + g.badgeHeight),
                "the line of the app" to Rect(g.appLineLeft(badge), g.badgeCenterY - g.appLineHeight, g.appLineLeft(badge) + g.appLineMaxWidth(badge), g.badgeCenterY + g.appLineHeight),
                "the opening" to Rect(g.columnLeft, g.openingTitleTop - config.openingDropU * u, g.columnLeft + g.columnWidth, g.openingDateTop + config.openingDateU * u),
                "the summary" to Rect(g.columnLeft, g.summaryTop(3, signature), g.columnLeft + g.columnWidth, g.summaryTop(3, signature) + g.summaryHeight(3)),
                "the signature" to Rect(g.iconLeft(g.signatureTextMaxWidth), g.signatureTop(signature), g.iconLeft(g.signatureTextMaxWidth) + g.columnWidth, g.signatureBottom),
            )
            // the playhead hangs its 1.5 u under the lane, as in any portrait: into the margin over the interface, not under it
            assertEquals(safe.bottom + 1.5f * u, g.playheadBottom, EPSILON)
            boxes.forEach { (name, box) ->
                assertTrue(
                    box.left >= safe.left - EPSILON && box.top >= safe.top - EPSILON && box.right <= safe.right + EPSILON && box.bottom <= safe.bottom + EPSILON,
                    "$name $box out of the safe zone $safe of 1080 × $height",
                )
            }
        }
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
