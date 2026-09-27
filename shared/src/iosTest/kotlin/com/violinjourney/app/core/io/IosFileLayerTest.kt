package com.violinjourney.app.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The files of the iOS app as Android has them: a delete of a file never takes a folder, and room is counted as iOS counts it. */
@OptIn(ExperimentalForeignApi::class)
class IosFileLayerTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also { it.makeDirectories() }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    private fun write(file: PlatformFile, bytes: ByteArray) {
        file.openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    @Test
    fun `a file is deleted and so is an empty folder but never a folder with things in it`() {
        val file = folder.child("file").also { write(it, byteArrayOf(1)) }
        assertTrue(file.deleteFile())
        assertFalse(file.exists())
        val empty = folder.child("empty").also { it.makeDirectories() }
        assertTrue(empty.deleteFile())
        assertFalse(empty.exists())
        val full = folder.child("full").also { it.makeDirectories() }
        val inside = full.child("inside").also { write(it, byteArrayOf(2)) }
        assertFalse(full.deleteFile())
        assertTrue(full.exists() && inside.exists())
        assertTrue(folder.child("nothing").deleteFile(), "what is not there is gone")
    }

    @Test
    fun `a file is a file and a folder is not`() {
        val file = folder.child("file").also { write(it, byteArrayOf(1)) }
        assertTrue(file.isRegularFile())
        assertFalse(folder.isRegularFile())
        assertFalse(folder.child("nothing").isRegularFile())
    }

    @Test
    fun `the room for a write the person asked for is read from the system and is never less than the free blocks`() {
        // null would mean the key is not read at all, and the room would silently be NSFileSystemFreeSize again
        assertNotNull(folder.importantUsageBytes(), "volumeAvailableCapacityForImportantUsage is read")
        assertTrue(folder.availableBytes() > 0)
        // the rule on two readings: the disk of a busy machine changes between two reads of it, so the live numbers are
        // not compared with each other (the free blocks read after the room once came out larger and failed the run)
        assertEquals(9_000L, roomOf(importantUsage = 9_000, freeBlocks = 5_000))
        assertEquals(5_000L, roomOf(importantUsage = 3_000, freeBlocks = 5_000), "never less than the free blocks")
        assertEquals(5_000L, roomOf(importantUsage = null, freeBlocks = 5_000), "a volume that does not say")
    }
}
