package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.recording.OpenedPcm
import com.violinjourney.app.core.recording.PcmFileOpener
import com.violinjourney.app.core.recording.PcmSource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.AVFAudio.AVAudioFile
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.posix.memset
import platform.posix.usleep

/**
 * The file that is sent on iOS: a second of a take through the chamber hall is a playable `.m4a`, longer by the hall; a
 * sound track the reader gave up on half-way is no file at all.
 */
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

    @Test
    fun `a sound the reader gave up on half-way is not sent cut`() = runTest {
        val config = SoundConfig()
        val source = PlatformFile("$folder/take.m4a")
        val target = PlatformFile("$folder/sent.m4a")
        val broken = IosSoundRenderer(config, Dispatchers.Default, halfSecond(breaks = true))
        assertFalse(broken.render(source, SoundRules.off(config), target) {}, "a cut sound is no file to send")
        assertFalse(target.exists(), "nothing is left under the name")

        val whole = IosSoundRenderer(config, Dispatchers.Default, halfSecond(breaks = false))
        assertTrue(whole.render(source, SoundRules.off(config), target) {}, "the same half second read to its end is sent")
    }

    @Test
    fun `a video is sent over what an export ended with the app left under its name`() = runTest {
        val video = PlatformFile("$folder/take.mp4")
        writePicture(video)
        val sound = PlatformFile("$folder/sound.m4a")
        val encoder = IosAacEncoder(sound, RATE)
        val hop = ShortArray(512)
        repeat(RATE / hop.size) { encoder.offer(hop, hop.size) }
        assertTrue(encoder.finish())
        // what an export the system ended together with the app left behind
        val target = PlatformFile("$folder/sent.mp4.part")
        val junk = ByteArray(1_000) { 7 }
        target.openOutput()!!.use { it.write(junk, 0, junk.size) }

        assertTrue(IosSoundRenderer(SoundConfig(), Dispatchers.Default).mux(video, sound, target), "the export is not refused")
        // as «Поделиться» renames it: a file is read by the type its name says
        val sentPath = "$folder/sent.mp4"
        assertTrue(NSFileManager.defaultManager.moveItemAtPath(target.path, sentPath, null))
        val sent = AVURLAsset(uRL = NSURL.fileURLWithPath(sentPath), options = null)
        assertEquals(1, sent.tracksWithMediaType(AVMediaTypeVideo).size, "the picture is there")
        assertEquals(1, sent.tracksWithMediaType(AVMediaTypeAudio).size, "the sound is there")
    }

    /** Half a second of a small grey picture at 30 frames a second, no sound: what a camera gives, in little. */
    private fun writePicture(file: PlatformFile) {
        val writer = AVAssetWriter(uRL = NSURL.fileURLWithPath(file.path), fileType = AVFileTypeMPEG4, error = null)
        val input = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf<Any?, Any?>(AVVideoCodecKey to AVVideoCodecTypeH264, AVVideoWidthKey to SIDE, AVVideoHeightKey to SIDE),
        )
        val adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput = input, sourcePixelBufferAttributes = null)
        writer.addInput(input)
        assertTrue(writer.startWriting(), "the writer starts: ${writer.error?.localizedDescription}")
        writer.startSessionAtSourceTime(CMTimeMake(0, FPS))
        repeat(FPS / 2) { frame ->
            while (!input.readyForMoreMediaData) usleep(POLL_US)
            memScoped {
                val buffer = alloc<CVPixelBufferRefVar>()
                assertEquals(0, CVPixelBufferCreate(null, SIDE.toULong(), SIDE.toULong(), kCVPixelFormatType_32BGRA, null, buffer.ptr))
                val pixels = buffer.value
                CVPixelBufferLockBaseAddress(pixels, 0u)
                CVPixelBufferGetBaseAddress(pixels)?.let { memset(it, GREY, CVPixelBufferGetDataSize(pixels)) }
                CVPixelBufferUnlockBaseAddress(pixels, 0u)
                assertTrue(adaptor.appendPixelBuffer(pixels, CMTimeMake(frame.toLong(), FPS)), "frame $frame")
                CVPixelBufferRelease(pixels)
            }
        }
        input.markAsFinished()
        val done = AtomicInt(0)
        writer.finishWritingWithCompletionHandler { done.value = 1 }
        while (done.value == 0) usleep(POLL_US)
        assertEquals(AVAssetWriterStatusCompleted, writer.status, "the picture is written: ${writer.error?.localizedDescription}")
    }

    /** Half a second of A4, then the end — or the reader giving up there. */
    private fun halfSecond(breaks: Boolean) = PcmFileOpener {
        object : OpenedPcm {
            private var left = RATE / 2
            private var n = 0
            override val sampleRate = RATE
            override val totalSamples = RATE.toLong()
            override val broken = breaks

            override fun read(out: ShortArray): Int {
                if (left == 0) return PcmSource.END
                val count = minOf(out.size, left)
                for (i in 0 until count) out[i] = (sin(2 * PI * 440 * (n++) / RATE) * 10_000).roundToInt().toShort()
                left -= count
                return count
            }

            override fun release() = Unit
        }
    }

    private companion object {
        const val RATE = 48_000
        const val SIDE = 64
        const val FPS = 30
        const val GREY = 0x80
        const val POLL_US = 1_000u
    }
}
