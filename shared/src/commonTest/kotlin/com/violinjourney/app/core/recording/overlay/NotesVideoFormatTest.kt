package com.violinjourney.app.core.recording.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class NotesVideoFormatTest {
    private val config = NotesVideoConfig()

    @Test
    fun `a 1080p portrait keeps its size and goes at about 5 Mbit a second`() {
        val format = NotesVideoFormat.of(1_080, 1_920, 30f, config)
        assertEquals(NotesVideoFormat(1_080, 1_920, 30, 4_976_640), format)
        assertEquals(1_080, format.shortSide)
    }

    @Test
    fun `a 4K video comes down to 1080 on its short side — a small one is not enlarged`() {
        assertEquals(1_080 to 1_920, NotesVideoFormat.of(2_160, 3_840, 30f, config).let { it.width to it.height })
        assertEquals(1_920 to 1_080, NotesVideoFormat.of(3_840, 2_160, 30f, config).let { it.width to it.height })
        assertEquals(640 to 360, NotesVideoFormat.of(640, 360, 30f, config).let { it.width to it.height })
        // an odd side becomes even
        assertEquals(640 to 360, NotesVideoFormat.of(641, 361, 30f, config).let { it.width to it.height })
    }

    @Test
    fun `no more than 30 frames a second — and 30 when the file does not say`() {
        assertEquals(30, NotesVideoFormat.of(1_080, 1_920, 60f, config).frameRate)
        assertEquals(24, NotesVideoFormat.of(1_080, 1_920, 23.976f, config).frameRate)
        assertEquals(30, NotesVideoFormat.of(1_080, 1_920, 0f, config).frameRate)
    }

    @Test
    fun `the bit rate stays between 1 and 6 Mbit a second`() {
        assertEquals(1_000_000, NotesVideoFormat.of(320, 240, 15f, config).bitrate)
        assertEquals(2_211_840, NotesVideoFormat.of(720, 1_280, 30f, config).bitrate)
        assertEquals(6_000_000, NotesVideoFormat.of(1_080, 1_920, 30f, config.copy(bitsPerPixel = 0.2)).bitrate)
    }

    @Test
    fun `a two-minute 1080p take with mono sound weighs about 78 MB`() {
        val format = NotesVideoFormat.of(1_080, 1_920, 30f, config)
        val bytes = NotesVideoFormat.bytes(format, soundBitrate = 128_000, durationMs = 125_000, config = config)
        assertEquals(81_674_240L, bytes)
        assertEquals(77, (bytes / (1024 * 1024)).toInt())
    }
}
