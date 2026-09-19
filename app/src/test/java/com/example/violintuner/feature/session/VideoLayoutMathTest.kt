package com.example.violintuner.feature.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLayoutMathTest {
    private val eps = 0.01f
    private val wide = 16f / 9f
    private val tall = 9f / 16f

    @Test
    fun `a wide video fills the width, a tall one stands at forty percent of the screen`() {
        assertEquals(213.75f, VideoLayoutMath.blockHeight(wide, availableWidth = 380f, screenHeight = 892f), eps)
        assertEquals(356.8f, VideoLayoutMath.blockHeight(tall, availableWidth = 380f, screenHeight = 892f), eps)
        val frame = VideoLayoutMath.frameAt(tall, 380f, 356.8f, collapse = 0f)
        assertEquals(200.7f, frame.width, eps)
        assertEquals("centred between its margins", (380f - 200.7f) / 2, frame.x, eps)
    }

    @Test
    fun `a video that has not said what it is is taken for sixteen by nine`() {
        assertEquals(wide, VideoLayoutMath.aspectOf(0, 0), eps)
        assertEquals(tall, VideoLayoutMath.aspectOf(1080, 1920), eps)
    }

    @Test
    fun `scrolling shrinks the block into the row and no further`() {
        val block = 213.75f
        assertEquals(0f, VideoLayoutMath.collapse(block, scrolled = 0f), eps)
        assertEquals(0.5f, VideoLayoutMath.collapse(block, scrolled = (block - 72f) / 2), eps)
        assertEquals(1f, VideoLayoutMath.collapse(block, scrolled = 5_000f), eps)
        // a screen so low that the block is a row already
        assertEquals(1f, VideoLayoutMath.collapse(60f, scrolled = 0f), eps)
    }

    @Test
    fun `the row holds the mini frame on the left, in the proportions of the video`() {
        val row = VideoLayoutMath.frameAt(wide, 380f, 213.75f, collapse = 1f)
        assertEquals(0f, row.x, eps)
        assertEquals(128f, row.width, eps)
        assertEquals(72f, row.height, eps)
        assertEquals(72f, row.blockHeight, eps)
        assertEquals(1f, row.wordsAlpha, eps)
        val tallRow = VideoLayoutMath.frameAt(tall, 380f, 356.8f, collapse = 1f)
        assertEquals(72f, tallRow.height, eps)
        assertEquals(40.5f, tallRow.width, eps)
    }

    @Test
    fun `the words of the row come in over the last part of the way, the frame moves all the way`() {
        assertEquals(0f, VideoLayoutMath.frameAt(wide, 380f, 213.75f, 0.6f).wordsAlpha, eps)
        assertEquals(0.5f, VideoLayoutMath.frameAt(wide, 380f, 213.75f, 0.8f).wordsAlpha, eps)
        val half = VideoLayoutMath.frameAt(wide, 380f, 213.75f, 0.5f)
        assertEquals((380f + 128f) / 2, half.width, eps)
        assertEquals((213.75f + 72f) / 2, half.blockHeight, eps)
    }

    @Test
    fun `the strip shows four seconds either side of the cursor`() {
        val segments = listOf(0L to 1_000L, 5_000L to 7_000L, 9_500L to 12_000L, 20_000L to 21_000L)
        val pieces = VideoLayoutMath.strip(segments, cursorMs = 6_000)
        assertEquals(listOf(1, 2), pieces.map { it.index })
        assertEquals(0.375f, pieces[0].from, eps)
        assertEquals(0.625f, pieces[0].to, eps)
        assertEquals(0.9375f, pieces[1].from, eps)
        assertEquals("cut at the edge of the window", 1f, pieces[1].to, eps)
        assertTrue(VideoLayoutMath.strip(segments, cursorMs = 16_000).isEmpty())
    }
}
