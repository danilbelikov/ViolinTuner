package com.violinjourney.app.core.audio.backing

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.recording.AacFileEncoder
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.testing.TestSound
import com.violinjourney.app.testing.TestVideo
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val unpacked = File(context.cacheDir, BackingPcmCache.DIRECTORY)
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

    private fun cacheOf() = BackingPcmCache(context, files, BackingConfig(), SystemWallClock)

    private fun backingOf(file: File, rate: Int, seconds: Int) = Backing(
        fileName = file.name, title = "a", durationMs = seconds * 1_000L, sampleRate = rate, channels = 1, sizeBytes = file.length(), addedAtEpochMs = 0,
    )

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
        val cache = cacheOf()
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
        assertNull(cacheOf().prepare(backing, 48_000))
        val left = unpacked.listFiles().orEmpty().map { it.name }.filter { it.startsWith(silent.nameWithoutExtension) }
        assertTrue("left behind: $left", left.isEmpty())
    }

    /**
     * A 24-bit WAV — a common export of a studio program — is the backing's sound, not noise (spec 5.25): until 27.09.2026
     * every sample was read as 16 bits. Prepared at another rate, it keeps its length and its pitch.
     */
    @Test
    fun aDeepWavBackingIsSoundNotNoise() {
        val wav = TestSound.wav(files.newFile("wav"), rate = 44_100, channels = 2, bits = 24, seconds = 2.0)
        val ready = checkNotNull(cacheOf().prepare(backingOf(wav, 44_100, 2), 48_000)) { "not unpacked" }
        BackingPcmReader(ready).use { reader ->
            assertTrue("${reader.frames} frames", abs(reader.frames - 2 * 48_000) < 480)
            val left = FloatArray(24_000)
            val right = FloatArray(24_000)
            reader.read(24_000, 24_000, 1f, left, right)
            for (side in listOf(left, right)) {
                assertEquals(440.0, TestSound.pitchOf(side, side.size, 48_000), 10.0)
                assertTrue("level ${side.maxOf { abs(it) }}", side.maxOf { abs(it) } in 0.2f..0.3f)
            }
        }
    }

    /** The unpack feeds the codec all it takes, as `PcmDecoder` does: until 27.09.2026 it ran at about twice real time. */
    @Test
    fun unpackingIsManyTimesFasterThanTheSound() {
        tone(source, 44_100, SPEED_SECONDS)
        val started = System.nanoTime()
        checkNotNull(cacheOf().prepare(backingOf(source, 44_100, SPEED_SECONDS), 48_000)) { "not unpacked" }
        val timesRealTime = SPEED_SECONDS / ((System.nanoTime() - started) / 1e9)
        Log.i("SoundChainSpeed", "backing unpack: ${"%.1f".format(timesRealTime)}x real time")
        assertTrue("unpacking runs at ${"%.1f".format(timesRealTime)}x", timesRealTime > 3.0)
    }

    /** A cache of its own for the sweeps: the app's real one on the emulator is left as it is. */
    private fun sweptCache(): Pair<BackingPcmCache, File> {
        val cacheDir = File(directory, "cache").apply { mkdirs() }
        val wrapped = object : ContextWrapper(context) {
            override fun getCacheDir(): File = cacheDir
        }
        return BackingPcmCache(wrapped, files, BackingConfig(), SystemWallClock) to cacheDir
    }

    /** Made until 27.09.2026 at the container's rate and 16 bits whatever the codec gave: the whole old folder goes. */
    @Test
    fun theOldFolderIsSwept() {
        val (cache, cacheDir) = sweptCache()
        val old = File(cacheDir, "backing-pcm").apply { mkdirs() }
        File(old, "$stem-48000.pcm").writeBytes(ByteArray(16))
        val kept = File(cacheDir, BackingPcmCache.DIRECTORY).apply { mkdirs() }.let { File(it, "$stem-48000.pcm") }.apply { writeBytes(ByteArray(16)) }
        cache.deleteOrphans(setOf(source.name))
        assertFalse(old.exists())
        assertTrue(kept.exists())
    }

    /** A `.partial` left by a process killed mid-unpack goes after an hour untouched; one being written stays. */
    @Test
    fun aPartialNobodyWritesToIsSweptAndAFreshOneStays() {
        val (cache, cacheDir) = sweptCache()
        val folder = File(cacheDir, BackingPcmCache.DIRECTORY).apply { mkdirs() }
        val stale = File(folder, "$stem-48000.pcm.partial").apply { writeBytes(ByteArray(16)) }
        assertTrue(stale.setLastModified(System.currentTimeMillis() - 2 * 60 * 60_000L))
        val fresh = File(folder, "$stem-44100.pcm.partial").apply { writeBytes(ByteArray(16)) }
        cache.deleteOrphans(emptySet())
        assertFalse("the stale partial", stale.exists())
        assertTrue("the fresh partial", fresh.exists())
    }

    private companion object {
        const val SECONDS = 10
        const val SPEED_SECONDS = 20
    }
}
