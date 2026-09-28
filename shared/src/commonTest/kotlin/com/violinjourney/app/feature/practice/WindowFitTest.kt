package com.violinjourney.app.feature.practice

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The window of the home on «Занятия» (spec 3.36.2, 5.29 R2): the picture takes what is left of the first screen, 148 at most, and
 * the whole window fits it — min(148, scroll window − blocks above the window − what stands under the picture − 12); less than 72 —
 * a line; beside its line in landscape, 176 wide, 120 in a column narrower than 400.
 */
class WindowFitTest {
    /** The line of 56 alone: the takts are enough, or the road is done. */
    private val line = 56.dp

    /** The line and the bar to the price with its field, 6 + 12: the takts are short. */
    private val lineAndBar = 74.dp

    @Test
    fun `a usual screen gives the whole 148`() {
        assertEquals(148.dp, WindowFit.picture(viewport = 700.dp, above = 300.dp, under = line))
        assertEquals(148.dp, WindowFit.picture(viewport = 700.dp, above = 300.dp, under = lineAndBar))
    }

    @Test
    fun `a scroll window of 468 with 321 above and the line alone under the picture leaves 79`() {
        // practice-extra.html, screen 3: 360 by 640 with the field of 4 and the tab bar of the mockup, «Хватает…» — no bar
        assertEquals(79.dp, WindowFit.picture(viewport = 468.dp, above = 321.dp, under = line))
    }

    @Test
    fun `the bar to the price under the line is taken from the picture`() {
        assertEquals(133.dp, WindowFit.picture(viewport = 540.dp, above = 321.dp, under = lineAndBar))
        // the 79 of the line alone less 18: the window no longer fits a picture
        assertNull(WindowFit.picture(viewport = 468.dp, above = 321.dp, under = lineAndBar), "61 left")
    }

    @Test
    fun `a call in three lines at a large font is taken from the picture`() {
        // three lines of 26 and the fields of 8: a line of 94
        assertEquals(90.dp, WindowFit.picture(viewport = 517.dp, above = 321.dp, under = 94.dp))
    }

    @Test
    fun `less than 72 left is a line`() {
        assertNull(WindowFit.picture(viewport = 468.dp, above = 340.dp, under = line), "60 left: no picture")
        assertNull(WindowFit.picture(viewport = 400.dp, above = 500.dp, under = line), "nothing left at all")
    }

    @Test
    fun `exactly 72 left is still a picture`() {
        assertEquals(72.dp, WindowFit.picture(viewport = 440.dp, above = 300.dp, under = line))
        assertNull(WindowFit.picture(viewport = 439.dp, above = 300.dp, under = line), "71 left")
    }

    @Test
    fun `the picture beside its line is 176 and 120 in a column narrower than 400`() {
        assertEquals(176.dp, WindowFit.besideWidth(596.dp), "892 by 412: the right column of 596")
        assertEquals(176.dp, WindowFit.besideWidth(400.dp))
        assertEquals(120.dp, WindowFit.besideWidth(399.dp))
        assertEquals(120.dp, WindowFit.besideWidth(344.dp), "640 by 360: the right column of 344")
    }
}
