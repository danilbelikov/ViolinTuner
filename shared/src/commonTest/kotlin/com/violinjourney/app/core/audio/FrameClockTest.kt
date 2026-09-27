package com.violinjourney.app.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The frame-clock time of a take on the input's clock (spec 5.25) — what the shift of a take under a backing starts from. */
class FrameClockTest {
    @Test
    fun `a moment after the stamp and one before it are the samples between away from it`() {
        // the input said: sample 48 000 — the first of the second second — was captured at 5 s
        assertEquals(5_000_000_000L, FrameClock.nanosAt(1_000, anchorFrame = 48_000, anchorNanos = 5_000_000_000L, sampleRateHz = 48_000))
        assertEquals(5_500_000_000L, FrameClock.nanosAt(1_500, anchorFrame = 48_000, anchorNanos = 5_000_000_000L, sampleRateHz = 48_000))
        // a take that began before the stamp the input gave last
        assertEquals(4_500_000_000L, FrameClock.nanosAt(500, anchorFrame = 48_000, anchorNanos = 5_000_000_000L, sampleRateHz = 48_000))
    }

    @Test
    fun `at 44_1 kHz whole hundredths of a second fall on whole samples and the rest on the sample before`() {
        val anchor = 3_000_000_000L
        // 10 ms is 441 samples: exact
        listOf(0L, 10L, 250L, 1_230L, 60_000L).forEach { tMs ->
            assertEquals(anchor + tMs * 1_000_000, FrameClock.nanosAt(tMs, anchorFrame = 0, anchorNanos = anchor, sampleRateHz = 44_100), "$tMs ms")
        }
        // 9 ms is 396.9 samples: sample 396 — never more than one sample early, never late
        val sample = 1_000_000_000L / 44_100
        listOf(9L, 1_001L, 12_345L).forEach { tMs ->
            val early = anchor + tMs * 1_000_000 - FrameClock.nanosAt(tMs, anchorFrame = 0, anchorNanos = anchor, sampleRateHz = 44_100)
            assertTrue(early in 0..sample, "$tMs ms is $early ns early")
        }
    }

    @Test
    fun `a take of ten hours on a stamp of its start does not overflow`() {
        val tenHours = 10L * 3_600 * 1_000
        val nanos = FrameClock.nanosAt(tenHours, anchorFrame = 0, anchorNanos = 7_000_000_000L, sampleRateHz = 48_000)
        assertEquals(7_000_000_000L + tenHours * 1_000_000, nanos)
        // and a stamp given ten hours into the stream for its very start
        assertEquals(-tenHours * 1_000_000, FrameClock.nanosAt(0, anchorFrame = tenHours * 48, anchorNanos = 0, sampleRateHz = 48_000))
    }
}
