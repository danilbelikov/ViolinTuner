package com.violinjourney.app.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorTextTest {
    @Test
    fun `paths uris and quoted names are masked`() {
        assertEquals(
            "<path>: open failed: EACCES (Permission denied)",
            ErrorText.scrub("/storage/emulated/0/Documents/Моя копия.zip: open failed: EACCES (Permission denied)"),
        )
        assertEquals(
            "cannot open <uri>",
            ErrorText.scrub("cannot open content://com.android.externalstorage.documents/document/primary%3AМоя.zip"),
        )
        assertEquals("The file <name> couldn’t be opened.", ErrorText.scrub("The file “Моя песня.mp3” couldn’t be opened."))
        assertEquals("файл <name> не найден", ErrorText.scrub("файл «Соната.m4a» не найден"))
        assertEquals("no such table: <name>", ErrorText.scrub("no such table: \"sessions\""))
        assertEquals(
            "at <path>: No such file or directory",
            ErrorText.scrub("at /var/mobile/Containers/Data/Application/1A2B/Library/Application Support/sessions/x.m4a: No such file or directory"),
        )
    }

    @Test
    fun `an apostrophe is a word and not a quote`() {
        assertEquals("can't open <name>, it doesn't exist", ErrorText.scrub("can't open 'x', it doesn't exist"))
    }

    @Test
    fun `the codes that tell the cause stay`() {
        listOf(
            "write failed: EIO (I/O error)",
            "Error code: 13, message: database or disk is full",
            "write failed: ENOSPC (No space left on device)",
            "The operation couldn’t be completed. (NSCocoaErrorDomain error 4.)",
        ).forEach { assertEquals(it, ErrorText.scrub(it)) }
        assertNull(ErrorText.scrub(null))
    }
}
