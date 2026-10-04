package com.violinjourney.app.core.ui.components

import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.pathOfFileUri
import kotlin.concurrent.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.IOException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSItemProviderRepresentationVisibilityAll
import platform.Foundation.NSProgress
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.memcpy

/**
 * The file of a video picked in the library (spec 3.19, 5.13, 0.94), with a provider that stands for the library: made only when asked,
 * copied into the picks while it is lent, stopped with the wait — and a file handed over after the stop leaves nothing behind.
 */
@OptIn(ExperimentalForeignApi::class)
class LibraryVideoTest {
    private val files = NSFileManager.defaultManager
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val clip = "$folder/clip.mov"
    private val bytes = ByteArray(4_096) { (it % 251).toByte() }

    init {
        files.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }.writeToFile(clip, atomically = true)
    }

    @AfterTest
    fun cleanUp() {
        files.removeItemAtPath(folder, null)
    }

    /**
     * A library with the video's own type, whose load handler [onLoad] decides what becomes of the video: its bytes, which the provider
     * writes into a file of its own and lends, as the library does — a provider cannot open a file of the test's folder for itself
     * («couldn't be opened»). It answers from a queue of its own, as the library does.
     */
    private fun library(onLoad: (hand: (NSData?, NSError?) -> Unit) -> NSProgress?): NSItemProvider = NSItemProvider().apply {
        registerDataRepresentationForTypeIdentifier(QUICKTIME, visibility = NSItemProviderRepresentationVisibilityAll) { completion ->
            onLoad { data, error ->
                dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { completion?.invoke(data, error) }
            }
        }
    }

    private fun picks(): Set<String> = files.contentsOfDirectoryAtPath(PickedCopies.root(), null).orEmpty().mapNotNull { it as? String }.toSet()

    @Test
    fun `the video is copied whole into a pick of its own`() = runTest {
        var asked = 0
        val video = LibraryVideo(
            library { hand ->
                asked++
                hand(NSData.dataWithContentsOfFile(clip), null)
                null
            },
        )
        assertEquals(0, asked, "nothing is made before the importer asks")
        val path = assertNotNull(pathOfFileUri(video.make()))
        try {
            assertEquals(1, asked)
            assertTrue(path.startsWith(PickedCopies.root() + "/"), "a copy of the app's own: $path")
            assertContentEquals(bytes, read(path))
        } finally {
            PickedCopies.release(path)
        }
    }

    @Test
    fun `a video the library does not hand over is an error with its reason`() = runTest {
        val video = LibraryVideo(
            library { hand ->
                hand(null, NSError.errorWithDomain("PHPhotosErrorDomain", code = 3164, userInfo = null))
                null
            },
        )
        val error = assertFailsWith<IOException> { video.make() }
        assertTrue(error.message.orEmpty().contains("3164"), "the reason travels to the statistics: ${error.message}")
    }

    @Test
    fun `a stop tells the library to stop and a file it hands over after that leaves nothing behind`() = runTest {
        val progress = NSProgress.discreteProgressWithTotalUnitCount(1)
        val stopped = CompletableDeferred<Unit>()
        progress.cancellationHandler = { stopped.complete(Unit) }
        val waiting = AtomicReference<((NSData?, NSError?) -> Unit)?>(null)
        val loading = CompletableDeferred<Unit>()
        val video = LibraryVideo(
            library { hand ->
                waiting.value = hand
                loading.complete(Unit)
                progress
            },
        )
        val before = picks()
        val wait = async(Dispatchers.Default) { video.make() }
        // real time: the library answers on a thread of its own, and the time of the test would run its timeouts out at once
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { loading.await() } }
        wait.cancel()
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { stopped.await() } }
        // the library hands the video over all the same, a moment too late: nothing of it is kept
        assertNotNull(waiting.value).invoke(NSData.dataWithContentsOfFile(clip), null)
        withContext(Dispatchers.Default) { delay(LATE_MS) }
        assertEquals(before, picks(), "no copy of a pick nobody waits for")
    }

    private fun read(path: String): ByteArray {
        val data = assertNotNull(NSData.dataWithContentsOfFile(path))
        return ByteArray(data.length.toInt()).also { out -> out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    private companion object {
        const val QUICKTIME = "com.apple.quicktime-movie"
        const val WAIT_MS = 10_000L

        /** Long enough for a file handed over too late to have been lent and let go. */
        const val LATE_MS = 1_000L
    }
}
