package com.violinjourney.app.feature.events.form

/**
 * The row of the tiles of the kinds in the form (spec 3.36.9; review of stage 98б): the chosen tile is kept in sight — a kind of one's
 * own is the fifth tile and further, past the edge of a phone of 360 (tiles of 80, 8 apart, the first 16 from the edge: the fifth starts
 * at 368). Pure, in pixels, with a test.
 */
internal object KindTiles {
    /**
     * Where a row of [viewport] scrolled to [scroll] has to go for the tile [index] — [tile] wide, [gap] apart, the first [side] from the
     * start of the row — to stand whole: [side] from the edge it was cut by (the first tile — the start of the row); null where it stands
     * whole already, at the very edge too, and the row does not move.
     */
    fun scrollToShow(index: Int, tile: Int, gap: Int, side: Int, viewport: Int, scroll: Int): Int? {
        val left = side + index * (tile + gap)
        val right = left + tile
        return when {
            left < scroll -> left - side
            right > scroll + viewport -> right + side - viewport
            else -> null
        }
    }
}
