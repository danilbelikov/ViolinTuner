package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «Записать дубль» keeps one line beside «Видео-дубль» in the left column of 300 lying (the review of stage 109): the button is
 * 268 − 56 − 10 = 202 wide, its words have 202 − 2 · 24 − (14 + 8) − 1 = 131. Widths — Manrope 800 with the tracking of
 * `labelLarge`: «Записать дубль» 137.1 at 17 sp, 133.1 at 16.5, 129.2 at 16; «Record a take» 114.7 at 17; «Enregistrer une prise»
 * 157.5 at 15.
 */
class ButtonFitTest {
    private fun widths(at17: Float): (Float) -> Float = { sizeSp -> at17 * sizeSp / 17f }

    @Test
    fun `the words step down to the largest size at which they stand in one line`() {
        val asked = mutableListOf<Float>()
        val size = ButtonFit.size(131f, maxSp = 17f, minSp = 15f) { sizeSp -> asked += sizeSp; widths(137.1f)(sizeSp) }
        assertEquals(16f, size)
        assertEquals(listOf(17f, 16.5f, 16f), asked)
    }

    @Test
    fun `words that fit keep the size of their style`() {
        assertEquals(17f, ButtonFit.size(131f, maxSp = 17f, minSp = 15f, widthAt = widths(114.7f)))
    }

    @Test
    fun `below the least size they do not go`() {
        assertEquals(15f, ButtonFit.size(131f, maxSp = 17f, minSp = 15f, widthAt = widths(178.2f)))
        assertEquals(15f, ButtonFit.size(131f, maxSp = 15f, minSp = 15f, widthAt = widths(178.2f)), "the key of 48 is 15 sp already")
    }
}
