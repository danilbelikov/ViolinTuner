package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.audio.share.IosSoundRenderer
import com.violinjourney.app.core.audio.share.RenderBacking
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.time.SystemWallClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/** The backing on iOS (spec 3.32, 5.25): a file of another rate becomes the PCM of the take's rate, and a take is sent with it in stereo. */
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

    private fun tone(path: String, rate: Int, seconds: Int, hz: Double) {
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(seconds * rate / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * hz * (n++) / rate) * 8_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())
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
}
