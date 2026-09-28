package com.violinjourney.app.feature.practice.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** How the ring of the level goes to a new level (spec 3.36.2, 5.29 «Время»): the number and the words never tell two levels. */
class LevelMotionMathTest {
    @Test
    fun `a level passed runs the arc to its end before the new number`() {
        assertEquals(LevelStep.LevelUp, LevelMotionMath.step(shownLevel = 4, level = 5, motion = true))
        assertEquals(LevelStep.LevelUp, LevelMotionMath.step(shownLevel = 4, level = 6, motion = true))
    }

    @Test
    fun `within a level the arc grows and the number stays`() {
        assertEquals(LevelStep.Grow, LevelMotionMath.step(shownLevel = 5, level = 5, motion = true))
    }

    @Test
    fun `a level down changes the number the words and the arc at once`() {
        // a day edited to less: the ring must not show 5 for 600 ms under the words of level 4
        assertEquals(LevelStep.AtOnce, LevelMotionMath.step(shownLevel = 5, level = 4, motion = true))
    }

    @Test
    fun `without motion everything is at once`() {
        assertEquals(LevelStep.AtOnce, LevelMotionMath.step(shownLevel = 4, level = 5, motion = false))
        assertEquals(LevelStep.AtOnce, LevelMotionMath.step(shownLevel = 5, level = 5, motion = false))
        assertEquals(LevelStep.AtOnce, LevelMotionMath.step(shownLevel = 5, level = 4, motion = false))
    }
}
