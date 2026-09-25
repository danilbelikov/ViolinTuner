package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.sizeBytes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/** The encoder of takes on iOS: a second of A4 fed hop by hop comes back from the file as a second of sound. */
@OptIn(ExperimentalForeignApi::class)
class IosAacEncoderTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun `a second of sound is a second of AAC`() {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(HOP)
        var n = 0
        repeat(rate / HOP) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 12_000).roundToInt().toShort()
            assertTrue(encoder.offer(hop, hop.size))
        }
        assertTrue(encoder.finish())
        assertTrue(PlatformFile(path).sizeBytes() > 1_000, "the file holds the sound")

        val read = AVAudioFile(forReading = NSURL.fileURLWithPath(path), error = null)
        val seconds = read.length.toDouble() / read.fileFormat.sampleRate
        assertTrue(abs(seconds - n.toDouble() / rate) < 0.05, "read back $seconds s")
    }

    private companion object {
        const val HOP = 512
    }
}
