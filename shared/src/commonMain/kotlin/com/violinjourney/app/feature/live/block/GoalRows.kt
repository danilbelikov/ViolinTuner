package com.violinjourney.app.feature.live.block

import kotlin.math.ceil

/**
 * How the chips of the goal of «Что играем» stand in a row [widthDp] wide (spec 3.36.6, 5.29 R6): «−5 · 5 · 10 · 15 · 20 · 30 · +5»
 * in one row where each gets its 48 — the gaps of the row are [GAP] where it has room for them and give way down to [MIN_GAP] before
 * a chip goes under its width, as the tonics of R4 do: a row no narrower than 360 (7 × 48 + 6 × 4). A phone of 411 dp (1080 px at
 * 420 dpi) leaves the panel 371.05 inside its fields of 20 — one row there, 5.84 apart, as on the screen of 412 the goal is drawn for.
 * Narrower — two rows, «−5 · 5 · 10 · 15» and «20 · 30 · +5», [GAP] apart, every chip as wide as those of the first row, from the
 * start of the row (portrait 360; the column of the goal lying down). A chip is also never narrower than its words ([chipDp] — at a
 * large font «30» with the fields of a chip is ≈ 51 at 1.3 and ≈ 55 at 1.5): then one row asks for more. [perRow] chips in a row,
 * each [cellDp] wide, [gapDp] apart. Pure.
 */
data class GoalRows(val perRow: Int, val cellDp: Float, val gapDp: Float) {
    companion object {
        /** The least a chip is pressed over (spec 5.29 R1): its 44 seen, 48 touched. */
        const val CHIP_TOUCH = 48f

        /** Between the chips of a row (spec 5.29 R6). */
        const val GAP = 6f

        /** The least gap of one row (as the tonics of R4, 5.29 R4): a row narrower than seven chips with these goes on two rows. */
        const val MIN_GAP = 4f

        /** «−5», the five quick goals and «+5». */
        const val CELLS = 7

        /** [chipDp] — the narrowest a chip may be: 48, or its widest words with its fields where they need more. */
        fun of(widthDp: Float, cells: Int = CELLS, chipDp: Float = CHIP_TOUCH): GoalRows {
            val count = cells.coerceAtLeast(1)
            val chip = maxOf(chipDp, CHIP_TOUCH)
            if (widthDp >= count * chip + (count - 1) * MIN_GAP) {
                // one row: 6 apart where it has room for them, else what the chips of their width leave — never less than 4
                val gap = if (count > 1) minOf(GAP, (widthDp - count * chip) / (count - 1)) else 0f
                return GoalRows(count, (widthDp - (count - 1) * gap) / count, gap)
            }
            val perRow = ceil(count / 2f).toInt()
            return GoalRows(perRow, ((widthDp - (perRow - 1) * GAP) / perRow).coerceAtLeast(0f), GAP)
        }
    }
}
