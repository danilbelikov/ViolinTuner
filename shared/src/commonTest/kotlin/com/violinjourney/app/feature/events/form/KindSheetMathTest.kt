package com.violinjourney.app.feature.events.form

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The signs of the sheet «Вид» (spec 3.36.9, 5.29 R9): six in a row 8 apart from a row of 328, 4 apart from 308, else four in a row 8
 * apart — a cell is never narrower than the touch of 48.
 */
class KindSheetMathTest {
    @Test
    fun `six in a row 8 apart from 328 then 4 apart from 308 then four in a row`() {
        assertEquals(SignGrid(6, 8.dp), KindSheetMath.signGrid(372.dp), "a sheet on 412")
        assertEquals(SignGrid(6, 8.dp), KindSheetMath.signGrid(328.dp))
        assertEquals(SignGrid(6, 4.dp), KindSheetMath.signGrid(327.5.dp))
        assertEquals(SignGrid(6, 4.dp), KindSheetMath.signGrid(320.dp), "a sheet on 360")
        assertEquals(SignGrid(6, 4.dp), KindSheetMath.signGrid(308.dp))
        assertEquals(SignGrid(4, 8.dp), KindSheetMath.signGrid(307.dp))
        assertEquals(SignGrid(4, 8.dp), KindSheetMath.signGrid(280.dp), "a sheet on 320")
    }

    @Test
    fun `no cell is narrower than 48 on any width from 240`() {
        var width = 240.dp
        while (width <= 640.dp) {
            val grid = KindSheetMath.signGrid(width)
            val cell = KindSheetMath.cellWidth(width, grid)
            assertTrue(cell >= 48.dp, "a cell of $cell in a row of $width")
            width += 0.5.dp
        }
    }
}
