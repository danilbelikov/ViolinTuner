package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chip of the streak (spec 5.29; 01-practice п. 7): no streak of zero, the flame from three days, bigger from seven. */
class StreakChipMathTest {
    private val flameFrom = PracticeMotion.FLAME_FROM_DAYS
    private val fullFrom = PracticeMotion.FLAME_FULL_FROM_DAYS

    @Test
    fun `there is no chip for no streak`() {
        assertFalse(StreakChipMath.shown(0))
        assertFalse(StreakChipMath.shown(-1))
        assertTrue(StreakChipMath.shown(1), "a streak of one day is a streak")
    }

    @Test
    fun `the flame is 16 while the streak is young and 18 from the full flame on`() {
        assertEquals(16.dp, StreakChipMath.flameSize(flameFrom))
        assertEquals(16.dp, StreakChipMath.flameSize(fullFrom - 1))
        assertEquals(18.dp, StreakChipMath.flameSize(fullFrom))
        assertEquals(18.dp, StreakChipMath.flameSize(PracticeMotion.FLAME_HOT_FROM_DAYS))
    }

    @Test
    fun `the words stand further from the edge while there is no flame`() {
        assertEquals(10.dp, StreakChipMath.startPadding(1))
        assertEquals(10.dp, StreakChipMath.startPadding(flameFrom - 1))
        assertEquals(7.dp, StreakChipMath.startPadding(flameFrom))
        assertEquals(7.dp, StreakChipMath.startPadding(fullFrom))
    }
}
