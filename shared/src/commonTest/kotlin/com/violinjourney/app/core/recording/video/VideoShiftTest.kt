package com.violinjourney.app.core.recording.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where a picture that began apart from its sound goes in the splice of a take (spec 3.32, 5.25), on both platforms. */
class VideoShiftTest {
    @Test
    fun `a picture that began later is moved later`() {
        assertEquals(180_000L, VideoShift.shiftUs(pictureStartNanos = 1_180_000_000L, soundStartNanos = 1_000_000_000L))
    }

    @Test
    fun `a picture that began earlier is moved earlier`() {
        assertEquals(-40_000L, VideoShift.shiftUs(pictureStartNanos = 960_000_000L, soundStartNanos = 1_000_000_000L))
    }

    @Test
    fun `frames moved before the sound's start are dropped`() {
        assertNull(VideoShift.shiftedUs(ptsUs = 10_000L, shiftUs = -40_000L))
        assertEquals(0L, VideoShift.shiftedUs(ptsUs = 40_000L, shiftUs = -40_000L))
        assertEquals(190_000L, VideoShift.shiftedUs(ptsUs = 10_000L, shiftUs = 180_000L))
    }
}
