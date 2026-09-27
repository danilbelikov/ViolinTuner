package com.violinjourney.app.core.audio.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareNamesTest {
    @Test
    fun `a title becomes a file name any system takes`() {
        assertEquals("Менуэт соль мажор · 18 сентября.m4a", ShareNames.fileName("Менуэт соль мажор · 18 сентября"))
        assertEquals("Соната № 1 Allegro.m4a", ShareNames.fileName("Соната № 1: Allegro"))
        assertEquals("a b c d.m4a", ShareNames.fileName("a/b\\c|d"))
        assertEquals("что.m4a", ShareNames.fileName("  что?  "))
        assertEquals("line break.m4a", ShareNames.fileName("line\nbreak"))
    }

    @Test
    fun `a name of nothing but signs is still a name and a long one is cut`() {
        assertEquals("recording.m4a", ShareNames.fileName("???"))
        assertEquals("recording.m4a", ShareNames.fileName("..."))
        assertEquals(80 + 4, ShareNames.fileName("я".repeat(200)).length)
    }

    @Test
    fun `a long name is cut whole characters at a time and fits the file system`() {
        val emoji = "\uD83C\uDFBB" // a violin: two UTF-16 units, one character
        val cut = ShareNames.fileName("я".repeat(79) + emoji + "я").removeSuffix(ShareNames.EXTENSION)
        assertEquals("я".repeat(79) + emoji, cut, "the eightieth character is the whole emoji — never half of it")
        assertFalse(cut.last().isHighSurrogate())

        val chinese = ShareNames.videoFileName("曲".repeat(100))
        val stem = chinese.removeSuffix(ShareNames.VIDEO_EXTENSION)
        assertEquals(66, stem.length, "three bytes a character, two hundred at most")
        assertTrue((chinese + ".part.sound.m4a").encodeToByteArray().size <= 255)
    }

    @Test
    fun `a video sent as shot keeps its container`() {
        assertEquals(".mov", ShareNames.videoExtensionOf("/x/sessions/A1.mov"))
        assertEquals(".mp4", ShareNames.videoExtensionOf("B.MP4"))
        assertEquals(".mp4", ShareNames.videoExtensionOf("/tmp/a.debug-Inbox/clip"), "a dot in a folder is no extension")
        assertEquals(".mp4", ShareNames.videoExtensionOf("clip."))
        assertEquals("Соната № 1 Allegro.mov", ShareNames.originalVideoFileName("Соната № 1: Allegro", "x.mov"))
        assertEquals("Соната № 1 Allegro.mp4", ShareNames.originalVideoFileName("Соната № 1: Allegro", "x.mp4"))
    }

    @Test
    fun `other apps are told the type of the file itself`() {
        assertEquals("audio/mp4", ShareNames.mimeTypeOf("Соната № 1 Allegro.m4a"))
        assertEquals("video/mp4", ShareNames.mimeTypeOf("Соната № 1 Allegro.mp4"))
        // shot on an iPhone and brought over by a copy: a video, never a sound for the apps that take one
        assertEquals("video/quicktime", ShareNames.mimeTypeOf("/cache/share/1f/Соната № 1 Allegro.MOV"))
        assertEquals("video/*", ShareNames.mimeTypeOf("Соната.m4v"))
    }
}
