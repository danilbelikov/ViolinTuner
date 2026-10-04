package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.Rect
import com.violinjourney.app.core.audio.recording.Tone
import com.violinjourney.app.core.audio.recording.writeAacTones
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.io.PlatformFile
import kotlin.concurrent.AtomicInt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetTrack
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
import platform.AVFoundation.duration
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.transform
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
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
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.posix.memset
import platform.posix.usleep

/**
 * «Видео с нотами» on iOS (spec 3.37, 5.30): a real video in — the file is three seconds longer, its picture carries the lane and
 * ends with the summary, its sound is the track it was given; a turned video is drawn standing; a render given up leaves nothing.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotesVideoRendererTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val config = NotesVideoConfig()
    private val renderer = IosNotesVideoRenderer({ IosOverlayText.load() }, config, Dispatchers.Default)

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    /** One A, in tune, from the start of the video to its end. */
    private fun overlay(videoMs: Long): NotesOverlay {
        val (low, high) = NotesOverlays.heights(listOf(A4), config)
        return NotesOverlay(
            notes = listOf(OverlayNote(A4, 0, videoMs, Zone.IN_TUNE, 0.0)), lowMidi = low, highMidi = high, scorePercent = 100,
            toleranceCents = 8, title = "A", heading = "A", date = "3 октября", bestMidi = A4, drift = null, previous = null,
            ribbon = listOf(RecordingBar((videoMs / 50).toInt(), Zone.IN_TUNE)), config = config,
        )
    }

    private val words = OverlayWords(
        signature = "Анализируй свою игру в приложении Violin Journey", toleranceLine = "в строе · допуск ±8 ц", bestNote = "Лучшая нота", drift = "Что уходит",
        driftCents = null, driftNone = "ничего", previousTake = "Прошлый дубль", previousScore = null,
    )

    /** A video of [seconds] and its sound beside it: a grey picture, a tone of A4. */
    private fun take(seconds: Int, turned: Boolean = false): Pair<PlatformFile, PlatformFile> {
        val picture = PlatformFile("$folder/take.mp4")
        writePicture(picture, seconds, turned)
        val sound = PlatformFile("$folder/take.m4a")
        writeAacTones(sound.path, RATE, listOf(Tone(440.0, seconds.toDouble())))
        return picture to sound
    }

    @Test
    fun `the file is longer by the summary and carries the sound it was given`() = runTest {
        val (picture, sound) = take(SECONDS)
        val target = PlatformFile("$folder/notes.mp4")
        val progress = ArrayList<Float>()
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(SECONDS * 1_000L), words, target) { progress += it } })
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(target.path), options = null)
        assertEquals(1, asset.tracksWithMediaType(AVMediaTypeVideo).size, "the picture is there")
        assertEquals(1, asset.tracksWithMediaType(AVMediaTypeAudio).size, "the sound is there")
        val seconds = CMTimeGetSeconds(asset.duration)
        assertTrue(abs(seconds - (SECONDS + config.summaryMs / 1_000.0)) <= FRAME_SLACK_S, "$seconds s")
        assertEquals(1f, progress.last(), "the progress ends at the end")
    }

    @Test
    fun `a sound longer than the picture rings on into the summary`() = runTest {
        // the hall of the processed sound, a backing: the sound goes on past the last frame — the writer must get the frames of the
        // summary in time, or it waits for them while the sound waits for it
        val picture = PlatformFile("$folder/take.mp4")
        writePicture(picture, SECONDS, turned = false)
        val sound = PlatformFile("$folder/take.m4a")
        writeAacTones(sound.path, RATE, listOf(Tone(440.0, SECONDS + 4.0)))
        val target = PlatformFile("$folder/notes.mp4")
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(SECONDS * 1_000L), words, target) {} })
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(target.path), options = null)
        val seconds = CMTimeGetSeconds(asset.duration)
        assertTrue(abs(seconds - (SECONDS + config.summaryMs / 1_000.0)) <= FRAME_SLACK_S, "$seconds s")
    }

    @Test
    fun `a sound shorter than the picture leaves the rest of the picture whole`() = runTest {
        // the sound ends at a second: finished at once, so the writer stops waiting for sound to put beside the frames to come
        val picture = PlatformFile("$folder/take.mp4")
        writePicture(picture, LONGER_PICTURE, turned = false)
        val sound = PlatformFile("$folder/take.m4a")
        writeAacTones(sound.path, RATE, listOf(Tone(440.0, 1.0)))
        val target = PlatformFile("$folder/notes.mp4")
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(LONGER_PICTURE * 1_000L), words, target) {} })
        val seconds = CMTimeGetSeconds(AVURLAsset(uRL = NSURL.fileURLWithPath(target.path), options = null).duration)
        assertTrue(abs(seconds - (LONGER_PICTURE + config.summaryMs / 1_000.0)) <= FRAME_SLACK_S, "$seconds s")
    }

    @Test
    fun `the note under the playhead is drawn in its colour`() = runTest {
        val (picture, sound) = take(SECONDS)
        val target = PlatformFile("$folder/notes.mp4")
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(SECONDS * 1_000L), words, target) {} })
        val frame = Frame.of(target, seconds = 1.0, keep = "ios-1s")
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        val (low, high) = NotesOverlays.heights(listOf(A4), config)
        assertColorNear(IN_TUNE, frame.pixel((geometry.headX + AHEAD_U * geometry.u).toInt(), geometry.pillCenterY(A4, low, high).toInt()))
    }

    @Test
    fun `the summary ends the video on a dark veil`() = runTest {
        val (picture, sound) = take(SECONDS)
        val target = PlatformFile("$folder/notes.mp4")
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(SECONDS * 1_000L), words, target) {} })
        val corner = Frame.of(target, seconds = SECONDS + 2.0, keep = "ios-summary").pixel(2, 2)
        assertTrue(luminance(corner) < DARK, "dark: ${corner.toString(16)}")
    }

    /**
     * Since 0.90: the opening title darkens the top over the first seconds of a video long enough for it and is gone after; left
     * of the playhead the capsule has crumbled to dust; the summary carries the icon of the app. Since 0.93: the line of the app
     * stands in the top right corner the whole video, the opening's and after it.
     */
    @Test
    fun `the opening the dust and the icon reach the file`() = runTest {
        val picture = PlatformFile("$folder/take.mp4")
        writePicture(picture, LONGER_PICTURE, turned = false)
        val sound = PlatformFile("$folder/take.m4a")
        writeAacTones(sound.path, RATE, listOf(Tone(440.0, LONGER_PICTURE.toDouble())))
        val target = PlatformFile("$folder/notes.mp4")
        val overlay = overlay(LONGER_PICTURE * 1_000L)
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay, words, target) {} })

        val opening = Frame.of(target, seconds = 1.5, keep = "ios-opening-1.5s")
        val after = Frame.of(target, seconds = 3.5, keep = "ios-3.5s")
        val shaded = luminance(opening.pixel(2, 2))
        val clear = luminance(after.pixel(2, 2))
        assertTrue(shaded < clear * SHADED_SHARE, "the top at 1.5 s: $shaded, at 3.5 s: $clear")

        val geometry = NotesOverlayGeometry(opening.width.toFloat(), opening.height.toFloat(), config)
        val (low, high) = NotesOverlays.heights(listOf(A4), config)
        val x = (geometry.headX - BEHIND_U * geometry.u).toInt()
        val middle = geometry.pillCenterY(A4, low, high)
        val column = ((middle - geometry.pillHeight / 2).toInt()..(middle + geometry.pillHeight / 2).toInt()).map { opening.pixel(x, it) }
        val solid = column.count { near(IN_TUNE, it) }
        assertTrue(solid < column.size / 2, "$solid of ${column.size} pixels are the capsule's colour")

        val summary = Frame.of(target, seconds = LONGER_PICTURE + 2.0, keep = "ios-summary-icon")
        val top = geometry.signatureBottom - config.appLineMaxLines * geometry.signatureLineHeight
        val warm = (top.toInt() until geometry.signatureBottom.toInt()).any { y ->
            (0 until summary.width).any { x -> summary.pixel(x, y).let { (it shr 16 and 0xFF) > WARM_RED && (it shr 16 and 0xFF) - (it and 0xFF) > WARM_LEAD } }
        }
        assertTrue(warm, "the sun of the icon under the summary")

        assertTrue(lettersIn(opening, geometry.appLineBox) > 0, "the line of the app at 1.5 s")
        assertTrue(lettersIn(after, geometry.appLineBox) > 0, "the line of the app at 3.5 s, after the opening")
    }

    /** The pixels of [box] that stand out of their row as letters do: lighter or darker than the picture at the left edge of the row. */
    private fun lettersIn(frame: Frame, box: Rect): Int = (box.top.toInt() until box.bottom.toInt()).sumOf { y ->
        val picture = luminance(frame.pixel(2, y))
        (box.left.toInt() until box.right.toInt().coerceAtMost(frame.width)).count { abs(luminance(frame.pixel(it, y)) - picture) > LETTER_CONTRAST }
    }

    /** The frames of a portrait and of a landscape video at the opening, in the lane and in the summary, kept to be looked at. */
    @Test
    fun `frames to look at`() = runTest {
        val notes = listOf(
            OverlayNote(A4, 0, 2_000, Zone.IN_TUNE, 3.0),
            OverlayNote(A4 + 4, 2_050, 3_000, Zone.OFF, 24.0),
            OverlayNote(A4 + 7, 3_050, 5_000, Zone.NEAR, -14.0),
        )
        val (low, high) = NotesOverlays.heights(notes.map { it.midi }, config)
        val overlay = NotesOverlay(
            notes = notes, lowMidi = low, highMidi = high, scorePercent = 82, toleranceCents = 8, title = "Менуэт соль мажор · 3 октября",
            heading = "Менуэт соль мажор", date = "3 октября", bestMidi = A4, drift = OverlayDrift(A4 + 4, 24.0, Zone.OFF), previous = OverlayPrevious(74, 8),
            ribbon = notes.map { RecordingBar(((it.endMs - it.startMs) / 50).toInt(), it.zone) }, config = config,
        )
        val shown = words.copy(driftCents = "+24 ц", previousScore = "74%")
        listOf(false to "landscape", true to "portrait").forEach { (turned, name) ->
            val picture = PlatformFile("$folder/take.mp4")
            writePicture(picture, LOOK_SECONDS, turned)
            val sound = PlatformFile("$folder/take.m4a")
            writeAacTones(sound.path, RATE, listOf(Tone(440.0, LOOK_SECONDS.toDouble())))
            val target = PlatformFile("$folder/notes-$name.mp4")
            assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay, shown, target) {} })
            listOf(1.5, 2.2, 3.5).forEach { Frame.of(target, seconds = it, keep = "ios-look-$name-${it}s") }
            Frame.of(target, seconds = LOOK_SECONDS + 2.0, keep = "ios-look-$name-summary")
            NSFileManager.defaultManager.removeItemAtPath(picture.path, null)
            NSFileManager.defaultManager.removeItemAtPath(sound.path, null)
        }
    }

    @Test
    fun `a turned video is drawn standing`() = runTest {
        val (picture, sound) = take(SECONDS, turned = true)
        val target = PlatformFile("$folder/notes.mp4")
        assertTrue(withContext(Dispatchers.Default) { renderer.render(picture, sound, overlay(SECONDS * 1_000L), words, target) {} })
        val frame = Frame.of(target, seconds = 1.0, keep = "ios-turned-1s")
        assertTrue(frame.height > frame.width, "standing: ${frame.width} × ${frame.height}")
        // the picture itself is there, above the shade: grey, not the black of a frame it fell out of
        val above = frame.pixel(frame.width / 2, frame.height / 5)
        assertTrue(luminance(above) > PICTURE, "the picture shows: ${above.toString(16)}")
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        val (low, high) = NotesOverlays.heights(listOf(A4), config)
        assertColorNear(IN_TUNE, frame.pixel((geometry.headX + AHEAD_U * geometry.u).toInt(), geometry.pillCenterY(A4, low, high).toInt()))
    }

    @Test
    fun `a render given up leaves nothing`() = runTest {
        val (picture, sound) = take(LONG_SECONDS)
        val target = PlatformFile("$folder/notes.mp4")
        val started = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.Default) {
            renderer.render(picture, sound, overlay(LONG_SECONDS * 1_000L), words, target) { if (it > 0f) started.complete(Unit) }
        }
        withContext(Dispatchers.Default) { started.await() }
        work.cancelAndJoin()
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(target.path), "no file is left")
    }

    /** [seconds] of a grey picture at [FPS], no sound; stored lying and turned a quarter when [turned], as a phone films standing. */
    private fun writePicture(file: PlatformFile, seconds: Int, turned: Boolean) {
        val writer = AVAssetWriter(uRL = NSURL.fileURLWithPath(file.path), fileType = AVFileTypeMPEG4, error = null)
        val input = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf<Any?, Any?>(AVVideoCodecKey to AVVideoCodecTypeH264, AVVideoWidthKey to WIDTH, AVVideoHeightKey to HEIGHT),
        )
        if (turned) input.transform = CGAffineTransformMakeRotation(PI / 2)
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

    /** A frame of a video as the player shows it, read as RGBA. */
    private class Frame(val width: Int, val height: Int, private val pixels: UByteArray) {
        fun pixel(x: Int, y: Int): Int {
            val at = (y * width + x) * RGBA
            return (pixels[at].toInt() shl 16) or (pixels[at + 1].toInt() shl 8) or pixels[at + 2].toInt()
        }

        companion object {
            /** The frame at [seconds]; kept as `tmp/overlay-frames/<keep>.png` of the simulator when [keep] is given, to be looked at. */
            fun of(file: PlatformFile, seconds: Double, keep: String? = null): Frame {
                val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
                val generator = AVAssetImageGenerator(asset = asset).apply {
                    appliesPreferredTrackTransform = true
                    requestedTimeToleranceBefore = kCMTimeZero.readValue()
                    requestedTimeToleranceAfter = kCMTimeZero.readValue()
                }
                val image = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(seconds, TIMESCALE), null, null)
                    ?: error("no frame at $seconds s")
                try {
                    keep?.let { name ->
                        val frames = NSTemporaryDirectory() + "overlay-frames"
                        NSFileManager.defaultManager.createDirectoryAtPath(frames, true, null, null)
                        UIImagePNGRepresentation(UIImage.imageWithCGImage(image))?.writeToFile("$frames/$name.png", atomically = true)
                    }
                    return read(image)
                } finally {
                    CGImageRelease(image)
                }
            }

            private fun read(image: CGImageRef): Frame {
                val width = CGImageGetWidth(image).toInt()
                val height = CGImageGetHeight(image).toInt()
                val pixels = UByteArray(width * height * RGBA)
                pixels.usePinned { pinned ->
                    val space = CGColorSpaceCreateDeviceRGB()
                    val context = CGBitmapContextCreate(
                        pinned.addressOf(0), width.convert(), height.convert(), BITS.convert(), (width * RGBA).convert(), space,
                        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                    )
                    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
                    CGContextRelease(context)
                    CGColorSpaceRelease(space)
                }
                return Frame(width, height, pixels)
            }
        }
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        assertTrue(near(expected, actual), "expected about ${expected.toString(16)}, was ${actual.toString(16)}")
    }

    private fun near(expected: Int, actual: Int): Boolean =
        (0..2).all { abs((expected shr (it * 8) and 0xFF) - (actual shr (it * 8) and 0xFF)) <= COLOR_SLACK }

    private fun luminance(rgb: Int) = (0.2126 * (rgb shr 16 and 0xFF) + 0.7152 * (rgb shr 8 and 0xFF) + 0.0722 * (rgb and 0xFF)) / 255

    private companion object {
        const val A4 = 69

        /** Right of the playhead — left of it a capsule is dust — and past the name the capsule carries at its start there. */
        const val AHEAD_U = 8f
        const val SECONDS = 2
        const val LONG_SECONDS = 20
        const val LONGER_PICTURE = 4
        const val LOOK_SECONDS = 5

        /** Left of the playhead, where the capsule of A would still be whole without the dust. */
        const val BEHIND_U = 3f

        /** The shade of the opening is 0.55 at the top: the picture keeps under half of itself there. */
        const val SHADED_SHARE = 0.7

        /** The sun of the icon is about ffe0a0, its dusk cc826a: red leads and is bright, as neither the veil nor white text is. */
        const val WARM_RED = 150
        const val WARM_LEAD = 40
        const val WIDTH = 320
        const val HEIGHT = 240
        const val FPS = 15
        const val RATE = 48_000
        const val GREY = 0x80
        const val POLL_US = 1_000u
        const val TIMESCALE = 600
        const val RGBA = 4
        const val BITS = 8
        const val IN_TUNE = 0x47C97E

        /** A letter of the line of the app — white at 0.45 or its shadow — against the picture beside it, past what the encoder moves. */
        const val LETTER_CONTRAST = 0.12

        /** The encoder moves colours a little: chroma at a quarter of the pixels, and the picture compressed. */
        const val COLOR_SLACK = 28

        /** A frame of the test video and one of the summary. */
        const val FRAME_SLACK_S = 0.15
        const val DARK = 0.2

        /** The grey of the test picture, 0x80, as it comes back through the encoder: well above black. */
        const val PICTURE = 0.35
    }
}
