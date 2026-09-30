package com.violinjourney.app.feature.live.block

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chips of the goal of «Что играем» (spec 3.36.6, 5.29 R6): one row where each gets its 48 — its gaps give way from 6 to 4
 * before a chip does, as the tonics of R4 — else two; a chip is never narrower than its words nor than it is pressed. The review of
 * stage 116: a phone of 411 dp leaves the panel 371.05, and the rule of 372 put its goal in two rows on every such phone.
 */
class GoalRowsTest {
    @Test
    fun `a row of 360 takes all seven chips of 48 with gaps of 4`() {
        assertEquals(GoalRows(perRow = 7, cellDp = 48f, gapDp = 4f), GoalRows.of(360f))
    }

    @Test
    fun `the panel of a phone of 411 dp keeps the seven in one row of 48 with narrower gaps`() {
        // 1080 px at 420 dpi, the fields of the panel 53 px each: 974 px inside — 371.05 dp
        val rows = GoalRows.of(371.05f)
        assertEquals(7, rows.perRow)
        assertEquals(48f, rows.cellDp, 0.001f)
        assertEquals(5.84f, rows.gapDp, 0.01f)
        // the window of 412 of a test at 2.625: 1082 px, 976 inside — 371.81
        assertEquals(7, GoalRows.of(371.81f).perRow)
    }

    @Test
    fun `from 372 the gaps are 6`() {
        assertEquals(GoalRows(perRow = 7, cellDp = 48f, gapDp = 6f), GoalRows.of(372f))
    }

    @Test
    fun `a row a hair narrower than 360 goes in two rows of four 6 apart`() {
        val rows = GoalRows.of(359.9f)
        assertEquals(4, rows.perRow)
        assertEquals(6f, rows.gapDp)
        assertEquals((359.9f - 18f) / 4f, rows.cellDp, 0.001f)
    }

    @Test
    fun `on a phone of 360 the sheet's row of 320 has four chips of 75 and a half`() {
        assertEquals(GoalRows(perRow = 4, cellDp = 75.5f, gapDp = 6f), GoalRows.of(320f))
    }

    @Test
    fun `the column of the goal lying down has four in a row`() {
        // the column of 300 less its sides of 20
        assertEquals(GoalRows(perRow = 4, cellDp = 60.5f, gapDp = 6f), GoalRows.of(260f))
    }

    @Test
    fun `a row wider than 372 keeps one row — its chips wider`() {
        val rows = GoalRows.of(600f)
        assertEquals(7, rows.perRow)
        assertEquals(6f, rows.gapDp)
        assertEquals((600f - 36f) / 7f, rows.cellDp, 0.001f)
    }

    @Test
    fun `at a large font the chips keep their words and one row asks for more`() {
        // «30» with the fields of a chip: 50.6 at the font 1.3 — seven of them and six gaps of 4 need 378.2; the phone of 412 has two rows
        assertEquals(4, GoalRows.of(371.81f, chipDp = 50.6f).perRow)
        assertEquals(4, GoalRows.of(378.1f, chipDp = 50.6f).perRow)
        val wide = GoalRows.of(378.2f, chipDp = 50.6f)
        assertEquals(7, wide.perRow)
        assertEquals(50.6f, wide.cellDp, 0.001f)
        assertEquals(4f, wide.gapDp, 0.001f)
    }

    @Test
    fun `words narrower than 48 do not make a chip narrower than it is pressed`() {
        // at the default font «30» with its fields is 45.4: a row of 355 is less than seven chips of 48 and six gaps of 4 — two rows
        assertEquals(GoalRows(perRow = 4, cellDp = 84.25f, gapDp = 6f), GoalRows.of(355f, chipDp = 45.4f))
        assertEquals(GoalRows(perRow = 7, cellDp = 48f, gapDp = 4f), GoalRows.of(360f, chipDp = 45.4f))
    }

    @Test
    fun `no chip is narrower than it is pressed nor any gap under 4 from the narrowest phone up`() {
        for (chipDp in listOf(GoalRows.CHIP_TOUCH, 45.4f, 50.6f)) {
            var width = 280f
            while (width <= 600f) {
                val rows = GoalRows.of(width, chipDp = chipDp)
                if (rows.perRow == GoalRows.CELLS) {
                    // one row: never narrower than the chip, nor than its touch
                    assertTrue(rows.cellDp >= maxOf(chipDp, GoalRows.CHIP_TOUCH) - 0.001f, "chip $chipDp at $width: ${rows.cellDp}")
                } else {
                    // two rows (the words of the narrowest phones are the caller's): never under the touch of 48
                    assertTrue(rows.cellDp >= GoalRows.CHIP_TOUCH, "chip $chipDp at $width: ${rows.cellDp}")
                }
                assertTrue(rows.gapDp in GoalRows.MIN_GAP..GoalRows.GAP, "chip $chipDp at $width: a gap of ${rows.gapDp}")
                // the row is exactly as wide as its chips and their gaps
                assertEquals(width, rows.perRow * rows.cellDp + (rows.perRow - 1) * rows.gapDp, 0.001f)
                width += 0.25f
            }
        }
    }
}
