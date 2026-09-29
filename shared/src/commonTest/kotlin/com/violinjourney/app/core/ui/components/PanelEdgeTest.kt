package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The panel of a player reaches the bottom edge of the window (spec 5.29 R5, «+ системный отступ»): the root pads a screen over the tabs
 * for the navigation bar and takes that inset away, so the zone stands the inset over the edge and its ground runs on through the
 * strip — and only that strip. Pixels of a Pixel 7 (2.625 a dp): the gesture bar of 24 dp is 63 px; the tolerance of 1 dp, 2.625.
 */
class PanelEdgeTest {
    private val tolerance = 2.625f

    @Test
    fun `under a zone the root has stood over the navigation bar the ground runs on to the edge`() {
        assertEquals(63f, PanelEdge.below(gap = 63f, inset = 63f, tolerance = tolerance))
        // a zone laid out in whole pixels over an inset of fractional ones: a pixel off is still the inset
        assertEquals(64f, PanelEdge.below(gap = 64f, inset = 63f, tolerance = tolerance))
    }

    @Test
    fun `a zone at the edge already draws nothing under it`() {
        // the zone padded the inset itself (nothing had taken it) — its ground is at the edge
        assertEquals(0f, PanelEdge.below(gap = 0f, inset = 63f, tolerance = tolerance))
    }

    @Test
    fun `something else under the zone is not drawn over`() {
        // a zone over the tab bar (its 56 and the 6 over it, 162 px, and the inset): the bar is there, not the edge of the window
        assertEquals(0f, PanelEdge.below(gap = 225f, inset = 63f, tolerance = tolerance))
        // no inset at all — a preview, a test in a window of its own size, a phone lying with its buttons at the side
        assertEquals(0f, PanelEdge.below(gap = 40f, inset = 0f, tolerance = tolerance))
    }
}
