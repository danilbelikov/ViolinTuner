package com.violinjourney.app.core.recording.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMuxerShiftTest {
    @Test
    fun `a picture that began later is moved later`() {
        assertEquals(180_000L, VideoMuxer.shiftUs(pictureStartNanos = 1_180_000_000L, soundStartNanos = 1_000_000_000L))
    }

    @Test
    fun `a picture that began earlier is moved earlier`() {
        assertEquals(-40_000L, VideoMuxer.shiftUs(pictureStartNanos = 960_000_000L, soundStartNanos = 1_000_000_000L))
    }

    @Test
    fun `frames moved before the sound's start are dropped`() {
        assertNull(VideoMuxer.shiftedUs(ptsUs = 10_000L, shiftUs = -40_000L))
        assertEquals(0L, VideoMuxer.shiftedUs(ptsUs = 40_000L, shiftUs = -40_000L))
        assertEquals(190_000L, VideoMuxer.shiftedUs(ptsUs = 10_000L, shiftUs = 180_000L))
    }
}
