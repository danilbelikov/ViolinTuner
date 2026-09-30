package com.violinjourney.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One size for the four labels of the tab bar (spec 5.29 R1). The widths are those of Manrope 600 (components.html, HarfBuzz):
 * «Enregistrements» — 95.8 dp at 12 sp and 79.9 at 10 sp, «Répertoire» 50.7 and «Репертуар» 52.4 at 10 sp, a width in proportion to
 * the size; the room of an item is 82 dp on a 360-dp screen, 95 on 412 and 72 on 320. At a large font the widths are those of the
 * size drawn: at 1.5 a label of s sp is as wide as one of 1.5 · s at the default font.
 */
class TabLabelsTest {
    private fun enregistrements(sizeSp: Float) = FRENCH_AT_12 * sizeSp / TabLabels.MAX_SP

    private fun repertoireFrench(sizeSp: Float) = REPERTOIRE_FRENCH_AT_10 * sizeSp / TabLabels.MIN_SP

    private fun repertoireRussian(sizeSp: Float, fontScale: Float) = REPERTOIRE_RUSSIAN_AT_10 * sizeSp * fontScale / TabLabels.MIN_SP

    private fun fit(room: Float, full: (Float) -> Float, short: (Float) -> Float = full, leastSp: Float = TabLabels.MIN_SP) =
        TabLabels.fit(room, leastSp, full, short)

    @Test
    fun `labels that fit keep 12 sp`() {
        assertEquals(TabLabels.Fit(12f, short = false), fit(room = 82f, full = { sizeSp -> 66.2f * sizeSp / 12f }))
    }

    @Test
    fun `the French word on 360 dp brings all four to 10 sp`() {
        assertEquals(TabLabels.Fit(10f, short = false), fit(room = 82f, full = ::enregistrements, short = ::repertoireFrench))
    }

    @Test
    fun `the French word on 412 dp stops at 11 and a half sp`() {
        assertEquals(TabLabels.Fit(11.5f, short = false), fit(room = 95f, full = ::enregistrements, short = ::repertoireFrench))
    }

    @Test
    fun `where the whole labels do not fit even at 10 sp the short ones come — from 12 sp`() {
        // French on 320 dp: «Enregistrements» is 79.9 at 10 sp in 72; with «Enreg.» the widest is «Répertoire», 60.8 at 12
        assertEquals(TabLabels.Fit(12f, short = true), fit(room = 72f, full = ::enregistrements, short = ::repertoireFrench))
    }

    @Test
    fun `at the default font nothing goes below 10 sp`() {
        // past 10 sp at the default font the answer is a short word from the translator, not a smaller one: clipped until it comes
        assertEquals(TabLabels.Fit(10f, short = true), fit(room = 60f, full = ::enregistrements))
        assertEquals(TabLabels.Fit(10f, short = true), fit(room = 0f, full = { 1_000f }))
    }

    @Test
    fun `at a large font all four go on down together half a point at a time`() {
        // «Репертуар» on 320 at the font 1.5: 78.6 wide at 10 sp in 72 — whole at 9 sp (70.7), drawn 13.5 dp
        val least = TabLabels.LEAST_DP / 1.5f
        assertEquals(
            TabLabels.Fit(9f, short = true),
            fit(room = 72f, full = { repertoireRussian(it, 1.5f) }, leastSp = least),
        )
    }

    @Test
    fun `at a large font the labels stop at 10 sp of the default font — even off the half points`() {
        val least = TabLabels.LEAST_DP / 1.5f
        // a room only the least size fits: 6.67 sp at 1.5, drawn as 10 at the default font
        val room = REPERTOIRE_RUSSIAN_AT_10 * TabLabels.LEAST_DP / TabLabels.MIN_SP
        assertEquals(TabLabels.Fit(least, short = true), fit(room = room, full = { repertoireRussian(it, 1.5f) }, leastSp = least))
        // and nothing smaller where not even that fits
        assertEquals(TabLabels.Fit(least, short = true), fit(room = room - 1f, full = { repertoireRussian(it, 1.5f) }, leastSp = least))
    }

    @Test
    fun `the sizes go down by half a point to the least one`() {
        assertEquals(listOf(12f, 11.5f, 11f, 10.5f, 10f), TabLabels.sizes(10f))
        assertEquals(listOf(12f, 11.5f, 11f, 10.5f, 10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 20f / 3f), TabLabels.sizes(20f / 3f))
    }

    @Test
    fun `a label exactly as wide as its room fits`() {
        assertEquals(TabLabels.Fit(11f, short = false), fit(room = enregistrements(11f), full = ::enregistrements))
    }

    @Test
    fun `sizes are tried from the largest down by half an sp and the first that fits wins`() {
        val asked = mutableListOf<Float>()
        val fit = fit(room = enregistrements(11f), full = { sizeSp -> asked += sizeSp; enregistrements(sizeSp) })
        assertEquals(11f, fit.sizeSp)
        assertEquals(listOf(12f, 11.5f, 11f), asked)
    }

    private companion object {
        const val FRENCH_AT_12 = 95.8f
        const val REPERTOIRE_FRENCH_AT_10 = 50.7f
        const val REPERTOIRE_RUSSIAN_AT_10 = 52.4f
    }
}
