package com.violinjourney.app.feature.share

import com.violinjourney.app.feature.sound.SoundCaption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chip of the format of each variant of «Поделиться» (spec 3.36.5) and the size of a large file (spec 3.19). */
class ShareInfoTest {
    private val sound = ShareInfo(
        sessionId = 1, fileName = "Менуэт · 27 сентября.m4a", durationMs = 125_000, processedBytes = 2_000_000, originalBytes = 1_900_000,
        caption = SoundCaption.Custom, message = "Менуэт · 82 % · 27 сентября", backing = true,
    )
    private val video = sound.copy(videoFileName = "Менуэт · 27 сентября.mp4", resolution = 1080)

    @Test
    fun `every variant of a sound is an m4a`() {
        ShareVariant.entries.forEach { assertEquals(".m4a", sound.extensionOf(it), "$it") }
    }

    @Test
    fun `a video made here is an mp4 and its sound alone an m4a`() {
        assertEquals(".mp4", video.extensionOf(ShareVariant.BACKING))
        assertEquals(".mp4", video.extensionOf(ShareVariant.PROCESSED))
        assertEquals(".mp4", video.extensionOf(ShareVariant.ORIGINAL))
        assertEquals(".m4a", video.extensionOf(ShareVariant.SOUND))
    }

    @Test
    fun `a video as shot on an iPhone is sent in its own container`() {
        val iphone = video.copy(originalVideoFileName = "Менуэт · 27 сентября.mov")
        assertEquals(".mov", iphone.extensionOf(ShareVariant.ORIGINAL))
        assertEquals(".mp4", iphone.extensionOf(ShareVariant.PROCESSED), "what is made here stays an mp4")
    }

    @Test
    fun `a file is large from one hundred mebibytes on`() {
        assertFalse(ShareInfo.isLarge(ShareInfo.LARGE_BYTES - 1))
        assertTrue(ShareInfo.isLarge(ShareInfo.LARGE_BYTES))
    }
}
