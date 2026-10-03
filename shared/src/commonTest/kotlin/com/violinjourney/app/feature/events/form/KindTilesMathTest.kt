package com.violinjourney.app.feature.events.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The row of the tiles of the kinds keeps the chosen one in sight (spec 3.36.9; review of stage 98б), at a density of 1: tiles of 80, 8
 * apart, the first 16 from the start; a phone of 360 sees the row 360 wide.
 */
class KindTilesMathTest {
    private fun scrollFor(index: Int, scroll: Int, viewport: Int = 360) =
        KindTiles.scrollToShow(index, tile = 80, gap = 8, side = 16, viewport = viewport, scroll = scroll)

    @Test
    fun `a tile in sight does not move the row`() {
        assertNull(scrollFor(index = 0, scroll = 0))
        assertNull(scrollFor(index = 3, scroll = 0), "the fourth ends at the edge of the screen — whole")
    }

    @Test
    fun `a kind of ones own past the edge of a phone of 360 comes into sight 16 from it`() {
        // the fifth tile — the first kind of one's own — stands at 368 … 448
        assertEquals(448 + 16 - 360, scrollFor(index = 4, scroll = 0))
        assertEquals(624 + 16 - 360, scrollFor(index = 6, scroll = 0), "the seventh")
        assertNull(scrollFor(index = 4, scroll = 104), "there already")
    }

    @Test
    fun `a tile behind the start comes back 16 from it and the first to the start of the row`() {
        // «Другое» — the fourth — after the kind chosen is deleted, the row scrolled to «Свой вид»
        assertEquals(280 - 16, scrollFor(index = 3, scroll = 400))
        assertEquals(0, scrollFor(index = 0, scroll = 200))
    }
}
