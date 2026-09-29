package com.violinjourney.app.feature.sound.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «Готовим минусовку…» in the first row of the player stands whole (spec 3.36.5, 5.29 R5): beside the dimmed A/B and «Звук» of the
 * compact panel at 15 sp, a little smaller if need be; alone in its row, with them a row lower, where it would break a word beside
 * them. The words here need [need] dp at 15 sp and proportionally less smaller — their widest word, the rest of them in two lines;
 * the rooms are those of the compact panel (dp): 360 × 640 — 116 beside A/B and «Звук», 268 alone; the column of 640 × 360 behind a
 * cutout — 65.5 beside, 217.5 alone. The needs: CoreText, Manrope 800 with the tracking of `bodyLarge`.
 */
class PreparingFitTest {
    private fun plan(need: Float, beside: Float, alone: Float) =
        PreparingFit.plan(beside, alone) { room, sizeSp -> need * sizeSp / PreparingFit.MAX_SP <= room }

    @Test
    fun `words that stand beside A B and «Звук» keep 15 sp and the row`() {
        // Russian at the font 1.0: «минусовку…» 97.4 of 116
        assertEquals(PreparingFit.Plan(15f, stacked = false), plan(need = 97.4f, beside = 116f, alone = 268f))
    }

    @Test
    fun `a little too wide beside them they step down in the row`() {
        // Russian at the font 1.3: «минусовку…» 126.6 of 116 — 13.5 sp, 113.9
        assertEquals(PreparingFit.Plan(13.5f, stacked = false), plan(need = 126.6f, beside = 116f, alone = 268f))
    }

    @Test
    fun `where they would break a word beside them they take the row alone at 15 sp`() {
        // French at the font 1.3: «l'accompagnement…» 208.2 — 13 sp would still be 180 of 116
        assertEquals(PreparingFit.Plan(15f, stacked = true), plan(need = 208.2f, beside = 116f, alone = 268f))
    }

    @Test
    fun `alone in a narrow row they step down too`() {
        // the same in a room of 190 alone: 13.5 sp, 187.4
        assertEquals(PreparingFit.Plan(13.5f, stacked = true), plan(need = 208.2f, beside = 65.5f, alone = 190f))
        // not even 12 sp: 12 sp all the same, the last line with an ellipsis
        assertEquals(PreparingFit.Plan(12f, stacked = true), plan(need = 300f, beside = 65.5f, alone = 200f))
    }

    @Test
    fun `with nothing after them in the row there is nowhere to go down to`() {
        // the regular panel: A/B and «Звук» have a row of their own; 12 sp, 160 of 160
        assertEquals(PreparingFit.Plan(12f, stacked = false), plan(need = 200f, beside = 160f, alone = 160f))
    }
}
