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
    private val renderer = MediaNotesVideoRenderer(
        context,
        OverlayTextLoader { OverlayText(createFontFamilyResolver(context), Manrope, OverlayText.icon()) },
        config,
        Dispatchers.IO,
    )

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** One A, in tune, from the start of the video to its end. */
    private fun overlay(videoMs: Long): NotesOverlay {
        val note = OverlayNote(69, 0, videoMs, Zone.IN_TUNE, 0.0)
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        return NotesOverlay(
            notes = listOf(note), lowMidi = low, highMidi = high, scorePercent = 100, toleranceCents = 8, title = "A",
            heading = "A", date = "3 октября", bestMidi = 69, drift = null, previous = null, ribbon = listOf(RecordingBar((videoMs / 50).toInt(), Zone.IN_TUNE)), config = config,
        )
    }

    private val words = OverlayWords(
        badge = "Анализ игры", signature = "Анализируй свою игру в приложении Violin Journey", toleranceLine = "в строе · допуск ±8 ц", bestNote = "Лучшая нота", drift = "Что уходит",
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

    /** The folder of a file to share is not there until something writes into it: with the video's own sound, that is this renderer. */
    @Test
    fun aFileIsMadeInAFolderNotThereYet() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "share/notes-of-take/notes.mp4")
        assertTrue(render(source, target))
        assertTrue(target.length() > 0)
    }

    @Test
    fun theNoteUnderThePlayheadIsDrawnInItsColour() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        assertTrue(render(source, target))
        val frame = frameAt(target, 1_000, keep = "video-1s")
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        val pixel = frame.getPixel((geometry.headX + AHEAD_U * geometry.u).toInt(), geometry.pillCenterY(69, low, high).toInt())
        assertColorNear(IN_TUNE, pixel)
    }

    /**
     * A dimmed note — drawn at 0.62 over the shade — comes out as bright as drawn: the shader of Media3 mixes an overlay as colours
     * not multiplied by their alpha, and a bitmap keeps them multiplied; multiplied twice, it came out about a third darker.
     */
    @Test
    fun aDimmedNoteIsAsBrightAsDrawn() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        // A until 1.5 s, then C# a little high, still ahead of the playhead at 1 s: dimmed
        val notes = listOf(OverlayNote(A4, 0, 1_500, Zone.IN_TUNE, 0.0), OverlayNote(C_SHARP_5, 1_550, SECONDS * 1_000L, Zone.NEAR, 12.0))
        val (low, high) = NotesOverlays.heights(notes.map { it.midi }, config)
        val overlay = NotesOverlay(
            notes = notes, lowMidi = low, highMidi = high, scorePercent = 80, toleranceCents = 8, title = "A", heading = "A", date = "3 октября", bestMidi = A4, drift = null,
            previous = null, ribbon = listOf(RecordingBar(SECONDS * 20, Zone.IN_TUNE)), config = config,
        )
        assertTrue(runBlocking { renderer.render(source, source, overlay, words, target) {} })
        val frame = frameAt(target, 1_000, keep = "video-dimmed")
        val geometry = NotesOverlayGeometry(frame.width.toFloat(), frame.height.toFloat(), config)
        val x = geometry.x(2_200, 1_000).toInt()
        val y = geometry.pillCenterY(C_SHARP_5, low, high)
        // the capsule under its name, and the shade beside it, a capsule higher
        val pill = frame.getPixel(x, (y + BELOW_NAME_U * geometry.u).toInt())
        val shade = frame.getPixel(x, (y - geometry.pillHeight * 1.5f).toInt())
        val expected = blend(NEAR, shade, config.otherNotesAlpha)
        assertColorNear(expected, pill)
    }

    /** The hall rings on past the summary: the sound is cut where the file ends, as on iOS (spec 3.37). */
    @Test
    fun theSoundEndsWithTheSummary() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        // a sound five seconds longer than the picture, two past the end of the summary: the track of a longer video
        val longSound = TestVideo.make(File(directory, "long.mp4"), seconds = SECONDS + 5)
        val target = File(directory, "notes.mp4")
        assertTrue(runBlocking { renderer.render(source, longSound, overlay(SECONDS * 1_000L), words, target) {} })
        val soundMs = checkNotNull(trackFormat(target, "audio/")).getLong(MediaFormat.KEY_DURATION) / 1_000
        val pictureMs = checkNotNull(trackFormat(source, "video/")).getLong(MediaFormat.KEY_DURATION) / 1_000
        assertTrue("the sound of $soundMs ms ends with the file", soundMs <= pictureMs + config.summaryMs + SOUND_SLACK_MS)
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

    /**
     * What Media3 is handed beside the band of the lane (since 0.90): the opening title darkens the top of the frame while it shows
     * and is wiped after it — a 3 s video ends it at 2 s; the dust left of the playhead; the icon of the signature in the summary.
     * The test picture changes its grey every frame, so the top is read against the picture between the two shades.
     */
    @Test
    fun theOpeningTheDustAndTheIconReachTheFile() {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = SECONDS)
        val target = File(directory, "notes.mp4")
        assertTrue(render(source, target))
        val opening = frameAt(target, 1_500, keep = "video-opening-1.5s")
        val after = frameAt(target, 2_500, keep = "video-2.5s")
        val geometry = NotesOverlayGeometry(opening.width.toFloat(), opening.height.toFloat(), config)
        val between = (geometry.openingScrimHeight + (geometry.scrimTop - geometry.openingScrimHeight) / 2).toInt()
        fun topShare(frame: Bitmap) = luminance(frame.getPixel(2, 2)) / luminance(frame.getPixel(2, between))
        assertTrue("the top under the shade: ${topShare(opening)} of the picture", topShare(opening) < SHADED_SHARE)
        assertTrue("the shade is gone: ${topShare(after)} of the picture", topShare(after) > CLEAR_SHARE)

        // left of the playhead the capsule of A has crumbled: not its colour from top to bottom
        val (low, high) = NotesOverlays.heights(listOf(69), config)
        val x = (geometry.headX - BEHIND_U * geometry.u).toInt()
        val middle = geometry.pillCenterY(69, low, high)
        val column = ((middle - geometry.pillHeight / 2).toInt()..(middle + geometry.pillHeight / 2).toInt()).map { opening.getPixel(x, it) }
        val solid = column.count { near(IN_TUNE, it) }
        assertTrue("$solid of ${column.size} pixels are the capsule's colour", solid < column.size / 2)

        val summary = frameAt(target, SECONDS * 1_000L + 2_000, keep = "video-summary-icon")
        val top = summary.height - (config.signatureBottomLandscapeU + config.appLineMaxLines * config.signatureLineU) * geometry.u
        val warm = (top.toInt() until summary.height).any { y ->
            (0 until summary.width).any { x -> summary.getPixel(x, y).let { red(it) > WARM_RED && red(it) - blue(it) > WARM_LEAD } }
        }
        assertTrue("the sun of the icon under the summary", warm)
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
        assertColorNear(IN_TUNE, frame.getPixel((geometry.headX + AHEAD_U * geometry.u).toInt(), geometry.pillCenterY(69, low, high).toInt()))
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
        assertTrue("expected about ${Integer.toHexString(expected)}, was ${Integer.toHexString(actual)}", near(expected, actual))
    }

    private fun near(expected: Int, actual: Int): Boolean =
        abs(red(expected) - red(actual)) <= COLOR_SLACK && abs(green(expected) - green(actual)) <= COLOR_SLACK && abs(blue(expected) - blue(actual)) <= COLOR_SLACK

    /** [color] at [alpha] over [under], as the frame shows it. */
    private fun blend(color: Int, under: Int, alpha: Float): Int {
        fun channel(shift: Int) = ((color shr shift and 0xFF) * alpha + (under shr shift and 0xFF) * (1 - alpha)).toInt()
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun red(pixel: Int) = pixel shr 16 and 0xFF
    private fun green(pixel: Int) = pixel shr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun luminance(pixel: Int) = (0.2126 * red(pixel) + 0.7152 * green(pixel) + 0.0722 * blue(pixel)) / 255

    private companion object {
        const val SECONDS = 3

        /** Right of the playhead — left of it a capsule is dust — and past the name the capsule carries at its start there. */
        const val AHEAD_U = 8f
        const val A4 = 69
        const val C_SHARP_5 = 73
        const val NEAR = 0xFFE5B03C.toInt()

        /** Left of the playhead, where the capsule of A would still be whole without the dust. */
        const val BEHIND_U = 3f

        /** The shade of the opening is 0.55 at the top: the picture keeps under half of itself there, and all of it after. */
        const val SHADED_SHARE = 0.7
        const val CLEAR_SHARE = 0.85

        /** The sun of the icon is about ffe0a0, its dusk cc826a: red leads and is bright, as neither the veil nor white text is. */
        const val WARM_RED = 150
        const val WARM_LEAD = 40

        /** Inside a capsule, under the letters of its name. */
        const val BELOW_NAME_U = 1.6f

        /** One packet of AAC: what a cut of sound may run over. */
        const val SOUND_SLACK_MS = 50L
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
