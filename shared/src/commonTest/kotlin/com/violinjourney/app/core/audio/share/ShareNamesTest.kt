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

    @Test
    fun `a sound brought in from a file is told as sound by its extension`() {
        // plan D48: sent as a video an mp3 would reach only the apps that play pictures
        val types = mapOf(
            "Урок · 21 сентября.mp3" to "audio/mpeg", "a.wav" to "audio/wav", "a.m4a" to "audio/mp4", "a.aac" to "audio/aac",
            "a.ogg" to "audio/ogg", "a.opus" to "audio/opus", "a.flac" to "audio/flac", "a.3ga" to "audio/3gpp", "a.AMR" to "audio/amr",
        )
        types.forEach { (name, type) ->
            assertEquals(type, ShareNames.mimeTypeOf(name), name)
            assertEquals(type, ShareNames.soundTypeOf(name), name)
        }
        assertEquals("video/mp4", ShareNames.mimeTypeOf("x.mp4"))
        assertEquals("video/quicktime", ShareNames.mimeTypeOf("x.mov"))
        assertEquals("video/*", ShareNames.mimeTypeOf("x.mkv"), "anything else is a video as it was shot, as before")
    }

    @Test
    fun `what a recording without a picture sends is sound whatever its extension - and the words of a name say nothing`() {
        // review of stage 98a: the name sent is made of the title — «Урок · 21 сентября.webm» — and says nothing of the file
        for (name in listOf("Урок · 21 сентября.webm", "Урок.oga", "Урок.mka", "Урок.audio", "Концерт 24.10", "Урок")) {
            assertEquals("audio/*", ShareNames.soundTypeOf(name), name)
        }
        assertEquals("video/mp4", ShareNames.mimeTypeOf("Final.sound.check.mp4"), "a video named so is still a video")
        assertEquals("video/quicktime", ShareNames.mimeTypeOf("Final.sound.check.mov"))
    }

    @Test
    fun `a sound sent as it came keeps its extension - a container named by its sound`() {
        assertEquals("Осенний концерт · 24 октября.mp3", ShareNames.originalAudioFileName("Осенний концерт · 24 октября", "1f2e.sound.mp3"))
        assertEquals("Урок · 21 сентября.wav", ShareNames.originalAudioFileName("Урок · 21 сентября", "/files/sessions/a.sound.WAV"))
        assertEquals("Урок.3ga", ShareNames.originalAudioFileName("Урок", "a.sound.3gp"))
        assertEquals("Урок.3ga", ShareNames.originalAudioFileName("Урок", "a.sound.3gpp"))
        assertEquals("Урок.m4a", ShareNames.originalAudioFileName("Урок", "a.sound.mp4"))
        assertEquals("Менуэт · 18 сентября.m4a", ShareNames.originalAudioFileName("Менуэт · 18 сентября", "take.m4a"), "the sound of a take is an m4a")
        assertEquals("Урок.m4a", ShareNames.originalAudioFileName("Урок", "noextension"))
    }
}
