package com.violinjourney.app.ios

import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileName
import kotlin.concurrent.AtomicInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalKey
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.kCMTimeZero
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.posix.memset
import platform.posix.usleep

/**
 * The thumbnail of a video on iOS (spec 3.38, 5.31), from a real file: the frame of the middle; a dark middle gives way to the first
 * frame; a thumbnail made again takes the place of the old one whole.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVideoThumbTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val videos = IosVideoFiles(RepertoireConfig(), Dispatchers.Default)
    private val made = mutableListOf<PlatformFile>()

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        made.forEach(videos::discard)
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    @Test
    fun `the thumbnail is the frame of the middle`() {
        val video = write(seconds = 4) { second -> listOf(60, 90, 200, 120)[second] }
        assertTrue(videos.makeThumb(video))
        val grey = greyOf(assertNotNull(videos.thumbOf(video.fileName), "the thumbnail beside the video"))
        assertTrue(grey in 170..230, "the middle, not the start: $grey")
    }

    @Test
    fun `a dark middle gives way to the first frame and a thumbnail made again replaces the old one`() {
        val video = write(seconds = 4) { second -> if (second == 1 || second == 2) DARK else 150 }
        assertTrue(videos.makeThumb(video))
        val thumb = assertNotNull(videos.thumbOf(video.fileName))
        assertTrue(greyOf(thumb) > 100, "the first frame, not the dark middle: ${greyOf(thumb)}")

        assertTrue(("not a picture" as NSString).writeToFile(thumb.path, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        assertTrue(videos.makeThumb(video), "a name that is taken does not stop it")
        assertTrue(greyOf(assertNotNull(videos.thumbOf(video.fileName))) > 100)
        val folderOfThumbs = thumb.path.substringBeforeLast('/')
        val partial = IosFolders.names(folderOfThumbs).filter { it.endsWith(".part") }
        assertEquals(emptyList(), partial, "no partial file is left")
    }

    /** [seconds] of a flat grey picture at [FPS], a key frame every second; [grey] is its level in each second. */
    private fun write(seconds: Int, grey: (Int) -> Int): PlatformFile {
        val file = PlatformFile("$folder/${NSUUID().UUIDString}.mp4")
        val writer = AVAssetWriter(uRL = NSURL.fileURLWithPath(file.path), fileType = AVFileTypeMPEG4, error = null)
        val input = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf<Any?, Any?>(
                AVVideoCodecKey to AVVideoCodecTypeH264,
                AVVideoWidthKey to WIDTH,
                AVVideoHeightKey to HEIGHT,
                AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(AVVideoMaxKeyFrameIntervalKey to FPS),
            ),
        )
        val adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput = input, sourcePixelBufferAttributes = null)
        writer.addInput(input)
        assertTrue(writer.startWriting(), "the writer starts: ${writer.error?.localizedDescription}")
        writer.startSessionAtSourceTime(kCMTimeZero.readValue())
        repeat(seconds * FPS) { frame ->
            while (!input.readyForMoreMediaData) usleep(POLL_US)
            memScoped {
                val buffer = alloc<CVPixelBufferRefVar>()
                assertEquals(0, CVPixelBufferCreate(null, WIDTH.toULong(), HEIGHT.toULong(), kCVPixelFormatType_32BGRA, null, buffer.ptr))
                val pixels = buffer.value
                CVPixelBufferLockBaseAddress(pixels, 0u)
                CVPixelBufferGetBaseAddress(pixels)?.let { memset(it, grey(frame / FPS), CVPixelBufferGetDataSize(pixels)) }
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
        return file.also { made += it }
    }

    /** The mean level of red of a grey picture, over a coarse grid. */
    private fun greyOf(thumb: PlatformFile): Int {
        val image = assertNotNull(UIImage.imageWithContentsOfFile(thumb.path)?.CGImage, "a picture")
        val pixels = UByteArray(GRID * GRID * RGBA)
        pixels.usePinned { pinned ->
            val space = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0), GRID.convert(), GRID.convert(), BITS.convert(), (GRID * RGBA).convert(), space,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            )
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, GRID.toDouble(), GRID.toDouble()), image)
            CGContextRelease(context)
            CGColorSpaceRelease(space)
        }
        return (0 until GRID * GRID).sumOf { pixels[it * RGBA].toInt() } / (GRID * GRID)
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
        const val FPS = 15
        const val POLL_US = 2_000u
        const val DARK = 4
        const val GRID = 8
        const val RGBA = 4
        const val BITS = 8
    }
}
