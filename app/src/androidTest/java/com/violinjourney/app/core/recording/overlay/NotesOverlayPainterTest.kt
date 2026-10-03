package com.violinjourney.app.core.recording.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.ui.theme.DarkZoneColors
import com.violinjourney.app.core.ui.theme.Manrope
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drawing of «Видео с нотами» (spec 3.37, 5.30) on Android — with the app's own Manrope, into a bitmap, as the overlay of
 * Media3 gets it: the note under the playhead in its zone colour, the playhead white, the glass of the badge, the veil of the
 * summary. The frames are kept in the cache (`cache/overlay-frames/`) to be looked at: `adb exec-out run-as … cat`.
 */
@RunWith(AndroidJUnit4::class)
class NotesOverlayPainterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val frames = File(context.cacheDir, "overlay-frames").apply { mkdirs() }
    private val config = NotesVideoConfig()

    /** A in tune 0–2 s, C sharp and far off 2.05–3 s, E a little flat 3.05–6 s; the video ends at 7 s. */
    private val notes = listOf(
        OverlayNote(69, 0, 2_000, Zone.IN_TUNE),
        OverlayNote(73, 2_050, 3_000, Zone.OFF),
        OverlayNote(76, 3_050, 6_000, Zone.NEAR),
    )
    private val videoEndMs = 7_000L
    private val low = NotesOverlays.heights(notes.map { it.midi }, config).first
    private val high = NotesOverlays.heights(notes.map { it.midi }, config).second

    private fun overlay(): NotesOverlay {
        return NotesOverlay(
            notes = notes, lowMidi = low, highMidi = high, scorePercent = 82, toleranceCents = 8, title = "Менуэт соль мажор · 3 октября",
            bestMidi = 69, drift = OverlayDrift(73, 24.0, Zone.OFF), previous = OverlayPrevious(74, 8),
            ribbon = notes.map { RecordingBar(((it.endMs - it.startMs) / 50).toInt(), it.zone) }, config = config,
        )
    }

    private val words = OverlayWords(
        badge = "в строе 82%", toleranceLine = "в строе · допуск ±8 ц", bestNote = "Лучшая нота", drift = "Что уходит",
        driftCents = "+24 ц", driftNone = "ничего", previousTake = "Прошлый дубль", previousScore = "74%",
    )

    /** The frame of [nowMs] over a plain grey picture. */
    private fun frame(width: Int, height: Int, nowMs: Long, name: String): Pair<Bitmap, NotesOverlayPainter> {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(GREY)
        val painter = NotesOverlayPainter(overlay(), words, OverlayText(createFontFamilyResolver(context), Manrope), width.toFloat(), height.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            painter.draw(this, nowMs, videoEndMs)
        }
        File(frames, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap to painter
    }

    @Test
    fun theNoteUnderThePlayheadIsWholeInItsZoneColour() {
        val (bitmap, painter) = frame(1_080, 1_920, nowMs = 1_000, name = "portrait-1s")
        val g = painter.geometry
        val y = g.pillCenterY(69, low, high).toInt()
        // a little left of the white playhead, inside the capsule of A, which runs from −1 s to +1 s around it
        assertColor(DarkZoneColors.inTune.toArgb(), bitmap.getPixel((g.headX - 3 * g.u).toInt(), y))
        // the playhead itself is white over the capsule
        assertTrue("the playhead is white", luminance(bitmap.getPixel(g.headX.toInt(), y)) > 0.85)
    }

    @Test
    fun theOtherNotesAreDimmedAndTheNextOneKeepsItsColour() {
        val (bitmap, painter) = frame(1_080, 1_920, nowMs = 1_000, name = "portrait-1s-next")
        val g = painter.geometry
        // C sharp starts 1.05 s ahead: dimmed, still red over the shade — read under the middle of the capsule, below its name
        val x = g.x(2_500, 1_000).toInt()
        val pixel = bitmap.getPixel(x, (g.pillCenterY(73, low, high) + BELOW_NAME_U * g.u).toInt())
        assertTrue("red leads: ${Integer.toHexString(pixel)}", red(pixel) > green(pixel) + 40 && red(pixel) > blue(pixel) + 40)
        assertTrue("dimmed, not whole: ${Integer.toHexString(pixel)}", red(pixel) < 0xE0)
    }

    @Test
    fun theBadgeStandsOnTheGlass() {
        val (bitmap, painter) = frame(1_080, 1_920, nowMs = 1_000, name = "portrait-badge")
        val g = painter.geometry
        // the start of the badge, before its dot: the glass alone over the shade of the lane
        val pixel = bitmap.getPixel((g.badgeLeft + g.u).toInt(), (g.badgeTop + g.badgeHeight / 2).toInt())
        assertTrue("dark glass: ${Integer.toHexString(pixel)}", luminance(pixel) < 0.12)
    }

    @Test
    fun theSummaryLiesOnADarkVeil() {
        val (bitmap, _) = frame(1_080, 1_920, nowMs = videoEndMs + 1_000, name = "portrait-summary")
        // a corner of the frame: grey under the veil of 0.9
        val corner = bitmap.getPixel(20, 20)
        assertEquals(veiled(0x80).toDouble(), red(corner).toDouble(), 3.0)
        assertEquals(veiled(0x80).toDouble(), green(corner).toDouble(), 3.0)
        frame(1_920, 1_080, nowMs = videoEndMs + 1_000, name = "landscape-summary")
        frame(1_920, 1_080, nowMs = 4_000, name = "landscape-4s")
        frame(1_080, 1_080, nowMs = 2_500, name = "square-2.5s")
    }

    @Test
    fun theSummaryComesInOverTheLane() {
        val (bitmap, _) = frame(1_080, 1_920, nowMs = videoEndMs + config.summaryFadeMs / 2, name = "portrait-summary-coming")
        val corner = bitmap.getPixel(20, 20)
        // half the time is three quarters of the way, the fade slowing towards its end: the veil at 0.675 — at 0.45 it would be linear
        assertTrue("on its way, slowing: ${red(corner)}", red(corner) in EASED_HALFWAY)
    }

    private fun veiled(channel: Int): Int = (channel * (1 - config.veilAlpha) + INK * config.veilAlpha).toInt()

    private fun assertColor(expected: Int, actual: Int) {
        val near = abs(red(expected) - red(actual)) <= 2 && abs(green(expected) - green(actual)) <= 2 && abs(blue(expected) - blue(actual)) <= 2
        assertTrue("expected ${Integer.toHexString(expected)}, was ${Integer.toHexString(actual)}", near)
    }

    private fun red(pixel: Int) = pixel shr 16 and 0xFF
    private fun green(pixel: Int) = pixel shr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun luminance(pixel: Int) = (0.2126 * red(pixel) + 0.7152 * green(pixel) + 0.0722 * blue(pixel)) / 255

    private companion object {
        const val GREY = 0xFF808080.toInt()
        const val INK = 0x0B

        /** Inside the capsule, under the letters of its name: the capsule is 4.4 u high, the name about 2 u. */
        const val BELOW_NAME_U = 1.6f

        /** Grey 0x80 under a veil of 0.9 × 0.75 is about 49; a linear fade would leave about 75. */
        val EASED_HALFWAY = 40..60
    }
}
