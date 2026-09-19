package com.example.violintuner.core.recording.video

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDatesTest {
    @Test
    fun `the date of a video is utc, with or without milliseconds`() {
        assertEquals(Instant.parse("2026-09-18T10:15:00Z").toEpochMilli(), VideoDates.parse("20260918T101500.000Z"))
        assertEquals(Instant.parse("2026-09-18T10:15:07Z").toEpochMilli(), VideoDates.parse("20260918T101507Z"))
    }

    @Test
    fun `a file that does not know its date says nineteen-o-four, and that is no date`() {
        assertNull(VideoDates.parse("19040101T000000.000Z"))
    }

    @Test
    fun `nonsense is no date`() {
        assertNull(VideoDates.parse(null))
        assertNull(VideoDates.parse(" "))
        assertNull(VideoDates.parse("yesterday"))
    }
}
