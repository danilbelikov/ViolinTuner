package com.violinjourney.app.ios

import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileUri
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile

/**
 * A picked video is kept under a name of its own with the extension of its file — of the name, not of the path: the
 * copies of the pickers lie in folders with dots in their names (`<bundle>.debug-Inbox`), and a file there may have no
 * extension at all.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVideoFilesTest {
    private val files = NSFileManager.defaultManager
    private val folder = "${NSTemporaryDirectory()}${NSUUID().UUIDString}.debug-Inbox"
    private val videos = IosVideoFiles(RepertoireConfig(), Dispatchers.Default)
    private val kept = mutableListOf<PlatformFile>()

    init {
        files.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun cleanUp() {
        kept.forEach(videos::discard)
        files.removeItemAtPath(folder, null)
    }

    @Test
    fun `a video without an extension in a folder with a dot keeps a name of its own`() = runTest {
        val stored = assertNotNull(videos.import(PlatformFile(bytes("$folder/clip")).fileUri), "the copy of the picker comes in")
        kept += stored
        val name = stored.path.substringAfterLast('/')
        assertTrue(name.endsWith(".mp4"), name)
        assertTrue(files.fileExistsAtPath(stored.path), stored.path)
        assertFalse("Inbox" in name, name)
    }

    @Test
    fun `a MOV keeps its container`() = runTest {
        val stored = assertNotNull(videos.import(PlatformFile(bytes("$folder/clip.MOV")).fileUri))
        kept += stored
        assertTrue(stored.path.endsWith(".mov"), stored.path)
    }

    private fun bytes(path: String): String {
        assertTrue(("not really a video" as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        return path
    }
}
