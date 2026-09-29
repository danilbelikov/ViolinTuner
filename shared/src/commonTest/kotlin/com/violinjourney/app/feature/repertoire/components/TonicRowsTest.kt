package com.violinjourney.app.feature.repertoire.components

import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.Tonic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seven tonics in one row only where each gets 48 dp; the gaps of the row give way from 6 to 4 before a button does; below that
 * in two rows of the same buttons (spec 3.36.4, 5.29 R4; the review of stage 110: a phone of 411 dp leaves the sheet 371).
 */
class TonicRowsTest {
    private val allSeven = listOf(Tonic.entries.toList())

    @Test
    fun `a row of 360 holds all seven — seven of 48 and six gaps of 4`() {
        assertEquals(360.dp, TonicRows.OneRowFrom)
        assertEquals(allSeven, TonicRows.of(360.dp))
        assertEquals(4.dp, TonicRows.gap(360.dp))
        assertEquals(48.dp, TonicRows.cellWidth(360.dp))
    }

    @Test
    fun `the sheet of a phone of 411 dp keeps them in one row of 48 with narrower gaps`() {
        // 1080 px at 420 dpi, the fields of the sheet 53 px each: 974 px inside — 371.05 dp
        val sheet = 371.05.dp
        assertEquals(allSeven, TonicRows.of(sheet))
        assertEquals(5.84f, TonicRows.gap(sheet).value, 0.01f)
        assertEquals(48f, TonicRows.cellWidth(sheet).value, 0.001f)
    }

    @Test
    fun `from 372 the gaps are 6 and a wider row makes the buttons wider`() {
        assertEquals(6.dp, TonicRows.gap(372.dp))
        assertEquals(48.dp, TonicRows.cellWidth(372.dp))
        // the form of a scale on the same phone: 379 inside its fields of 16
        assertEquals(6.dp, TonicRows.gap(379.dp))
        assertTrue(TonicRows.cellWidth(379.dp) > 48.dp)
        assertEquals(allSeven, TonicRows.of(560.dp))
    }

    @Test
    fun `a row narrower than 360 breaks into C D E F over G A B`() {
        val rows = TonicRows.of(359.dp)
        assertEquals(listOf(listOf(Tonic.C, Tonic.D, Tonic.E, Tonic.F), listOf(Tonic.G, Tonic.A, Tonic.B)), rows)
        assertEquals(6.dp, TonicRows.gap(359.dp))
    }

    @Test
    fun `in two rows every button is as wide as those of the first row and none is under 48`() {
        // portrait 360: the form has 328 inside its fields, the sheet 320 inside its own
        for (width in listOf(359.dp, 328.dp, 320.dp)) {
            val cell = TonicRows.cellWidth(width)
            assertEquals(width, cell * TonicRows.FIRST_ROW + TonicRows.Gap * (TonicRows.FIRST_ROW - 1), "the first row fills $width")
            assertTrue(cell >= 48.dp, "a button of $cell at $width")
        }
    }

    @Test
    fun `no button of one row is under 48 however narrow the gaps`() {
        var width = TonicRows.OneRowFrom
        while (width <= 380.dp) {
            val cell = TonicRows.cellWidth(width)
            assertTrue(cell.value >= 48f - 0.001f, "a button of $cell at $width")
            assertTrue(TonicRows.gap(width) >= TonicRows.MinGap, "a gap of ${TonicRows.gap(width)} at $width")
            width += 0.25.dp
        }
    }
}
