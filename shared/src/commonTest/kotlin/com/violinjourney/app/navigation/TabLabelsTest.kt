package com.violinjourney.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One size for the four labels of the tab bar (spec 5.29). The widths are those components.html measured in Manrope 600:
 * «Enregistrements» — 95.8 dp at 12 sp and 79.9 at 10 sp, so a width in proportion to the size; the room of an item is
 * 82 dp on a 360-dp screen and 95 on 412.
 */
class TabLabelsTest {
    private fun enregistrements(sizeSp: Float) = FRENCH_AT_12 * sizeSp / TabLabels.MAX_SP

    @Test
    fun `labels that fit keep 12 sp`() {
        assertEquals(12f, TabLabels.size(room = 82f) { sizeSp -> 66.2f * sizeSp / 12f })
    }

    @Test
    fun `the French word on 360 dp brings all four to 10 sp`() {
        assertEquals(10f, TabLabels.size(room = 82f, widthAt = ::enregistrements))
    }

    @Test
    fun `the French word on 412 dp stops at 11 and a half sp`() {
        assertEquals(11.5f, TabLabels.size(room = 95f, widthAt = ::enregistrements))
    }

    @Test
    fun `nothing below 10 sp even when 10 does not fit`() {
        // a large system font: past 10 sp the answer is a short word from the translator, not a smaller one
        assertEquals(10f, TabLabels.size(room = 60f, widthAt = ::enregistrements))
        assertEquals(10f, TabLabels.size(room = 0f) { 1_000f })
    }

    @Test
    fun `a label exactly as wide as its room fits`() {
        assertEquals(11f, TabLabels.size(room = enregistrements(11f), widthAt = ::enregistrements))
    }

    @Test
    fun `sizes are tried from the largest down by half an sp and the first that fits wins`() {
        val asked = mutableListOf<Float>()
        val size = TabLabels.size(room = enregistrements(11f)) { sizeSp -> asked += sizeSp; enregistrements(sizeSp) }
        assertEquals(11f, size)
        assertEquals(listOf(12f, 11.5f, 11f), asked)
    }

    private companion object {
        const val FRENCH_AT_12 = 95.8f
    }
}
