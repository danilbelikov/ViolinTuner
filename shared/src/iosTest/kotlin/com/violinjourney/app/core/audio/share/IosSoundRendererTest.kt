package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/** The file that is sent on iOS: a second of a take through the chamber hall is a playable `.m4a`, longer by the hall. */
@OptIn(ExperimentalForeignApi::class)
class IosSoundRendererTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    @Test
    fun `a take through the hall rings on after its end`() = runTest {
        val rate = 48_000
        val source = PlatformFile("$folder/take.m4a")
        val encoder = IosAacEncoder(source, rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(rate / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 10_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())

        val config = SoundConfig()
        val settings = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
        val target = PlatformFile("$folder/sent.m4a")
        var progress = 0f
        assertTrue(IosSoundRenderer(config, Dispatchers.Default).render(source, settings, target) { progress = it })
        assertTrue(progress > 0.99f, "progress $progress")
        val sent = AVAudioFile(forReading = NSURL.fileURLWithPath(target.path), error = null)
        val seconds = sent.length.toDouble() / sent.fileFormat.sampleRate
        assertTrue(seconds > 1.5, "the hall rings on: $seconds s")
    }
}
