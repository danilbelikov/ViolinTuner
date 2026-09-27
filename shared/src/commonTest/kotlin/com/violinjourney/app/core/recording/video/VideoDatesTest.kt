package com.violinjourney.app.core.recording.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class VideoDatesTest {
    @Test
    fun `the date of a video is utc with or without milliseconds`() {
        assertEquals(Instant.parse("2026-09-18T10:15:00Z").toEpochMilliseconds(), VideoDates.parse("20260918T101500.000Z"))
        assertEquals(Instant.parse("2026-09-18T10:15:07Z").toEpochMilliseconds(), VideoDates.parse("20260918T101507Z"))
    }

    @Test
    fun `a file that does not know its date says nineteen-o-four and that is no date`() {
        assertNull(VideoDates.parse("19040101T000000.000Z"))
    }

    @Test
    fun `nonsense is no date`() {
        assertNull(VideoDates.parse(null))
        assertNull(VideoDates.parse(" "))
        assertNull(VideoDates.parse("yesterday"))
    }
}
