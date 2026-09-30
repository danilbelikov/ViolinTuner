package com.violinjourney.app.feature.live.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «Игра | Настройка» in its row (spec 5.29 R6): the segment of the wider word at 15 sp with 16 on either side and not under 106,
 * standing in the middle. Upright a word too wide steps down to 12 sp, then its padding gives; only then the switcher leaves the
 * middle, and never reaches under the touch of the gear. Lying down the words keep the size the column gives them and the switcher
 * stands in the middle of the column where it fits there at it. The words are measured by HarfBuzz in Manrope 700 at 15 sp (font
 * 1.0): «Настройка» 80.4, «Tune» 36.0; a size and a font scale scale them.
 */
class SwitcherFitTest {
    private val tuningRu = 80.4f
    private val tuneEn = 36.0f

    /** The rooms of the row upright on a phone [width] wide: the row is 12 in from the sides, the gear's touch 48 at its end. */
    private fun upright(width: Float, word: Float, fontScale: Float = 1f): SwitcherFit.Plan {
        val row = width - 2 * 12f
        return plan(centered = row - 2 * 48f, beside = row - 48f, word = word, fontScale = fontScale)
    }

    /**
     * The right column of landscape [column] wide: the gear's touch of 48 reaches 44 into it, 4 over its edge, and the middle keeps
     * as much free on the other side. The words keep their size.
     */
    private fun lying(column: Float, word: Float, fontScale: Float = 1f): SwitcherFit.Plan =
        plan(centered = column - 2 * 44f, beside = column - 44f, word = word, fontScale = fontScale, keepSize = true)

    private fun plan(centered: Float, beside: Float, word: Float, fontScale: Float = 1f, keepSize: Boolean = false) = SwitcherFit.plan(
        centered = centered,
        beside = beside,
        inset = 4f,
        minSegment = 106f,
        padding = 16f,
        minPadding = 8f,
        keepSize = keepSize,
    ) { sizeSp -> word * sizeSp / 15f * fontScale }

    /** The width of the switcher of [plan]: its two segments and the inset on either side. */
    private fun width(plan: SwitcherFit.Plan) = 2 * plan.segment + 2 * 4f

    private fun assertPlan(sizeSp: Float, segment: Float, centered: Boolean, plan: SwitcherFit.Plan) {
        assertEquals(sizeSp, plan.sizeSp, "the size of the words")
        assertEquals(segment, plan.segment, 0.001f, "the segment")
        assertEquals(centered, plan.centered, "in the middle")
    }

    @Test
    fun `on the base screen the words stand at 15 in the middle`() {
        assertPlan(sizeSp = 15f, segment = 112.4f, centered = true, plan = upright(412f, tuningRu))
    }

    @Test
    fun `a short word keeps the segment of 106`() {
        assertPlan(sizeSp = 15f, segment = 106f, centered = true, plan = upright(412f, tuneEn))
    }

    @Test
    fun `on a narrow phone the least segment gives way to the touch of the gear`() {
        val plan = upright(320f, tuneEn)
        assertEquals(15f, plan.sizeSp)
        assertTrue(plan.centered)
        // the row of 296 keeps 48 for the gear's touch on either side: 200 for the switcher
        assertEquals(96f, plan.segment, 0.001f)
        assertEquals(200f, width(plan), 0.001f)
    }

    @Test
    fun `a word a little too wide steps down by half a point only as far as it must`() {
        // 412 at the font 1.5: «Настройка» is 120.6 at 15 sp; with 16 on either side it takes 144.6 at 14 sp and 140.5 at 13.5 —
        // the 142 of a segment in the middle holds 13.5, not the 12 of the last step
        assertPlan(sizeSp = 13.5f, segment = tuningRu * 13.5f / 15f * 1.5f + 32f, centered = true, plan = upright(412f, tuningRu, fontScale = 1.5f))
    }

    @Test
    fun `a word too wide steps down to 12 before the switcher leaves the middle`() {
        // 360 at the font 1.3: «Настройка» 104.5 at 15 sp, 83.6 at 12 in the 116 of a segment
        val plan = upright(360f, tuningRu, fontScale = 1.3f)
        assertEquals(12f, plan.sizeSp)
        assertTrue(plan.centered)
        assertTrue(plan.segment <= 116f && plan.segment >= tuningRu * 12f / 15f * 1.3f + 32f - 0.001f, "segment ${plan.segment}")
    }

