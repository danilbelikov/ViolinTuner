package com.violinjourney.app.core.ui.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The status switch never breaks a word (spec 3.36.4): «слова не сокращаются: не помещаются — сегмент растёт до двух строк».
 * Widths in dp from Manrope 700 (PIL, the wght axis at 700, 14 sp plus the tracking 0.1 sp of `labelLarge`; the font 1.3 by the curve
 * of Android 14 — 14 sp are 18.8 dp); the rooms around a label are those of the regular switch — 14 at the ends, 12 in the middle.
 */
class SegmentFitTest {
    private val around = listOf(14f, 12f, 14f)
    private val slack = 1f

    /** Labels measured at 14 sp as (line, widest word), scaled with the size. */
    private fun at14(vararg labels: Pair<Float, Float>): (Float) -> List<SegmentFit.Label> = { sizeSp ->
        labels.map { (line, word) -> SegmentFit.Label(line * sizeSp / 14f, word * sizeSp / 14f) }
    }

    // «Разбираю · Учу · В репертуаре»
    private val ruAt10 = at14(70.4f to 70.4f, 25.2f to 25.2f, 95.9f to 84.1f)
    private val ruAt13 = at14(94.5f to 94.5f, 33.9f to 33.9f, 128.8f to 113.0f)

    // «Déchiffrage · En travail · Au répertoire» at the font 1.0
    private val frAt10 = at14(81.6f to 81.6f, 64.2f to 44.2f, 92.3f to 71.0f)

    private fun plan(room: Float, share: SegmentFit.Share, labelsAt: (Float) -> List<SegmentFit.Label>) =
        SegmentFit.plan(room, around, slack, maxSp = 14f, share = share, labelsAt = labelsAt)

    private fun assertAddsUp(room: Float, plan: SegmentFit.Plan) = assertTrue(abs(plan.widths.sum() - room) < 0.01f, "the widths fill the row: ${plan.widths}")

    @Test
    fun `equal thirds stay while each holds its widest word`() {
        // 412 × 892: the column of 380
        val plan = plan(380f, SegmentFit.Share.Equal, ruAt10)
        assertEquals(14f, plan.sizeSp)
        assertEquals(List(3) { 380f / 3 }, plan.widths)
    }

    @Test
    fun `on 360 at the font 1_3 a third no longer holds «репертуаре» and the steps share the row by their words`() {
        // the review of stage 109: 328 / 3 = 109.3 — «репертуаре» with its room needs 128 and broke by the letter
        assertTrue(328f / 3 < 113.0f + 14f + 1f, "the equal third would break the word")
        val plan = plan(328f, SegmentFit.Share.Equal, ruAt13)
        assertEquals(14f, plan.sizeSp)
        val labels = ruAt13(14f)
        plan.widths.forEachIndexed { i, width -> assertTrue(width >= labels[i].line + around[i] + slack, "step $i stands in one line: $width") }
        assertAddsUp(328f, plan)
    }

    @Test
    fun `by the words a single word stands whole and the label of two words wraps at its space`() {
        // landscape, the column of 268: the old weights by the whole lines cut «Déchiffrage» to 77.6 dp
        val plan = plan(268f, SegmentFit.Share.ByWords, frAt10)
        assertEquals(14f, plan.sizeSp)
        val labels = frAt10(14f)
        assertTrue(plan.widths[0] >= labels[0].line + around[0] + slack, "«Déchiffrage» whole: ${plan.widths}")
        assertTrue(plan.widths[1] >= labels[1].line + around[1] + slack, "«En travail» in one line: ${plan.widths}")
        assertTrue(plan.widths[2] >= labels[2].word + around[2] + slack, "«répertoire» whole: ${plan.widths}")
        assertTrue(plan.widths[2] < labels[2].line + around[2] + slack, "«Au / répertoire» on two lines: ${plan.widths}")
        assertAddsUp(268f, plan)
    }

    @Test
    fun `where the row holds every label in one line each gets its line and the rest in proportion`() {
        val plan = plan(500f, SegmentFit.Share.ByWords, frAt10)
        val lines = frAt10(14f).mapIndexed { i, label -> label.line + around[i] + slack }
        plan.widths.forEachIndexed { i, width -> assertEquals(lines[i] / lines.sum(), width / 500f, 0.0001f, "in proportion") }
    }

    @Test
    fun `the labels that need the least more for one line get it first`() {
        // least 40 + 40 + 40 = 120 with the rooms and 15 to spare; one line costs 5 more for the first and 30 for the third
        val labels: (Float) -> List<SegmentFit.Label> = { listOf(SegmentFit.Label(30f, 25f), SegmentFit.Label(20f, 20f), SegmentFit.Label(55f, 25f)) }
        val plan = SegmentFit.plan(135f, listOf(14f, 19f, 14f), slack = 1f, maxSp = 14f, share = SegmentFit.Share.ByWords, labelsAt = labels)
        assertTrue(plan.widths[0] >= 45f, "the first in one line: ${plan.widths}")
        assertTrue(plan.widths[2] < 70f, "the third wraps at its space: ${plan.widths}")
        assertAddsUp(135f, plan)
    }

    @Test
    fun `where the widest words do not fit all the labels step down together`() {
        val asked = mutableListOf<Float>()
        // the words fit at 13 and not before
        val labels: (Float) -> List<SegmentFit.Label> = { sizeSp ->
            asked += sizeSp
            val word = if (sizeSp > 13f) 90f else 80f
            List(3) { SegmentFit.Label(word, word) }
        }
        val plan = SegmentFit.plan(290f, around, slack, maxSp = 14f, share = SegmentFit.Share.Equal, labelsAt = labels)
        assertEquals(listOf(14f, 13.5f, 13f), asked)
        assertEquals(13f, plan.sizeSp)
        assertAddsUp(290f, plan)
    }

    @Test
    fun `below the least size they do not go and share the row by their widest words`() {
        val labels: (Float) -> List<SegmentFit.Label> = { listOf(SegmentFit.Label(200f, 200f), SegmentFit.Label(100f, 100f), SegmentFit.Label(200f, 200f)) }
        val plan = SegmentFit.plan(268f, around, slack, maxSp = 14f, share = SegmentFit.Share.ByWords, labelsAt = labels)
        assertEquals(SegmentFit.MIN_SP, plan.sizeSp)
        assertEquals(plan.widths[0], plan.widths[2], 0.001f)
        assertTrue(plan.widths[0] > plan.widths[1])
        assertAddsUp(268f, plan)
    }
}
