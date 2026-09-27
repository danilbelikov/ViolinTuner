package com.violinjourney.app.feature.sound.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «Сейчас сжимает» on made-up frame times (spec 5.11): up at once, down in ~300 ms, a number ten times a second. */
class ReductionMeterMotionTest {
    private val fullDb = 20.0

    @Test
    fun `the bar rises to its share at once and falls to nothing in three hundred milliseconds`() {
        val motion = ReductionMeterMotion(fullDb)
        motion.step(0, 10.0)
        assertEquals(0.5f, motion.level, 1e-6f)
        motion.step(90, null)
        assertEquals(0.2f, motion.level, 1e-6f)
        motion.step(150, null)
        assertEquals(0f, motion.level, 1e-6f)
        motion.step(160, 30.0)
        assertEquals(1f, motion.level, 1e-6f, "more than the whole bar is the whole bar")
    }

    @Test
    fun `the number follows the reading at most ten times a second`() {
        val motion = ReductionMeterMotion(fullDb)
        motion.step(0, 4.0)
        assertEquals(4.0, motion.number)
        motion.step(50, 6.0)
        assertEquals(4.0, motion.number)
        motion.step(100, 6.0)
        assertEquals(6.0, motion.number)
        motion.step(200, null)
        assertEquals(0.0, motion.number, "no reading says nothing is taken off")
    }

    @Test
    fun `it rests only with no reading and an empty bar`() {
        val motion = ReductionMeterMotion(fullDb)
        assertTrue(motion.atRest(null))
        assertFalse(motion.atRest(0.0), "a reading of nothing still plays")
        motion.step(0, 8.0)
        assertFalse(motion.atRest(null), "the bar is still falling")
        motion.step(300, null)
        assertTrue(motion.atRest(null))
    }

    @Test
    fun `the first step after a rest shows its number at once`() {
        val motion = ReductionMeterMotion(fullDb)
        motion.step(0, 4.0)
        motion.rest()
        assertEquals(0.0, motion.number)
        motion.step(40, 7.0)
        assertEquals(7.0, motion.number)
    }
}