    @Test
    fun `the padding gives before the switcher leaves the middle`() {
        // 320 at the font 1.0: at 12 sp «Настройка» is 64.3 — with 16 on either side it misses the 96 of a segment by a hair
        assertPlan(sizeSp = 12f, segment = 96f, centered = true, plan = upright(320f, tuningRu))
    }

    @Test
    fun `where not even that holds the word in the middle the switcher moves up to the gear and never under it`() {
        // 320 at the font 1.5: «Настройка» at 12 sp is 96.5 — more than the 96 of a segment in the middle, even with no padding
        val plan = upright(320f, tuningRu, fontScale = 1.5f)
        assertFalse(plan.centered)
        assertEquals(12f, plan.sizeSp)
        assertTrue(width(plan) <= 296f - 48f, "the switcher of ${width(plan)} stays clear of the gear's touch at 248")
        assertTrue(plan.segment - tuningRu * 12f / 15f * 1.5f >= 2 * 8f, "the word keeps 8 on either side")
    }

    @Test
    fun `in the right column of landscape the switcher stands in its middle at the full size`() {
        // 892 x 412: a column of 460, its middle 372 clear of the gear's touch on either side — on the axis of the word and the key
        assertPlan(sizeSp = 15f, segment = 112.4f, centered = true, plan = lying(460f, tuningRu))
    }

    @Test
    fun `in a narrow column of landscape 106 gives way to the middle and the words do not`() {
        // 640 x 360 with a cutout: a window of about 603, a column of about 300.6, its middle 212.6 — a segment of 102.3 there.
        // «Tune» with its padding takes 68: the segments give up their least 106 and the switcher stands in the middle at 15 sp
        assertPlan(sizeSp = 15f, segment = (212.6f - 8f) / 2, centered = true, plan = lying(300.6f, tuneEn))
        // «Настройка» takes 112.4 — the middle would step it down to 13, and the controls do not shrink (spec 3.36.6): it keeps 15 sp
        // and stands off the middle, as near to it as the gear's touch lets it (start)
        assertPlan(sizeSp = 15f, segment = 112.4f, centered = false, plan = lying(300.6f, tuningRu))
    }

    @Test
    fun `in a narrow column of landscape the words step down only where the column does not hold them`() {
        // the same column at the font 1.5: «Настройка» is 120.6 at 15 sp, 96.5 at 12 — only 12 with the least padding holds
        val plan = lying(300.6f, tuningRu, fontScale = 1.5f)
        assertEquals(12f, plan.sizeSp)
        assertFalse(plan.centered)
        assertTrue(width(plan) <= 300.6f - 44f + 0.001f, "the switcher of ${width(plan)} stays clear of the gear's touch")
    }

    @Test
    fun `a switcher in the middle stands in the middle of its row`() {
        // 892 x 412: a column of 460 — the switcher of 235 at 15 sp stands in its middle (live.html, 5), on the axis of the word and
        // the key, not at the start of the column
        assertEquals((460 - 235) / 2, SwitcherFit.start(row = 460, switcher = 235, gearStart = 416, centered = true))
    }

    @Test
    fun `off the middle it stands as near to it as the touch of the gear lets it`() {
        // 603 x 336, ru: the switcher of 235 at 15 sp in a column of 300 whose gear's touch begins at 256 — its end there, 11 off
        // the middle and not at the start of the column
        assertEquals(256 - 235, SwitcherFit.start(row = 300, switcher = 235, gearStart = 256, centered = false))
        // a row with no room at all keeps the switcher at its start, the word cut at its end
        assertEquals(0, SwitcherFit.start(row = 200, switcher = 190, gearStart = 152, centered = false))
    }

    @Test
    fun `a row with no room at all gives the least size and cuts the word at its end`() {
        assertPlan(sizeSp = 12f, segment = 26f, centered = false, plan = plan(centered = 20f, beside = 60f, word = tuningRu, fontScale = 2f))
    }
}
