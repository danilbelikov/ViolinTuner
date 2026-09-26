package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.audio.recording.AacFile
import com.violinjourney.app.core.audio.share.IosSoundRenderer
import com.violinjourney.app.core.audio.share.RenderBacking
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.listNames
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.pathOfFileUri
import com.violinjourney.app.core.io.writeBytes
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.ui.components.copyKeepingName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/**
 * The backing on iOS (spec 3.32, 5.25): a file of another rate becomes the PCM of the take's rate, and a take is sent with it
 * in stereo; a file picked in Files is named after itself, and one that is refused leaves nothing behind.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackingTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val data = PlatformFile("$folder/data")
    private val caches = PlatformFile("$folder/caches")

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(data.path, true, null, null)
        NSFileManager.defaultManager.createDirectoryAtPath(caches.path, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    /** Written straight into the file: the take's encoder gives up on a queue of more than a second and a half of hops. */
    private fun tone(path: String, rate: Int, seconds: Int, hz: Double) {
        val aac = AacFile(path, rate, channels = 1)
        val hop = ShortArray(512)
        var n = 0
        repeat(seconds * rate / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * hz * (n++) / rate) * 8_000).roundToInt().toShort()
            assertTrue(aac.write(hop, hop.size))
        }
        assertTrue(aac.close())
    }

    @Test
    fun `a backing of 44_1 kHz is made ready at 48 kHz`() {
        val files = IosBackingFiles(data, SystemWallClock)
        val source = files.newFile("m4a")
        tone(source.path, 44_100, 2, 220.0)
        val backing = Backing(fileName = source.path.substringAfterLast('/'), title = "a", durationMs = 2_000, sampleRate = 44_100, channels = 1, sizeBytes = 0, addedAtEpochMs = 0)
        val pcm = IosBackingPcm(caches, files)
        val prepared = assertNotNull(pcm.prepare(backing, 48_000))
        assertEquals(prepared.path, pcm.cached(backing, 48_000)?.path)
        val reader = IosBackingPcmReader(prepared)
        // two seconds at the new rate, give or take the encoder's priming
        assertTrue(abs(reader.frames - 96_000) < 4_000, "frames ${reader.frames}")
        val left = FloatArray(1_000)
        val right = FloatArray(1_000)
        reader.read(48_000, 1_000, 1f, left, right)
        assertTrue(left.maxOf { abs(it) } > 0.1f, "sound in the middle")
        assertTrue(left.indices.all { left[it] == right[it] }, "a mono backing is doubled to both sides")
        reader.read(-2_000, 1_000, 1f, left, right)
        assertTrue(left.all { it == 0f }, "silence before its start")
        reader.close()
    }

    @Test
    fun `a take is sent with its backing in stereo`() = runTest {
        val take = PlatformFile("$folder/take.m4a")
        tone(take.path, 48_000, 2, 440.0)
        val files = IosBackingFiles(data, SystemWallClock)
        val source = files.newFile("m4a")
        tone(source.path, 48_000, 3, 220.0)
        val backing = Backing(fileName = source.path.substringAfterLast('/'), title = "a", durationMs = 3_000, sampleRate = 48_000, channels = 1, sizeBytes = 0, addedAtEpochMs = 0)
        val pcm = IosBackingPcm(caches, files)
        val target = PlatformFile("$folder/sent.m4a")
        val config = SoundConfig()
        val sent = IosSoundRenderer(config, Dispatchers.Default).renderWithBacking(
            take, SoundRules.off(config), RenderBacking(pcm = { rate -> pcm.prepare(backing, rate) }, offsetMs = 0, gainDb = -6f), target,
        ) {}
        assertTrue(sent)
        val file = AVAudioFile(forReading = NSURL.fileURLWithPath(target.path), error = null)
        assertEquals(2u, file.fileFormat.channelCount)
    }

    /**
     * A smoke test of the real path — AVAudioFile, the `.partial` and `moveItemAtPath`, which refuses a target that is there:
     * three callers let go at once, on a backing long enough for their unpacks to overlap, all get the one ready file. Before
     * the lock the later ones wrote the same `.partial` and got null. The protocol itself is guarded by
     * `SingleFlightBackingPcmTest` on the JVM; here the timing is the system's.
     */
    @Test
    fun `three callers at once get one ready file`() = runTest(timeout = 2.minutes) {
        val files = IosBackingFiles(data, SystemWallClock)
        val source = files.newFile("m4a")
        tone(source.path, 44_100, LONG_SECONDS, 220.0)
        val backing = Backing(
            fileName = source.path.substringAfterLast('/'), title = "a", durationMs = LONG_SECONDS * 1_000L, sampleRate = 44_100,
            channels = 1, sizeBytes = 0, addedAtEpochMs = 0,
        )
        val pcm = IosBackingPcm(caches, files)
        val go = CompletableDeferred<Unit>()
        val callers = List(3) {
            async(Dispatchers.Default) {
                go.await()
                pcm.prepare(backing, 48_000)?.path
            }
        }
        go.complete(Unit)
        val prepared = callers.awaitAll()
        val ready = assertNotNull(pcm.cached(backing, 48_000)).path
        assertEquals(listOf(ready, ready, ready), prepared)
        val left = PlatformFile("${caches.path}/backing-pcm").listNames()
        assertTrue(left.none { it.endsWith(".partial") }, "left behind: $left")
    }

    /**
     * A cache that cannot be written — a full disk, a `.partial` that cannot be opened — is no backing (spec 5.25), as on
     * Android: before, the throw went through the view model, the player or the microphone's thread and ended the app.
     */
    @Test
    fun `a backing that cannot be unpacked is no backing and no fall of the app`() {
        val files = IosBackingFiles(data, SystemWallClock)
        val source = files.newFile("m4a")
        tone(source.path, 44_100, 1, 220.0)
        val backing = Backing(fileName = source.path.substringAfterLast('/'), title = "a", durationMs = 1_000, sampleRate = 44_100, channels = 1, sizeBytes = 0, addedAtEpochMs = 0)
        // a file where the folder of the cache should be: nothing can be written under it
        val blocked = PlatformFile("$folder/blocked")
        val output = assertNotNull(blocked.openOutput())
        output.writeBytes("not a folder".encodeToByteArray())
        output.close()
        assertNull(IosBackingPcm(blocked, files).prepare(backing, 48_000))
        // the lock of the pair was let go: where the cache can be written, the same backing is unpacked
        assertNotNull(IosBackingPcm(caches, files).prepare(backing, 48_000))
    }

    @Test
    fun `a picked file keeps its name as the title`() {
        // what the picker lends: its own copy in the app's tmp
        val lent = "$folder/Концерт ля минор.m4a"
        tone(lent, 44_100, 2, 220.0)
        val uri = assertNotNull(copyKeepingName(NSURL.fileURLWithPath(lent), "$folder/tmp/"))
        val copy = assertNotNull(pathOfFileUri(uri))
        assertTrue(copy.endsWith("/Концерт ля минор.m4a"), copy)
        assertFalse(exists(lent), "the lent copy is moved — not copied a second time")
        val files = IosBackingFiles(data, SystemWallClock)
        val added = assertIs<BackingImport.Added>(IosBackingImporter(data, files, BackingConfig(), SystemWallClock).import(uri))
        assertEquals("Концерт ля минор", added.backing.title)
        assertFalse(exists(copy), "the copy is moved in")
        assertNotNull(files.existing(added.backing.fileName))
    }

    @Test
    fun `two picks of one name stay apart`() {
        val first = "$folder/a/Этюд.m4a"
        val second = "$folder/b/Этюд.m4a"
        NSFileManager.defaultManager.createDirectoryAtPath("$folder/a", true, null, null)
        NSFileManager.defaultManager.createDirectoryAtPath("$folder/b", true, null, null)
        tone(first, 44_100, 1, 220.0)
        tone(second, 44_100, 1, 330.0)
        val one = assertNotNull(pathOfFileUri(assertNotNull(copyKeepingName(NSURL.fileURLWithPath(first), "$folder/tmp/"))))
        val two = assertNotNull(pathOfFileUri(assertNotNull(copyKeepingName(NSURL.fileURLWithPath(second), "$folder/tmp/"))))
        assertTrue(one != two && exists(one) && exists(two), "$one — $two")
        assertEquals(one.substringAfterLast('/'), two.substringAfterLast('/'))
    }

    @Test
    fun `a refused file leaves no copy behind`() {
        val files = IosBackingFiles(data, SystemWallClock)
        val importer = IosBackingImporter(data, files, BackingConfig(maxDurationMs = 1_000), SystemWallClock)
        val long = "$folder/long.m4a"
        tone(long, 44_100, 2, 220.0)
        val longCopy = assertNotNull(copyKeepingName(NSURL.fileURLWithPath(long), "$folder/tmp/"))
        assertEquals(BackingImport.TooLong, importer.import(longCopy))
        assertFalse(exists(assertNotNull(pathOfFileUri(longCopy))), "a backing too long")
        val text = "$folder/x.mp3"
        val output = assertNotNull(PlatformFile(text).openOutput())
        output.writeBytes("not a sound".encodeToByteArray())
        output.close()
        val textCopy = assertNotNull(copyKeepingName(NSURL.fileURLWithPath(text), "$folder/tmp/"))
        assertEquals(BackingImport.Unreadable, importer.import(textCopy))
        assertFalse(exists(assertNotNull(pathOfFileUri(textCopy))), "a file that is no sound")
        assertTrue(PlatformFile("${data.path}/backings").listNames().isEmpty(), "nothing is taken in")
    }

    private fun exists(path: String) = NSFileManager.defaultManager.fileExistsAtPath(path)

    private companion object {
        /** Long enough for three unpacks to overlap even on a fast Mac. */
        const val LONG_SECONDS = 20
    }
}
