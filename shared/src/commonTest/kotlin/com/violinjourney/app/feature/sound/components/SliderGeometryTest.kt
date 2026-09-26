package com.violinjourney.app.feature.sound.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where the thumb of a «Звук» slider and the words under it stand — a track 600 px wide with a thumb 20 px across. */
class SliderGeometryTest {
    private val width = 600
    private val inset = 10f

    @Test
    fun `a fraction and the place of the thumb are one and the same`() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.85f, 1f)) {
            val x = SliderGeometry.thumbCentre(fraction, width.toFloat(), inset)
            assertEquals(fraction, SliderGeometry.fractionAt(x, width.toFloat(), inset), 1e-6f)
        }
        assertEquals(inset, SliderGeometry.thumbCentre(0f, width.toFloat(), inset))
        assertEquals(width - inset, SliderGeometry.thumbCentre(1f, width.toFloat(), inset))
        assertEquals(0f, SliderGeometry.fractionAt(-50f, width.toFloat(), inset), "a finger past the end is the end")
        assertEquals(1f, SliderGeometry.fractionAt(width + 50f, width.toFloat(), inset))
    }

    @Test
    fun `a word stands centred under the thumb at its mark`() {
        val wordWidth = 64
        // «чуть», «заметно», «сильно» (spec 5.11)
        for (fraction in listOf(0.25f, 0.5f, 0.85f)) {
            val centre = SliderGeometry.markLeft(fraction, wordWidth, width, inset) + wordWidth / 2f
            val thumb = SliderGeometry.thumbCentre(fraction, width.toFloat(), inset)
            assertTrue(abs(centre - thumb) <= 0.5f, "a word at $fraction is centred at $centre and the thumb stands at $thumb")
        }
    }

    @Test
    fun `a word near an end stays within the track`() {
        val wordWidth = 120
        assertEquals(0, SliderGeometry.markLeft(0.02f, wordWidth, width, inset))
        assertEquals(width - wordWidth, SliderGeometry.markLeft(0.98f, wordWidth, width, inset))
        assertEquals(0, SliderGeometry.markLeft(0.5f, width + 40, width, inset), "a word wider than the track starts at its left")
    }
}
