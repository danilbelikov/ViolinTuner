package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.Backing
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A backing is unpacked once at a time per backing and rate (spec 5.25): the piece, the player, «Звук», «Поделиться» and
 * the camera ask for it at once while the cache is empty, and two unpacks would write one `.partial`. Real threads: the
 * locks are the platform's own. The locks are one per process, so every test takes backings of its own.
 */
class SingleFlightBackingPcmTest {
    private val folder: File = Files.createTempDirectory("single-flight").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun backing() =
        Backing(fileName = "${UUID.randomUUID()}.m4a", title = "a", durationMs = 1_000, sampleRate = 44_100, channels = 1, sizeBytes = 0, addedAtEpochMs = 0)

    private fun keyOf(backing: Backing, rate: Int) = "${backing.fileName}@$rate"

    /** A new thread each time: [com.violinjourney.app.core.concurrent.PlatformLock] is reentrant, a thread of a pool could pass its own lock. */
    private fun <T> onThread(block: () -> T): Future<T> = FutureTask<T> { block() }.also { Thread(it).start() }

    /** Makes a file named after its key; the make of [gated] waits for [gate]. */
    private inner class FakePcm : SingleFlightBackingPcm() {
        val made = AtomicInteger()
        val entered = LinkedBlockingQueue<String>()
        val gate = CountDownLatch(1)

        @Volatile var gated: String? = null

        @Volatile var failuresLeft = 0

        @Volatile var throwing = false

        override fun cached(backing: Backing, sampleRate: Int): File? = fileOf(backing, sampleRate).takeIf { it.isFile }

        override fun make(backing: Backing, sampleRate: Int): File? {
            val key = keyOf(backing, sampleRate)
            made.incrementAndGet()
            entered.add(key)
            if (key == gated) gate.await(WAIT_S, TimeUnit.SECONDS)
            if (throwing) throw IllegalStateException("decoder refused")
            if (failuresLeft > 0) {
                failuresLeft--
                return null
            }
            return fileOf(backing, sampleRate).apply { writeText(key) }
        }

        override fun deleteOrphans(keptFiles: Set<String>) = Unit

        private fun fileOf(backing: Backing, sampleRate: Int) = File(folder, "${backing.fileName}-$sampleRate.pcm")
    }

    @Test
    fun `a second caller waits for the first and gets its file — unpacked once`() {
        val pcm = FakePcm()
        val a = backing()
        pcm.gated = keyOf(a, RATE)
        val first = onThread { pcm.prepare(a, RATE) }
        assertEquals(keyOf(a, RATE), pcm.entered.poll(WAIT_S, TimeUnit.SECONDS), "the first caller unpacks")
        val second = onThread { pcm.prepare(a, RATE) }
        assertNull(pcm.entered.poll(SETTLE_MS, TimeUnit.MILLISECONDS), "the second caller does not unpack it again")
        assertFalse(second.isDone, "the second caller waits for the first instead of giving up")
        pcm.gate.countDown()
        val one = assertNotNull(first.get(WAIT_S, TimeUnit.SECONDS))
        assertEquals(one, second.get(WAIT_S, TimeUnit.SECONDS))
        assertEquals(1, pcm.made.get())
    }

    @Test
    fun `another backing or another rate does not wait`() {
        val pcm = FakePcm()
        val a = backing()
        val b = backing()
        pcm.gated = keyOf(a, RATE)
        val blocked = onThread { pcm.prepare(a, RATE) }
        assertEquals(keyOf(a, RATE), pcm.entered.poll(WAIT_S, TimeUnit.SECONDS))
        assertNotNull(onThread { pcm.prepare(b, RATE) }.get(WAIT_S, TimeUnit.SECONDS), "another backing")
        assertNotNull(onThread { pcm.prepare(a, OTHER_RATE) }.get(WAIT_S, TimeUnit.SECONDS), "another rate")
        assertFalse(blocked.isDone)
        pcm.gate.countDown()
        assertNotNull(blocked.get(WAIT_S, TimeUnit.SECONDS))
    }

    @Test
    fun `a ready file is handed out without unpacking`() {
        val pcm = FakePcm()
        val a = backing()
        val made = assertNotNull(pcm.prepare(a, RATE))
        assertEquals(made, pcm.prepare(a, RATE))
        assertEquals(1, pcm.made.get())
    }

    @Test
    fun `a failed unpack lets the next caller try again`() {
        val pcm = FakePcm()
        val a = backing()
        pcm.failuresLeft = 1
        assertNull(pcm.prepare(a, RATE))
        assertNotNull(pcm.prepare(a, RATE))
        assertEquals(2, pcm.made.get())
    }

    @Test
    fun `an unpack that throws leaves the lock free`() {
        val pcm = FakePcm()
        val a = backing()
        pcm.throwing = true
        val failed = assertFailsWith<ExecutionException> { onThread { pcm.prepare(a, RATE) }.get(WAIT_S, TimeUnit.SECONDS) }
        assertIs<IllegalStateException>(failed.cause)
        pcm.throwing = false
        // another thread: the one that threw would pass its own lock even if it had kept it
        assertNotNull(onThread { pcm.prepare(a, RATE) }.get(WAIT_S, TimeUnit.SECONDS))
    }

    private companion object {
        const val RATE = 48_000
        const val OTHER_RATE = 44_100
        const val WAIT_S = 5L
        const val SETTLE_MS = 200L
    }
}
