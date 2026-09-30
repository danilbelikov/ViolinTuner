package com.violinjourney.app.feature.sound.components

import com.violinjourney.app.core.audio.fx.SoundMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The output meter of the player of «Звук» on made-up frame times; its numbers are those of spec 5.11. */
class OutputMeterMotionTest {
    private fun reading(peakDb: Double, limiting: Boolean = false) = SoundMeters(outputPeakDb = peakDb, reductionDb = 0.0, limiting = limiting)

    @Test
    fun `the limiter mark stays dark while the limiter has not worked`() {
        // frame times of any size: `now - Long.MIN_VALUE` used to wrap around and light the mark from the first frame
        val motion = OutputMeterMotion()
        for (now in listOf(0L, 16L, 1_000_000_000L)) {
            motion.step(now, reading(-12.0))
            assertFalse(motion.lit, "lit at $now ms")
            assertEquals(-12.0, motion.number, "the number, not the word, at $now ms")
        }
    }

    @Test
    fun `the limiter mark is lit for six hundred milliseconds after the limiter worked`() {
        val motion = OutputMeterMotion()
        motion.step(1_000, reading(-1.0, limiting = true))
        assertTrue(motion.lit)
        motion.step(1_599, reading(-1.0))
        assertTrue(motion.lit, "still lit a frame before the 600 ms are over")
        motion.step(1_600, reading(-1.0))
        assertFalse(motion.lit)
    }

    @Test
    fun `the level rises at once and the whole bar falls in three hundred milliseconds as spec five eleven says`() {
        val motion = OutputMeterMotion()
        motion.step(0, reading(-30.0))
        assertEquals(0.5f, motion.level, 1e-6f)
        motion.step(60, null)
        assertEquals(0.3f, motion.level, 1e-6f)
        motion.step(150, null)
        assertEquals(0f, motion.level, 1e-6f)
        motion.step(160, reading(-6.0))
        assertEquals(0.9f, motion.level, 1e-6f, "a louder reading is shown at once")
    }

    @Test
    fun `the meter comes to rest only after the limiter mark has gone out`() {
        val motion = OutputMeterMotion()
        // a quiet peak, gone from the bar in 250 ms, but with the limiter working: the mark outlasts the bar
        motion.step(0, reading(-45.0, limiting = true))
        assertFalse(motion.atRest(null))
        motion.step(300, null)
        assertEquals(0f, motion.level)
        assertFalse(motion.atRest(null), "the mark is still lit")
        motion.step(599, null)
        assertFalse(motion.atRest(null))
        motion.step(600, null)
        assertTrue(motion.atRest(null))
        assertFalse(motion.atRest(reading(-45.0)), "a reading wakes it")
    }

    @Test
    fun `the number is refreshed ten times a second`() {
        val motion = OutputMeterMotion()
        motion.step(0, reading(-10.0))
        assertEquals(-10.0, motion.number)
        motion.step(50, reading(-20.0))
        assertEquals(-10.0, motion.number)
        motion.step(100, reading(-20.0))
        assertEquals(-20.0, motion.number)
    }

    @Test
    fun `the first frame after a rest shows its number at once`() {
        val motion = OutputMeterMotion()
        motion.step(0, reading(-10.0))
        motion.rest()
        assertNull(motion.number)
        motion.step(40, reading(-20.0))
        assertEquals(-20.0, motion.number)
    }
}
