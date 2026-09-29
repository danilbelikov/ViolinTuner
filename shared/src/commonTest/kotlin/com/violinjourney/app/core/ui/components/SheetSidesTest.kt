package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet never lies under a cutout of the camera or a bar of the system at a side of its window (the review of stage 110): in the
 * middle while they are clear of it, moved off them otherwise, and narrower than 640 where the window has no room for it between them.
 * The numbers are dp, as pixels at the density 1.
 */
class SheetSidesTest {
    private val widest = 640

    @Test
    fun `lying on 892 the sheet of 640 stays in the middle - the cutout of 36 is far from it`() {
        val width = SheetSides.width(892, widest, left = 36, right = 0)
        assertEquals(640, width)
        assertEquals(126, SheetSides.x(892, width, left = 36, right = 0))
    }

    @Test
    fun `lying on 640 with a cutout the sheet starts at its edge and is narrower`() {
        // the emulator of the notes: DisplayCutout{insets=Rect(73, 0 - 0, 0)} at 2 px a dp — 36.5 dp, here 36
        val width = SheetSides.width(640, widest, left = 36, right = 0)
        assertEquals(604, width)
        assertEquals(36, SheetSides.x(640, width, left = 36, right = 0))
    }

    @Test
    fun `a bar of three buttons at the right leaves the sheet what is between it and the cutout`() {
        val width = SheetSides.width(640, widest, left = 36, right = 48)
        assertEquals(556, width)
        val x = SheetSides.x(640, width, left = 36, right = 48)
        assertEquals(36, x)
        assertEquals(640 - 48, x + width)
    }

    @Test
    fun `where the middle would touch the cutout the sheet moves off it and keeps its 640`() {
        val width = SheetSides.width(700, widest, left = 36, right = 0)
        assertEquals(640, width)
        assertEquals(36, SheetSides.x(700, width, left = 36, right = 0))
    }

    @Test
    fun `upright and on an iPhone lying it is where it always was`() {
        assertEquals(411, SheetSides.width(411, widest, left = 0, right = 0))
        assertEquals(0, SheetSides.x(411, 411, left = 0, right = 0))
        // iPhone 15 Pro Max on its side: 932 wide, the safe area 59 at each side
        val width = SheetSides.width(932, widest, left = 59, right = 59)
        assertEquals(146, SheetSides.x(932, width, left = 59, right = 59))
    }

    @Test
    fun `whatever the window and its sides the sheet is never under them`() {
        for (window in 300..1000 step 7) {
            for (left in 0..60 step 6) {
                for (right in 0..60 step 12) {
                    val width = SheetSides.width(window, widest, left, right)
                    val x = SheetSides.x(window, width, left, right)
                    assertTrue(width <= widest, "$width wider than $widest")
                    assertTrue(x >= left, "at $x under the left side $left of $window")
                    assertTrue(x + width <= window - right, "to ${x + width} under the right side $right of $window")
                }
            }
        }
    }
}
