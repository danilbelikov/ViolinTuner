package com.violinjourney.app.core.audio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** The player waits on a full track for as long as the rest of its chunk takes to play, within bounds. */
class TrackRoomTest {
    @Test
    fun `the wait is the time the rest of the chunk takes to play`() {
        assertEquals(15L, TrackRoom.waitMs(samplesLeft = 720, channels = 1, sampleRate = 48_000))
        // interleaved stereo: twice the samples for the same time
        assertEquals(15L, TrackRoom.waitMs(samplesLeft = 1_440, channels = 2, sampleRate = 48_000))
        assertEquals(10L, TrackRoom.waitMs(samplesLeft = 441, channels = 1, sampleRate = 44_100))
    }

    @Test
    fun `a sliver is not a busy loop and a long rest is not a long sleep`() {
        assertEquals(TrackRoom.MIN_WAIT_MS, TrackRoom.waitMs(samplesLeft = 100, channels = 1, sampleRate = 48_000))
        assertEquals(TrackRoom.MAX_WAIT_MS, TrackRoom.waitMs(samplesLeft = 48_000, channels = 1, sampleRate = 48_000))
    }

    @Test
    fun `the track runs down by no more than a mixer period or two before it is topped up`() {
        // a whole chunk left (2048 frames, ~43 ms): the thread comes back well before it has played
        assertEquals(20L, TrackRoom.waitMs(samplesLeft = 2_048, channels = 1, sampleRate = 48_000))
        assertEquals(20L, TrackRoom.waitMs(samplesLeft = 4_096, channels = 2, sampleRate = 44_100))
    }

    @Test
    fun `odd numbers from a track do not divide by zero`() {
        // no channels reads as one
        assertEquals(15L, TrackRoom.waitMs(samplesLeft = 720, channels = 0, sampleRate = 48_000))
        assertEquals(TrackRoom.MAX_WAIT_MS, TrackRoom.waitMs(samplesLeft = 2_048, channels = 1, sampleRate = 0))
    }
}
