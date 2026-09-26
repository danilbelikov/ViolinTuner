package com.violinjourney.app.core.audio.backing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.recording.AacFileEncoder
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.testing.TestVideo
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real decoder of the device and `File.renameTo` (spec 5.25): three callers let go at once on one backing get the one
 * ready file, and no `.partial` is left. Before the lock the later ones cut the earlier one's `.partial` short and got null.
 * The protocol itself is guarded by `SingleFlightBackingPcmTest` on the JVM. Emulator only, with
 * `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
 */
@RunWith(AndroidJUnit4::class)
class BackingPcmCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "backing-pcm-test").apply { mkdirs() }
    private val unpacked = File(context.cacheDir, "backing-pcm")
    private val files = object : BackingFiles {
        override fun newFile(extension: String) = File(directory, "${UUID.randomUUID()}.$extension")

        override fun existing(name: String) = File(directory, name).takeIf { it.isFile }

        override fun delete(name: String) {
            File(directory, name).delete()
        }

        override fun deleteOrphans(kept: Set<String>) = Unit
    }
    private val source = files.newFile("m4a")
    private val stem = source.nameWithoutExtension

    @After
    fun tearDown() {
        directory.deleteRecursively()
        unpacked.listFiles().orEmpty().filter { it.name.startsWith(stem) }.forEach { it.delete() }
    }

    private fun tone(file: File, rate: Int, seconds: Int) {
        val encoder = AacFileEncoder(file, rate)
        val hop = ShortArray(512)
        var sample = 0L
        repeat(rate * seconds / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 220.0 * sample++ / rate) * 6_000).toInt().toShort()
            assertTrue(encoder.offer(hop, hop.size))
            Thread.sleep(4)
        }
        assertTrue(encoder.finish())
    }

    @Test
    fun threeCallersAtOnceGetOneReadyFile() {
        tone(source, 44_100, SECONDS)
        val backing = Backing(
            fileName = source.name, title = "a", durationMs = SECONDS * 1_000L, sampleRate = 44_100, channels = 1,
            sizeBytes = source.length(), addedAtEpochMs = 0,
        )
        val cache = BackingPcmCache(context, files)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        val callers = List(3) {
            pool.submit<File?> {
                go.await()
                cache.prepare(backing, 48_000)
            }
        }
        go.countDown()
        val prepared = callers.map { it.get(2, TimeUnit.MINUTES) }
        pool.shutdown()
        val ready = checkNotNull(cache.cached(backing, 48_000)) { "no ready file" }
        assertEquals(listOf(ready, ready, ready), prepared)
        assertTrue("some sound was unpacked", ready.length() > 0)
        assertEquals(listOf(ready.name), unpacked.listFiles().orEmpty().map { it.name }.filter { it.startsWith(stem) })
    }

    /** A copy without a sound track — an import from iOS this extractor reads otherwise — is no backing, not a fall (spec 5.25). */
    @Test
    fun aCopyWithoutSoundIsNoBackingNotAFall() {
        val silent = TestVideo.make(files.newFile("mp4"), seconds = 1, withSound = false)
        val backing = Backing(
            fileName = silent.name, title = "a", durationMs = 1_000, sampleRate = 48_000, channels = 1, sizeBytes = silent.length(), addedAtEpochMs = 0,
        )
        assertNull(BackingPcmCache(context, files).prepare(backing, 48_000))
        val left = unpacked.listFiles().orEmpty().map { it.name }.filter { it.startsWith(silent.nameWithoutExtension) }
        assertTrue("left behind: $left", left.isEmpty())
    }

    private companion object {
        const val SECONDS = 10
    }
}
