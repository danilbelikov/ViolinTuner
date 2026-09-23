package com.violinjourney.app.core.audio.share

import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `a name of nothing but signs is still a name, and a long one is cut`() {
        assertEquals("recording.m4a", ShareNames.fileName("???"))
        assertEquals("recording.m4a", ShareNames.fileName("..."))
        assertEquals(80 + 4, ShareNames.fileName("я".repeat(200)).length)
    }
}
