package com.violinjourney.app.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.EACCES
import platform.posix.EDQUOT
import platform.posix.EHOSTUNREACH
import platform.posix.EIO
import platform.posix.ENOENT
import platform.posix.ENOSPC
import platform.posix.ENXIO
import platform.posix.EPIPE
import platform.posix.ETIMEDOUT

/** A refusal of the file system on iOS tells its reason as a value — Darwin's words for an errno are not libcore's. */
@OptIn(ExperimentalForeignApi::class)
class StorageFailureTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also { it.makeDirectories() }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    @Test
    fun `an errno says why a place refused`() {
        listOf(EIO, ENXIO, ENOENT, EPIPE, ETIMEDOUT, EHOSTUNREACH).forEach { assertEquals(StorageFailure.GONE, storageFailureOf(it), "errno $it") }
        listOf(ENOSPC, EDQUOT).forEach { assertEquals(StorageFailure.NO_SPACE, storageFailureOf(it), "errno $it") }
        assertEquals(StorageFailure.OTHER, storageFailureOf(EACCES))
    }

    @Test
    fun `a stream that fails throws its reason`() {
        val out = folder.child("a").openOutput()!!
        out.close()
        // a closed descriptor: EBADF, a failure of its own
        val failure = assertFailsWith<StorageException> { out.write(byteArrayOf(1), 0, 1) }
        assertEquals(StorageFailure.OTHER, failure.failure)
    }
}
