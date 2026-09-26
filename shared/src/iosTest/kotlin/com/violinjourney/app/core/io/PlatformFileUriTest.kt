package com.violinjourney.app.core.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `file:` URI of iOS is read back as the path it was made of: the data of the app lies in «Application Support», and a
 * path with a space cut out of its URI without undoing the escapes names no file — the photo of the camera was lost so.
 */
class PlatformFileUriTest {
    @Test
    fun `a path with a space goes to a uri and back`() {
        val path = "/tmp/x/Application Support/camera/a.jpg"
        val uri = PlatformFile(path).fileUri
        assertTrue(uri.startsWith("file:"), uri)
        assertTrue("Application%20Support" in uri, uri)
        assertEquals(path, pathOfFileUri(uri))
    }

    @Test
    fun `cyrillic and the signs a uri reserves come back as they were`() {
        for (path in listOf("/tmp/Ноты/лист 1.jpg", "/tmp/x/a#b%c?d.jpg", "/tmp/x/100% ready.mov")) {
            val uri = PlatformFile(path).fileUri
            assertTrue(uri.startsWith("file:"), uri)
            assertEquals(path, pathOfFileUri(uri), uri)
        }
    }

    @Test
    fun `a bare absolute path is taken as it is`() {
        val path = "/tmp/x/Application Support/profile/p.jpg"
        assertEquals(path, pathOfFileUri(path))
    }

    @Test
    fun `what is not a file of this phone has no path`() {
        for (uri in listOf("content://media/external/images/1", "https://example.com/a.jpg", "a.jpg", "file:a.jpg", "")) {
            assertNull(pathOfFileUri(uri), uri)
        }
    }
}
