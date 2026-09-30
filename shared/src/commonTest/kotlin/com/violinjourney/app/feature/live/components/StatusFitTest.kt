package com.violinjourney.app.feature.live.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The word and the cents in the room the right column of landscape leaves them (spec 5.29 R6): full, compact, then a point smaller
 * at a time down to the letters of the strings, and where not even that stands — nothing, never a glyph clipped. The lines are those
 * of Manrope at the default font: 1.366 of the size (ascent 1.066 + descent 0.3, `manrope_variable.ttf`); the widths — the Russian
 * «в строе» (3.65 dp a point, HarfBuzz, 600) and the widest cents «−00» (1.78 dp a point, 700, tabular).
 */
class StatusFitTest {
    private fun lineOf(sizeSp: Float) = LINE_EM * sizeSp

    private fun heightOf(step: StatusStep) = maxOf(lineOf(step.wordSp), lineOf(step.centsSp), step.arrowDp)

    private fun widthOf(step: StatusStep) = step.arrowDp + 2 * step.gapDp + WORD_PER_SP * step.wordSp + CENTS_PER_SP * step.centsSp

    private fun fit(roomHeight: Float, roomWidth: Float = WIDE) = StatusFit.fit(roomHeight, roomWidth, ::heightOf, ::widthOf)

    @Test
    fun `the steps go from the full size through the compact one a point at a time down to the letters of the strings`() {
        assertEquals(listOf(28f, 24f, 23f, 22f, 21f, 20f), StatusFit.steps.map { it.wordSp })
        assertEquals(listOf(36f, 30f, 28.75f, 27.5f, 26.25f, 25f), StatusFit.steps.map { it.centsSp })
        assertEquals(StatusFit.LEAST_WORD_SP, StatusFit.steps.last().wordSp)
        // the sign and the gap keep the proportion of the compact size
        assertEquals(32f * 20f / 24f, StatusFit.steps.last().arrowDp, 0.001f)
        assertEquals(12f * 20f / 24f, StatusFit.steps.last().gapDp, 0.001f)
    }

    @Test
    fun `in the room of 640 x 360 behind a cutout the word keeps its full size in tuning`() {
        // 603 × 336: the column leaves the word and the cents 50 (LiveLayoutMath.landscapeWordRoom); the cents' line is 49.2
        assertEquals(StatusFit.FULL, fit(roomHeight = 50f))
    }

    @Test
    fun `a lower room takes the compact size — then a point smaller at a time`() {
        assertEquals(StatusFit.COMPACT, fit(roomHeight = 42f))
        assertEquals(23f, fit(roomHeight = 40f)?.wordSp)
        assertEquals(20f, fit(roomHeight = 34.2f)?.wordSp)
    }

    @Test
    fun `where not even the least stands the two give way whole`() {
        // the emulator's 640 × 360 lying down: Live 308 high, the column leaves 22 in «Настройка»
        assertNull(fit(roomHeight = 22f))
        assertNull(fit(roomHeight = 34f))
        assertNull(fit(roomHeight = 0f))
    }

    @Test
    fun `a narrow column steps the row down by its width as well`() {
        // the full row of «в строе» is 234 wide: in 200 the compact one (197) stands
        assertEquals(StatusFit.COMPACT, fit(roomHeight = 60f, roomWidth = 200f))
        assertNull(fit(roomHeight = 60f, roomWidth = 100f))
    }

    @Test
    fun `the sign counts in the height of the row`() {
        // at 28 sp the word's line is 38.2 but the arrow of «выше» is 40: a room of 39 is not enough for it
        val signs = StatusFit.fit(39f, WIDE, heightOf = { step -> maxOf(lineOf(step.wordSp), step.arrowDp) }, widthOf = ::widthOf)
        assertEquals(StatusFit.COMPACT, signs)
    }

    private companion object {
        const val LINE_EM = 1.366f
        const val WORD_PER_SP = 3.65f
        const val CENTS_PER_SP = 1.78f
        const val WIDE = 460f
    }
}
