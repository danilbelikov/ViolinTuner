package com.violinjourney.app.feature.events.form

import androidx.compose.ui.unit.Dp
import com.violinjourney.app.feature.events.EventsDimens

/** How the twelve signs of one's own stand in the sheet «Вид»: [columns] to a row, [gap] between them. */
data class SignGrid(val columns: Int, val gap: Dp)

/**
 * The arithmetic of the sheet «Вид» (spec 3.36.9, 5.29 R9): pure, with a test. Six signs in a row with a gap of 8 where the row is not
 * narrower than 328 (a sheet on 412: 372); a gap of 4 from 308 (on 360: 320 — cells of 50); narrower still — four in a row in three rows
 * with a gap of 8 (on 320: 280 — cells of 64): a sign is never a target under 48 (3.36, «Неизменное»), as the tonics of R4.
 */
object KindSheetMath {
    fun signGrid(rowWidth: Dp): SignGrid = when {
        rowWidth >= EventsDimens.SignRowWide -> SignGrid(EventsDimens.SIGNS_WIDE, EventsDimens.SignGapWide)
        rowWidth >= EventsDimens.SignRowTight -> SignGrid(EventsDimens.SIGNS_WIDE, EventsDimens.SignGapTight)
        else -> SignGrid(EventsDimens.SIGNS_NARROW, EventsDimens.SignGapWide)
    }

    /** The width a cell of [grid] gets in a row [rowWidth] wide — the row shares itself by weight, to a pixel; the grid keeps it ≥ 48. */
    fun cellWidth(rowWidth: Dp, grid: SignGrid): Dp = (rowWidth - grid.gap * (grid.columns - 1)) / grid.columns
}
