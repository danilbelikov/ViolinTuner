package com.violinjourney.app.feature.events.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wheels of «Время» (spec 3.36.9, plan D26): lists round and round, the chosen value in the middle of the five rows seen. The wheel
 * takes its middle row and its value from here ([TimeWheel]): what is checked here is what it does.
 */
class WheelMathTest {
    @Test
    fun `the list starts in the middle of its turns with the value in the middle row`() {
        val top = WheelMath.startRow(value = 17, count = 24)
        assertEquals(200 * 24 + 15, top)
        assertEquals(top + 2, WheelMath.middleRow(top, past = false), "the third of the five rows seen")
        assertEquals(17, WheelMath.valueOf(WheelMath.middleRow(top, past = false), count = 24))
        assertEquals(18, WheelMath.valueOf(WheelMath.middleRow(top, past = true), count = 24), "more than half of the first row gone up — the next is in the middle")
    }

    @Test
    fun `a row counts as gone once more than half of it has gone up`() {
        // a row of 44 dp at 2.625 px a dp: 115.5 px
        assertFalse(WheelMath.isPast(offset = 57, row = 115.5f), "57 of 115.5 — the row is still the first")
        assertTrue(WheelMath.isPast(offset = 58, row = 115.5f), "58 — more than half: the next row is in the middle")
        assertFalse(WheelMath.isPast(offset = 0, row = 115.5f), "a wheel standing on a row")
    }

    @Test
    fun `the values go round`() {
        assertEquals(0, WheelMath.valueOf(24 * 300, count = 24))
        assertEquals(23, WheelMath.valueOf(24 * 300 - 1, count = 24))
        assertEquals(11, WheelMath.valueOf(-1, count = 12), "below the first row the list goes on from its end")
    }

    @Test
    fun `a value from outside is reached the nearest way round`() {
        val middle = WheelMath.startRow(value = 23, count = 24) + 2
        // 23 → 0 is one step forward, not 23 back
        assertEquals(middle + 1 - 2, WheelMath.topFor(0, middle, 24))
        assertEquals(middle - 1 - 2, WheelMath.topFor(22, middle, 24))
        val minutes = WheelMath.startRow(value = 0, count = 12) + 2
        assertEquals(minutes - 1 - 2, WheelMath.topFor(11, minutes, 12), "00 → 55 is one step back")
        assertEquals(11, WheelMath.valueOf(WheelMath.middleRow(WheelMath.topFor(11, minutes, 12), past = false), count = 12))
    }
}
