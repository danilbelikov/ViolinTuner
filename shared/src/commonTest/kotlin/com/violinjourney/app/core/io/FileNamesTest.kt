package com.violinjourney.app.core.io

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNamesTest {
    @Test
    fun `the names the app makes are its own`() {
        listOf("a.jpg", "avatar-1790000000000.jpg", "3F2504E0-4F89-41D3-9A0C-0305E82C3301.m4a", "take-thumb.jpg", "..jpg", "a..b").forEach {
            assertTrue(isOwnFileName(it), it)
        }
    }

    @Test
    fun `a name that reaches outside its folder is not`() {
        listOf("", ".", "..", "../databases/violin.db", "a/b", "/etc/x", "a\\b", "a\u0000b").forEach {
            assertFalse(isOwnFileName(it), it)
        }
    }
}
