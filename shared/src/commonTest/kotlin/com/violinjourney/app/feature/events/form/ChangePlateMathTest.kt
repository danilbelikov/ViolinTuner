package com.violinjourney.app.feature.events.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plate «что меняется» (spec 3.36.9, 5.29 R9; review of stage 98б): what each text needs, at a density of 1 — the widths of Manrope
 * measured by the review: «Преподаватель» 13 sp / 700 — 103, «—» — 12, «Анна Сергеевна» 15 sp / 700 — 123; the arrow 18, 10 apart.
 */
class ChangePlateMathTest {
    @Test
    fun `texts that fit take what they need - a short old value leaves its room to the new one`() {
        // 412: the plate 372 − 2 × 14 = 344 inside
        val plate = ChangePlateMath.layout(needs = listOf(103, 12, 123), least = listOf(103, 12, 72), width = 344, arrow = 18, gap = 10, captioned = true)
        assertFalse(plate.captionAbove)
        assertEquals(listOf(103, 12, 123), plate.widths, "«Анна Сергеевна» whole beside «—», not cut at a half of the row")
    }

    @Test
    fun `texts that do not fit give way in proportion to what they need and never under their longest word`() {
        val widths = ChangePlateMath.shrink(needs = listOf(60, 150, 200), least = listOf(60, 70, 90), room = 300)
        assertEquals(60, widths[0], "the caption at its one word")
        assertTrue(widths[1] >= 70 && widths[2] >= 90, "$widths")
        assertTrue(widths.sum() <= 300, "$widths")
        assertTrue(widths[2] > widths[1], "the longer keeps more: $widths")
        assertEquals(listOf(100, 200), ChangePlateMath.shrink(needs = listOf(100, 200), least = listOf(40, 40), room = 300), "as they are where they fit")
    }

    @Test
    fun `where even the longest words do not fit one row the caption stands over the values`() {
        // 360 at 1.3: the plate 320 − 28 = 292; «Преподаватель» 134, «—» 15, «Анна Сергеевна» 160 with «Сергеевна» 100
        val plate = ChangePlateMath.layout(needs = listOf(134, 15, 160), least = listOf(134, 15, 100), width = 292, arrow = 18, gap = 10, captioned = true)
        assertTrue(plate.captionAbove)
        assertEquals(listOf(134, 15, 160), plate.widths, "the values have the whole row under it")
    }

    @Test
    fun `a date has no caption and its values share the row`() {
        val plate = ChangePlateMath.layout(needs = listOf(120, 130), least = listOf(40, 40), width = 200, arrow = 18, gap = 10, captioned = false)
        assertFalse(plate.captionAbove)
        assertTrue(plate.widths.sum() <= 200 - 18 - 20, "${plate.widths}")
        assertTrue(plate.widths.all { it >= 40 }, "${plate.widths}")
    }
}
