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

    /**
     * «Без тональности · Готово» of the sheet «Тональность» (the review of stage 110): on 360 each half has 155 − 2 · 16 − 1 = 122 for
     * its widest word; «тональности» (Manrope 700, the tracking of `labelLarge`) is 127.2 at 19.5 dp — 15 sp at the font 1.3 — so both
     * step down together until it fits: 14.5 sp gives 123, 14 gives 118.7.
     */
    @Test
    fun `buttons side by side step down together until the widest word of each fits its half`() {
        val tonality = { sizeSp: Float -> 127.2f * sizeSp / 15f }
        val done = { sizeSp: Float -> 79.8f * sizeSp / 15f }
        val asked = mutableListOf<Float>()
        val size = ButtonFit.sharedSize(maxSp = 15f, minSp = 13f) { sizeSp ->
            asked += sizeSp
            maxOf(tonality(sizeSp) - 122f, done(sizeSp) - 122f)
        }
        assertEquals(14f, size)
        assertEquals(listOf(15f, 14.5f, 14f), asked)
    }

    @Test
    fun `buttons whose words fit keep the size of their style`() {
        assertEquals(15f, ButtonFit.sharedSize(maxSp = 15f, minSp = 13f) { -1f })
        assertEquals(15f, ButtonFit.sharedSize(maxSp = 15f, minSp = 13f) { 0f }, "a word as wide as its room fits")
    }

    @Test
    fun `where not even the least size keeps a word whole there is no size and the buttons stack`() {
        val asked = mutableListOf<Float>()
        assertEquals(null, ButtonFit.sharedSize(maxSp = 15f, minSp = 13f) { sizeSp -> asked += sizeSp; 1f })
        assertEquals(listOf(15f, 14.5f, 14f, 13.5f, 13f), asked)
        // at the least size exactly it still fits
        assertEquals(13f, ButtonFit.sharedSize(maxSp = 15f, minSp = 13f) { sizeSp -> if (sizeSp <= 13f) 0f else 1f })
    }
}

