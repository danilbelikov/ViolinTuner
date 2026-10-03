package com.violinjourney.app.core.recording.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoThumbsTest {
    /** A decoded frame: the moment it was asked at, and whether it is dark. */
    private class Frame(val at: Long, val dark: Boolean)

    private val asked = mutableListOf<Long>()
    private val discarded = mutableListOf<Long>()

    private fun pick(moments: List<Long>, frames: Map<Long, Boolean?>): Frame? = VideoThumbs.pick(
        moments,
        frameAt = { at -> asked += at; frames[at]?.let { dark -> Frame(at, dark) } },
        isDark = { it.dark },
        discard = { discarded += it.at },
    )

    @Test
    fun `a thumbnail is named after its video without the extension of any kind`() {
        assertEquals("1f2e-thumb.jpg", VideoThumbs.nameOf("1f2e.mp4"))
        assertEquals("A1B2-C3-thumb.jpg", VideoThumbs.nameOf("A1B2-C3.mov"), "a video of an iPhone that came to Android with a copy")
        assertEquals("clip-thumb.jpg", VideoThumbs.nameOf("clip"))
    }

    @Test
    fun `the frame is looked for in the middle first then at the start and a second in`() {
        assertEquals(listOf(60_000L, 0L, 1_000L), VideoThumbs.momentsMs(120_000))
        assertEquals(listOf(1_000L, 0L), VideoThumbs.momentsMs(2_000), "the middle of two seconds is the second in")
        assertEquals(listOf(0L, 1_000L), VideoThumbs.momentsMs(0), "a video that does not tell its length keeps the old rule")
    }

    @Test
    fun `a light middle is taken at once and nothing else is decoded`() {
        val frame = pick(VideoThumbs.momentsMs(120_000), mapOf(60_000L to false, 0L to false, 1_000L to false))
        assertEquals(60_000L, frame?.at)
        assertEquals(listOf(60_000L), asked)
        assertEquals(emptyList(), discarded)
    }

    @Test
    fun `a dark middle gives way to the first light frame and is let go`() {
        val frame = pick(VideoThumbs.momentsMs(120_000), mapOf(60_000L to true, 0L to false, 1_000L to false))
        assertEquals(0L, frame?.at)
        assertEquals(listOf(60_000L, 0L), asked)
        assertEquals(listOf(60_000L), discarded)
    }

    @Test
    fun `when every frame is dark the middle stays and the others go`() {
        val moments = VideoThumbs.momentsMs(120_000)
        val frame = pick(moments, mapOf(60_000L to true, 0L to true, 1_000L to true))
        assertEquals(60_000L, frame?.at)
        assertEquals(moments, asked)
        assertEquals(listOf(0L, 1_000L), discarded)
    }

    @Test
    fun `a moment without a frame is passed over`() {
        val frame = pick(VideoThumbs.momentsMs(120_000), mapOf(60_000L to null, 0L to true, 1_000L to false))
        assertEquals(1_000L, frame?.at)
        assertEquals(listOf(0L), discarded, "the dark start is let go once a light frame is found")
    }

    @Test
    fun `a video without a single frame has no thumbnail`() {
        assertNull(pick(VideoThumbs.momentsMs(120_000), emptyMap()))
        assertEquals(emptyList(), discarded)
    }

    @Test
    fun `the frame taken is the very one decoded`() {
        val light = Frame(0, dark = false)
        assertSame(light, VideoThumbs.pick(listOf(0L), frameAt = { light }, isDark = { it.dark }, discard = {}))
    }
}
