package com.violinjourney.app.core.recording.overlay

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.ui.theme.Manrope
import com.violinjourney.app.testing.TestVideo
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Видео с нотами» on Android (spec 3.37, 5.30): a real video in, through Media3 — the file is three seconds longer, its picture
 * H.264 with the lane drawn on it and the summary at the end, its sound the track it was given, as it was; a turned video stays
 * turned; a render given up leaves nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class NotesVideoRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "notes-video-test").apply { mkdirs() }
    private val config = NotesVideoConfig()
    private val renderer = MediaNotesVideoRenderer(context, OverlayText(createFontFamilyResolver(context), Manrope), config, Dispatchers.IO)

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** One A, in tune, from the start of the video to its end. */
    private fun overlay(videoMs: Long): NotesOverlay {
        val note = OverlayNote(69, 0, videoMs, Zone.IN_TUNE)
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        return NotesOverlay(
            notes = listOf(note), lowMidi = low, highMidi = high, scorePercent = 100, toleranceCents = 8, title = "A",
            bestMidi = 69, drift = null, previous = null, ribbon = listOf(RecordingBar((videoMs / 50).toInt(), Zone.IN_TUNE)), config = config,
        )
    }

    private val words = OverlayWords(
        badge = "в строе 100%", toleranceLine = "в строе · допуск ±8 ц", bestNote = "Лучшая нота", drift = "Что уходит",
        driftCents = null, driftNone = "ничего", previousTake = "Прошлый дубль", previousScore = null,
    )

    private fun render(source: File, target: File, progress: MutableList<Float> = ArrayList()): Boolean = runBlocking {
        renderer.render(source, source, overlay(SECONDS * 1_000L), words, target) { synchronized(progress) { progress += it } }
    }

    private fun trackFormat(file: File, kind: String): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith(kind) == true }
        } finally {
            extractor.release()
        }
    }

    /** The frame of [file] at [ms], kept as `cache/overlay-frames/<keep>.png` to be looked at. */
    private fun frameAt(file: File, ms: Long, keep: String): Bitmap {
        val retriever = MediaMetadataRetriever()
        val frame = try {
            retriever.setDataSource(file.absolutePath)
            checkNotNull(retriever.getFrameAtTime(ms * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)) { "no frame at $ms ms" }
        } finally {
            retriever.release()
        }
        File(context.cacheDir, "overlay-frames").apply { mkdirs() }.resolve("$keep.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return frame
    }

    @Test
    fun theFileIsLongerByTheSummaryWithANewPictureAndTheSoundItWasGiven() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        val progress = ArrayList<Float>()
        assertTrue(render(source, target, progress))

        val picture = checkNotNull(trackFormat(target, "video/"))
        assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, picture.getString(MediaFormat.KEY_MIME))
        val sourceMs = checkNotNull(trackFormat(source, "video/")).getLong(MediaFormat.KEY_DURATION) / 1_000
        val targetMs = picture.getLong(MediaFormat.KEY_DURATION) / 1_000
        assertTrue("$targetMs ms against $sourceMs ms", abs(targetMs - (sourceMs + config.summaryMs)) <= FRAME_SLACK_MS)
        // the sound track as it was given: the same kind of sound
        assertEquals(trackFormat(source, "audio/")?.getString(MediaFormat.KEY_MIME), trackFormat(target, "audio/")?.getString(MediaFormat.KEY_MIME))
        assertTrue("progress grows to the end: $progress", progress.isNotEmpty() && progress.zipWithNext().all { (a, b) -> b >= a } && progress.last() == 1f)
        // nothing is left beside the file
        assertEquals(listOf("notes.mp4", "take.mp4"), directory.list()!!.filter { !it.endsWith(".m4a") }.sorted())
    }

    @Test
    fun theNoteUnderThePlayheadIsDrawnInItsColour() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        assertTrue(render(source, target))
        val frame = frameAt(target, 1_000, keep = "video-1s")
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        val pixel = frame.getPixel((geometry.headX - 3 * geometry.u).toInt(), geometry.pillCenterY(69, low, high).toInt())
        assertColorNear(IN_TUNE, pixel)
    }

    @Test
    fun theSummaryEndsTheVideoOnADarkVeil() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        assertTrue(render(source, target))
        val frame = frameAt(target, SECONDS * 1_000L + 2_000, keep = "video-summary")
        // the corner of the frame lies under the veil of 0.9: whatever grey the last frame was, little of it shows
        val corner = frame.getPixel(2, 2)
        assertTrue("dark: ${Integer.toHexString(corner)}", luminance(corner) < 0.2)
    }

    @Test
    fun aTurnedVideoStaysTurnedAndTheLaneStandsAtTheBottomOfWhatIsSeen() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS, rotation = 90)
        val target = File(directory, "notes.mp4")
        assertTrue(render(source, target))
        val frame = frameAt(target, 1_000, keep = "video-turned-1s")
        assertTrue("shown standing: ${frame.width} × ${frame.height}", frame.height > frame.width)
        // the picture itself is there, above the shade: grey, not the black of a frame it fell out of
        val above = frame.getPixel(frame.width / 2, frame.height / 5)
        assertTrue("the picture shows: ${Integer.toHexString(above)}", luminance(above) > PICTURE)
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        assertTrue(geometry.portrait)
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        assertColorNear(IN_TUNE, frame.getPixel((geometry.headX - 3 * geometry.u).toInt(), geometry.pillCenterY(69, low, high).toInt()))
    }

    @Test
    fun aRenderGivenUpLeavesNothing() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = LONG_SECONDS)
        val target = File(directory, "notes.mp4")
        val started = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.Default) {
            renderer.render(source, source, overlay(LONG_SECONDS * 1_000L), words, target) { if (it > 0f) started.complete(Unit) }
        }
        started.await()
        work.cancelAndJoin()
        assertFalse(target.exists())
        assertEquals(listOf("take.mp4"), directory.list()!!.filter { !it.endsWith(".m4a") }.sorted())
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        val near = abs(red(expected) - red(actual)) <= COLOR_SLACK && abs(green(expected) - green(actual)) <= COLOR_SLACK &&
            abs(blue(expected) - blue(actual)) <= COLOR_SLACK
        assertTrue("expected about ${Integer.toHexString(expected)}, was ${Integer.toHexString(actual)}", near)
    }

    private fun red(pixel: Int) = pixel shr 16 and 0xFF
    private fun green(pixel: Int) = pixel shr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun luminance(pixel: Int) = (0.2126 * red(pixel) + 0.7152 * green(pixel) + 0.0722 * blue(pixel)) / 255

    private companion object {
        const val SECONDS = 3
        const val LONG_SECONDS = 20
        const val IN_TUNE = 0xFF47C97E.toInt()

        /** The encoder moves colours a little: chroma is kept at a quarter of the pixels, and the picture is compressed. */
        const val COLOR_SLACK = 28

        /** A frame of the test video and a frame of the summary. */
        const val FRAME_SLACK_MS = 150L

        /** The test picture is mid grey wherever the frame count puts it: well above black. */
        const val PICTURE = 0.12
    }
}
