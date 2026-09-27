package com.violinjourney.app.core.audio.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The position of a player is what is heard (spec 5.13): the head of the track less the delay of the output. */
class HeardClockTest {
    @Test
    fun `the delay is the head less what the output presents now`() {
        // a stamp of 38 400 frames taken now, with the head at 48 000: 200 ms at 48 kHz
        assertEquals(9_600L, HeardClock.lagOf(48_000, 38_400, stampNanos = NOW, nowNanos = NOW, rate = RATE, maxLagMs = 1_000))
        // the same stamp read 100 ms later: the output has presented 4 800 more since
        assertEquals(4_800L, HeardClock.lagOf(48_000, 38_400, stampNanos = NOW, nowNanos = NOW + 100 * NANOS_PER_MS, rate = RATE, maxLagMs = 1_000))
    }

    @Test
    fun `a stamp that puts the ear ahead of the track or a second behind it is not believed`() {
        assertNull(HeardClock.lagOf(48_000, 50_000, stampNanos = NOW, nowNanos = NOW, rate = RATE, maxLagMs = 1_000))
        assertNull(HeardClock.lagOf(100_000, 48_000, stampNanos = NOW, nowNanos = NOW, rate = RATE, maxLagMs = 1_000))
    }

    @Test
    fun `after a start the position waits at its place until the sound reaches the ear`() {
        val clock = HeardClock(RATE, maxLagMs = 1_000, waitMs = 500)
        clock.stamp(head = 9_600, stampFrames = 0, stampNanos = NOW, nowNanos = NOW)
        assertTrue(clock.lagKnown)
        clock.startAt(2_000, head = 10_000)
        // 100 ms taken by the track, 200 ms of delay: nothing of it heard yet
        assertEquals(2_000L, clock.positionMs(head = 14_800, count = 2_048, durationMs = 60_000))
        // 300 ms taken: 100 ms heard
        assertEquals(2_100L, clock.positionMs(head = 24_400, count = 2_048, durationMs = 60_000))
        val short = HeardClock(RATE, maxLagMs = 1_000, waitMs = 500)
        short.stamp(head = 0, stampFrames = 0, stampNanos = NOW, nowNanos = NOW)
        short.startAt(2_000, head = 0)
        assertEquals(2_100L, short.positionMs(head = 9_600, count = 2_048, durationMs = 2_100), "never past the end")
    }

    @Test
    fun `a delay that grows while the sound plays slows the position and never stops it`() {
        val clock = HeardClock(RATE, maxLagMs = 1_000, waitMs = 500)
        clock.stamp(head = 0, stampFrames = 0, stampNanos = NOW, nowNanos = NOW)
        clock.startAt(0, head = 0)
        var head = 24_000L
        var last = clock.positionMs(head, count = CHUNK, durationMs = 60_000)
        assertEquals(500L, last)
        // headphones came: the output now says 250 ms. Stood still for those 250 ms, the position would let the picture
        // carry its clock on from the last word and then seek back to it
        clock.stamp(head = head, stampFrames = head - 12_000, stampNanos = NOW, nowNanos = NOW)
        repeat(20) {
            head += CHUNK
            val now = clock.positionMs(head, count = CHUNK, durationMs = 60_000)
            assertTrue(now > last, "chunk $it: $now after $last")
            assertTrue(now - last >= CHUNK_MS / 2, "at least half speed: ${now - last} ms")
            last = now
        }
        // the delay is taken whole in the end: the head less 250 ms
        assertEquals((head - 12_000) * 1_000 / RATE, last)
    }

    @Test
    fun `a delay learnt before the position has moved is waited out whole`() {
        // the first play: the output has not said its delay yet, and the position waits
        val clock = HeardClock(RATE, maxLagMs = 1_000, waitMs = 500)
        clock.startAt(3_000, head = 0)
        assertEquals(3_000L, clock.positionMs(head = 4_800, count = CHUNK, durationMs = 60_000))
        // it says 200 ms before anything was heard: that is waited out whole, not half a chunk at a time
        clock.stamp(head = 9_600, stampFrames = 0, stampNanos = NOW, nowNanos = NOW)
        assertEquals(3_000L, clock.positionMs(head = 9_600, count = CHUNK, durationMs = 60_000))
        assertEquals(3_100L, clock.positionMs(head = 14_400, count = CHUNK, durationMs = 60_000))
        // a shorter delay later is taken at once: the ear is further on than was thought
        clock.stamp(head = 14_400, stampFrames = 14_400 - 4_800, stampNanos = NOW, nowNanos = NOW)
        assertEquals(3_200L, clock.positionMs(head = 14_400, count = CHUNK, durationMs = 60_000))
    }

    @Test
    fun `an output that never tells its delay is taken to have none after a while`() {
        val clock = HeardClock(RATE, maxLagMs = 1_000, waitMs = 100)
        clock.startAt(1_000, head = 0)
        // the first chunks only fill the track: the head stands, and no sound has been waited for yet
        repeat(4) { assertEquals(1_000L, clock.positionMs(head = 0, count = CHUNK, durationMs = 60_000)) }
        assertEquals(1_000L, clock.positionMs(head = 2_048, count = 2_048, durationMs = 60_000))
        assertEquals(1_000L, clock.positionMs(head = 4_096, count = 2_048, durationMs = 60_000))
        assertEquals(1_000L, clock.positionMs(head = 6_144, count = 2_048, durationMs = 60_000))
        assertFalse(clock.lagKnown)
        // 100 ms of sound waited for: the head alone, as before
        assertEquals(1_170L, clock.positionMs(head = 8_160, count = 2_048, durationMs = 60_000))
    }

    @Test
    fun `a picture carries the clock on only while the sound is heard moving`() {
        // playing on: 43 ms later, 43 ms further
        assertTrue(PictureCarry.carries(1_000, 0, true, 1_043, 43, true))
        // «play»: the word before was a pause
        assertFalse(PictureCarry.carries(1_000, 0, false, 1_000, 43, true))
        // the same place again: the sound has not reached the ear
        assertFalse(PictureCarry.carries(1_000, 0, true, 1_000, 43, true))
        // a seek back or far forward is a jump, not playing on
        assertFalse(PictureCarry.carries(5_000, 0, true, 2_000, 43, true))
        assertFalse(PictureCarry.carries(1_000, 0, true, 3_000, 43, true))
        // a pause
        assertFalse(PictureCarry.carries(1_000, 0, true, 1_043, 43, false))
    }

    private companion object {
        const val RATE = 48_000
        const val NOW = 5_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
        const val CHUNK = 2_048
        const val CHUNK_MS = CHUNK * 1_000L / RATE
    }
}
