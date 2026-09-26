package com.violinjourney.app.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The files of the iOS app as streams are closed once for all: the number of a closed descriptor may be another file's by then. */
@OptIn(ExperimentalForeignApi::class)
class IosFileStreamsTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also { it.makeDirectories() }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    private fun write(file: PlatformFile, bytes: ByteArray) {
        file.openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    @Test
    fun `closing a file twice leaves the next opened file alone`() {
        val first = folder.child("a").also { write(it, byteArrayOf(1)) }
        val second = folder.child("b").also { write(it, ByteArray(1_000) { 7 }) }
        val a = first.openInput()!!
        a.close()
        // the number of the descriptor just let go is the one the next open gets
        val b = second.openInput()!!
        a.close()
        val buffer = ByteArray(2_000)
        var read = 0
        b.use {
            while (true) {
                val got = it.read(buffer, read, buffer.size - read)
                if (got < 0) break
                read += got
            }
        }
        assertEquals(1_000, read)
    }

    @Test
    fun `a write after the close goes into no other file`() {
        val out = folder.child("a").openOutput()!!
        out.close()
        val second = folder.child("b").also { write(it, byteArrayOf(5)) }
        val keep = second.openOutput()!!
        assertFailsWith<okio.IOException> { out.write(byteArrayOf(9, 9, 9), 0, 3) }
        keep.close()
        assertEquals(0L, second.sizeBytes(), "the file opened after the close got nothing of the late write")
    }
}
