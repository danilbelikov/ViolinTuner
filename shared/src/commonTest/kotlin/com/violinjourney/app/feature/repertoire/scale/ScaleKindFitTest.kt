package com.violinjourney.app.feature.repertoire.scale

import kotlin.test.Test
import kotlin.test.assertEquals

/** The kinds of a scale stand two to a row with their words whole: smaller together first, one to a row only at the end (spec 3.36.4). */
class ScaleKindFitTest {
    /** A word as wide as [perSp] × the size. */
    private fun widest(perSp: Float): (Float) -> Float = { sizeSp -> perSp * sizeSp }

    @Test
    fun `a word that fits at 14 keeps 14 and two to a row`() {
        assertEquals(ScaleKindFit.Plan(14f, columns = 2), ScaleKindFit.plan(halfRoom = 136f, widest(perSp = 9f)))
    }

    @Test
    fun `a word too wide at 14 makes all the words smaller by half steps`() {
        // «гармонический» at the font 1.3 on 360: ≈ 142 at 14 sp, the tile has 136 for its words
        val plan = ScaleKindFit.plan(halfRoom = 136f, widest(perSp = 142f / 14f))
        assertEquals(2, plan.columns)
        assertEquals(13f, plan.sizeSp)
    }

    @Test
    fun `where even 11 and a half breaks a word the tiles stand one to a row at 14`() {
        assertEquals(ScaleKindFit.Plan(14f, columns = 1), ScaleKindFit.plan(halfRoom = 100f, widest(perSp = 10f)))
    }

    @Test
    fun `the least size itself is tried`() {
        assertEquals(ScaleKindFit.Plan(11.5f, columns = 2), ScaleKindFit.plan(halfRoom = 115f, widest(perSp = 10f)))
    }
}
