package com.violinjourney.app.core.recording.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drawing of «Видео с нотами» (spec 3.37, 5.30) on Android — with the app's own Manrope, into a bitmap, as the overlay of
 * Media3 gets it: the note under the playhead in its zone colour, the playhead white, the line of the app in its corner, the veil
 * of the summary. The frames are kept in the cache (`cache/overlay-frames/`) to be looked at: `adb exec-out run-as … cat`.
 */
@RunWith(AndroidJUnit4::class)
class NotesOverlayPainterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val frames = File(context.cacheDir, "overlay-frames").apply { mkdirs() }
    private val config = NotesVideoConfig()
    private val text = runBlocking { OverlayText(createFontFamilyResolver(context), Manrope, OverlayText.icon()) }

    /** A in tune 0–2 s, C sharp and far off 2.05–3 s, E a little flat 3.05–6 s; the video ends at 7 s. */
    private val notes = listOf(
        OverlayNote(69, 0, 2_000, Zone.IN_TUNE, 3.0),
        OverlayNote(73, 2_050, 3_000, Zone.OFF, 24.0),
        OverlayNote(76, 3_050, 6_000, Zone.NEAR, -14.0),
    )
    private val videoEndMs = 7_000L
    private val low = NotesOverlays.heights(notes.map { it.midi }, config).first
    private val high = NotesOverlays.heights(notes.map { it.midi }, config).second

    private fun overlay(): NotesOverlay {
        return NotesOverlay(
            notes = notes, lowMidi = low, highMidi = high, scorePercent = 82, toleranceCents = 8, title = "Менуэт соль мажор · 3 октября",
            heading = "Менуэт соль мажор", date = "3 октября",
            bestMidi = 69, drift = OverlayDrift(73, 24.0, Zone.OFF), previous = OverlayPrevious(74, 8),
            ribbon = notes.map { RecordingBar(((it.endMs - it.startMs) / 50).toInt(), it.zone) }, config = config,
        )
    }

    private val words = OverlayWords(
        signature = "Анализируй свою игру в приложении Violin Journey", toleranceLine = "в строе · допуск ±8 ц", bestNote = "Лучшая нота", drift = "Что уходит",
        driftCents = "+24 ц", driftNone = "ничего", previousTake = "Прошлый дубль", previousScore = "74%",
    )

    /** The frame of [nowMs] over a plain picture of [background], grey unless given. */
    private fun frame(width: Int, height: Int, nowMs: Long, name: String, background: Int = GREY): Pair<Bitmap, NotesOverlayPainter> {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(background)
        val painter = NotesOverlayPainter(overlay(), words, text, width.toFloat(), height.toFloat())
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
        // right of the white playhead and past the name, inside the capsule of A, which runs on to +1 s; left of it is dust
        assertColor(DarkZoneColors.inTune.toArgb(), bitmap.getPixel((g.headX + AHEAD_U * g.u).toInt(), y))
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

    /** The line of the app (since 0.93): in the top right corner, its lines to the right; left of it, in its rows, the picture alone. */
    @Test
    fun theLineOfTheAppStandsInTheTopRightCorner() {
        listOf(1_080 to 1_440, 1_920 to 1_080, 1_080 to 1_920).forEach { (width, height) ->
            // after the opening and before the summary: the corner alone at the top
            val (bitmap, painter) = frame(width, height, nowMs = 5_000, name = "app-line-${width}x$height")
            val g = painter.geometry
            val box = g.appLineBox
            val rows = box.top.toInt() until box.bottom.toInt()
            val lit = rows.flatMap { y -> (box.left.toInt() until box.right.toInt()).filter { luminance(bitmap.getPixel(it, y)) > luminance(GREY) + TEXT_LIFT } }
            assertTrue("the letters of $width × $height are drawn", lit.isNotEmpty())
            assertTrue("$width × $height: the letters end at ${lit.max()}, the room at ${box.right}", box.right - lit.max() < RIGHT_SLACK_U * g.u)
            // the shadow reaches a little past the letters, and no further
            rows.forEach { y -> (0 until (box.left - SHADOW_ROOM_U * g.u).toInt()).forEach { x -> assertColor(GREY, bitmap.getPixel(x, y)) } }
        }
    }

    /** On a white wall the dimmed line still reads (since 0.93): a shadow darkens round its letters. */
    @Test
    fun theLineOfTheAppReadsOnAWhiteWall() {
        val (bitmap, painter) = frame(1_080, 1_440, nowMs = 5_000, name = "app-line-white-wall", background = WHITE)
        val box = painter.geometry.appLineBox
        val shadowed = (box.top.toInt() until box.bottom.toInt()).sumOf { y ->
            (box.left.toInt() until box.right.toInt()).count { x -> luminance(bitmap.getPixel(x, y)) < SHADOWED }
        }
        assertTrue("$shadowed pixels under the shadow", shadowed > MIN_SHADOWED)
    }

    /** No badge at the bottom left (since 0.93): under the lane, the shade alone, the same across every row. */
    @Test
    fun theBottomLeftIsTheShadeAlone() {
        // after the last note and its dust
        val (bitmap, painter) = frame(1_080, 1_440, nowMs = 6_800, name = "portrait-3x4-6.8s")
        assertRowsPlain(bitmap, (ceil(painter.geometry.playheadBottom).toInt() + 1) until bitmap.height, "under the lane")
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

    /** The opening title (since 0.90): its shade darkens the top of the frame over the first seconds, and is gone by 5 s. */
    @Test
    fun theOpeningDarkensTheTopAndGoes() {
        val (opening, _) = frame(1_080, 1_920, nowMs = 1_500, name = "portrait-opening-1.5s")
        val (after, _) = frame(1_080, 1_920, nowMs = 5_000, name = "portrait-5s")
        frame(1_920, 1_080, nowMs = 1_500, name = "landscape-opening-1.5s")
        assertTrue("the shade at 1.5 s: ${Integer.toHexString(opening.getPixel(20, 20))}", red(opening.getPixel(20, 20)) < red(GREY) - SHADED)
        assertColor(GREY, after.getPixel(20, 20))
    }

    /**
     * The opening's text (since 0.90): the heading — not the title, which carries the date too — and the date under it, each
     * inside its em box, which the spec stands under the line of the app (since 0.93) and 1.6 u under the name; gone by 5 s. The
     * line of the app above them stays: the rows looked through start under its room.
     */
    @Test
    fun theOpeningSaysTheHeadingAndTheDateInTheirPlaces() {
        val (opening, painter) = frame(1_080, 1_920, nowMs = 1_500, name = "portrait-opening-text")
        val (after, _) = frame(1_080, 1_920, nowMs = 5_000, name = "portrait-opening-gone")
        val g = painter.geometry
        val slack = EM_SLACK_U * g.u
        val nameBox = g.openingTitleTop..(g.openingTitleTop + config.openingTitleU * g.u)
        val dateBox = g.openingDateTop..(g.openingDateTop + config.openingDateU * g.u)
        // the two are told apart halfway through the gap between their em boxes
        val between = ((nameBox.endInclusive + dateBox.start) / 2).toInt()
        val nameRows = brightRows(opening, NAME_BRIGHT, g.appLineBottom.toInt() until between)
        val dateRows = brightRows(opening, DATE_BRIGHT, between until g.openingScrimHeight.toInt())
        assertTrue("the name is drawn", nameRows.isNotEmpty())
        assertTrue("the date is drawn", dateRows.isNotEmpty())
        assertTrue("the name ${nameRows.first()}..${nameRows.last()} in its em box $nameBox", nameRows.first() >= nameBox.start - slack && nameRows.last() <= nameBox.endInclusive + slack)
        assertTrue("the date ${dateRows.first()}..${dateRows.last()} in its em box $dateBox", dateRows.first() >= dateBox.start - slack && dateRows.last() <= dateBox.endInclusive + slack)
        // the heading alone: the title with its date would fill the column and end in «…»
        val span = brightSpan(opening, NAME_BRIGHT, nameRows)
        assertTrue("the name is ${span.last - span.first} px of a column of ${g.columnWidth}", span.last - span.first < g.columnWidth * HEADING_SHARE)
        assertTrue("nothing bright at 5 s", brightRows(after, DATE_BRIGHT, g.appLineBottom.toInt() until g.openingScrimHeight.toInt()).isEmpty())
    }

    /** The tag's sign (since 0.90): the arrow up over a note that sits high, down under one that sits low, the dot in tune. */
    @Test
    fun theTagBacksItsColourWithAShape() {
        listOf(
            Triple(2_300L, Zone.OFF, Sign.UP),
            Triple(3_500L, Zone.NEAR, Sign.DOWN),
            Triple(1_000L, Zone.IN_TUNE, Sign.DOT),
        ).forEach { (nowMs, zone, expected) ->
            val (bitmap, painter) = frame(1_080, 1_920, nowMs = nowMs, name = "portrait-tag-${nowMs}ms")
            assertEquals("the sign at $nowMs ms", expected, signOf(bitmap, painter.geometry, DarkZoneColors.colorFor(zone).toArgb()))
        }
    }

    private enum class Sign { UP, DOWN, DOT }

    /**
     * The sign of the tag: the rightmost cluster of [color] in it — the name before it is of the zone too, the number after it is
     * grey. The sign stands in the middle of the tag: an arrow up is wide above it (its head) and narrow under it (its stem), an
     * arrow down the other way round, the dot the same both sides.
     */
    private fun signOf(bitmap: Bitmap, g: NotesOverlayGeometry, color: Int): Sign {
        val rows = (g.tagBottom - g.tagHeight).toInt()..g.tagBottom.toInt()
        fun inColumn(x: Int) = rows.count { near(color, bitmap.getPixel(x, it), SIGN_SLACK) }
        val right = (bitmap.width - 1 downTo 0).first { inColumn(it) > 0 }
        var left = right
        while (left > 0 && inColumn(left - 1) > 0) left--
        fun width(y: Float) = (left..right).count { near(color, bitmap.getPixel(it, y.toInt()), SIGN_SLACK) }
        val middle = g.tagBottom - g.tagHeight / 2
        val above = width(middle - SIGN_PROBE_U * g.u)
        val below = width(middle + SIGN_PROBE_U * g.u)
        return when {
            abs(above - below) <= DOT_SLACK_U * g.u -> Sign.DOT
            above > below -> Sign.UP
            else -> Sign.DOWN
        }
    }

    /** The rows of [rows] with a pixel brighter than [bright] in the middle third of the frame. */
    private fun brightRows(bitmap: Bitmap, bright: Double, rows: IntRange): List<Int> =
        rows.filter { y -> (bitmap.width / 3 until bitmap.width * 2 / 3).any { luminance(bitmap.getPixel(it, y)) > bright } }

    /** From the leftmost to the rightmost pixel brighter than [bright] in [rows]. */
    private fun brightSpan(bitmap: Bitmap, bright: Double, rows: List<Int>): IntRange {
        val xs = rows.flatMap { y -> (0 until bitmap.width).filter { luminance(bitmap.getPixel(it, y)) > bright } }
        return xs.min()..xs.max()
    }

    /** A capsule that reaches the playhead crumbles there (since 0.90): left of it is dust, never the capsule whole. */
    @Test
    fun leftOfThePlayheadIsDustNotTheCapsule() {
        val (bitmap, painter) = frame(1_080, 1_920, nowMs = 1_000, name = "portrait-dust")
        val g = painter.geometry
        val x = (g.headX - BEHIND_U * g.u).toInt()
        val middle = g.pillCenterY(69, low, high)
        val column = ((middle - g.pillHeight / 2).toInt()..(middle + g.pillHeight / 2).toInt()).map { bitmap.getPixel(x, it) }
        val solid = column.count { near(DarkZoneColors.inTune.toArgb(), it, COLOR_SLACK) }
        assertTrue("$solid of ${column.size} pixels are the capsule's colour", solid < column.size / 2)
    }

    /** The signature of the summary (since 0.90): the icon beside the line of the app — its warm sun under the veil. */
    @Test
    fun theSummaryCarriesTheIcon() {
        listOf(1_080 to 1_920, 1_920 to 1_080).forEach { (width, height) ->
            val (bitmap, painter) = frame(width, height, nowMs = videoEndMs + 1_000, name = "summary-icon-${width}x$height")
            assertTrue("the icon in the signature of $width × $height", warmPixelIn(bitmap, signatureBand(painter.geometry)))
        }
    }

    /** The rows under the summary's block and its gap: the signature alone, one or two lines of it. */
    private fun signatureBand(g: NotesOverlayGeometry): IntRange {
        val top = g.signatureBottom - config.appLineMaxLines * g.signatureLineHeight
        return top.toInt() until g.signatureBottom.toInt()
    }

    /**
     * A tall frame — 9 : 16, for Shorts, Reels and TikTok (since 0.91) — leaves its edges to their interface: under the lane, over
     * the opening and round the summary there is the shade or the veil alone, the same across a row. A 3 : 4 portrait is kept
     * beside it to be looked at.
     */
    @Test
    fun aTallFrameLeavesItsEdgesToTheInterface() {
        // after the last note and its dust: the shade, the playhead and the line of the app at the top
        val (lane, painter) = frame(1_080, 1_920, nowMs = 6_800, name = "tall-6.8s")
        val g = painter.geometry
        val safe = checkNotNull(g.safe)
        assertRowsPlain(lane, 0 until safe.top.toInt(), "over the line of the app")
        assertRowsPlain(lane, (ceil(g.playheadBottom).toInt() + 1) until lane.height, "under the lane")
        frame(1_080, 1_920, nowMs = 1_000, name = "tall-1s")
        frame(1_080, 1_440, nowMs = 1_000, name = "portrait-3x4-1s")
        frame(1_080, 1_440, nowMs = videoEndMs + 1_000, name = "portrait-3x4-summary")

        val (opening, _) = frame(1_080, 1_920, nowMs = 1_500, name = "tall-opening-1.5s")
        assertRowsPlain(opening, 0 until safe.top.toInt(), "over the opening")
        assertTrue("the name under the top of the safe zone", brightRows(opening, NAME_BRIGHT, 0 until g.openingScrimHeight.toInt()).first() >= safe.top)

        val (summary, _) = frame(1_080, 1_920, nowMs = videoEndMs + 1_000, name = "tall-summary")
        val veil = summary.getPixel(0, 0)
        val outside = (0 until summary.height).flatMap { y ->
            (0 until summary.width).filter { x -> !safe.contains(Offset(x + 0.5f, y + 0.5f)) }.map { x -> summary.getPixel(x, y) }
        }
        assertTrue("round the summary: the veil alone", outside.all { near(veil, it, PLAIN_SLACK) })
        assertTrue("the icon in the signature of the tall frame", warmPixelIn(summary, signatureBand(g)))
    }

    /** Every row of [rows] is one colour from edge to edge: a shade, nothing drawn on it. */
    private fun assertRowsPlain(bitmap: Bitmap, rows: IntRange, where: String) {
        rows.forEach { y ->
            val first = bitmap.getPixel(0, y)
            val x = (0 until bitmap.width).firstOrNull { !near(first, bitmap.getPixel(it, y), PLAIN_SLACK) }
            assertTrue("$where: row $y differs at $x", x == null)
        }
    }

    /** Some pixel of [rows] is the icon's sun or dusk — warm and bright, which neither the veil nor the white text is. */
    private fun warmPixelIn(bitmap: Bitmap, rows: IntRange): Boolean = rows.any { y ->
        (0 until bitmap.width).any { x -> bitmap.getPixel(x, y).let { red(it) > WARM_RED && red(it) - blue(it) > WARM_LEAD } }
    }

    private fun veiled(channel: Int): Int = (channel * (1 - config.veilAlpha) + INK * config.veilAlpha).toInt()

    private fun assertColor(expected: Int, actual: Int) {
        assertTrue("expected ${Integer.toHexString(expected)}, was ${Integer.toHexString(actual)}", near(expected, actual, 2))
    }

    private fun near(expected: Int, actual: Int, slack: Int): Boolean =
        abs(red(expected) - red(actual)) <= slack && abs(green(expected) - green(actual)) <= slack && abs(blue(expected) - blue(actual)) <= slack

    private fun red(pixel: Int) = pixel shr 16 and 0xFF
    private fun green(pixel: Int) = pixel shr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun luminance(pixel: Int) = (0.2126 * red(pixel) + 0.7152 * green(pixel) + 0.0722 * blue(pixel)) / 255

    private companion object {
        const val GREY = 0xFF808080.toInt()
        const val INK = 0x0B

        /** Inside the capsule, under the letters of its name: the capsule is 4.4 u high, the name about 2 u. */
        const val BELOW_NAME_U = 1.6f

        /** Right of the playhead past the name a capsule carries at its start there: «A4» and its inset are about 5 u. */
        const val AHEAD_U = 8f

        /** Left of the playhead, where the capsule of A would still be whole without the dust. */
        const val BEHIND_U = 3f

        /** A capsule's colour, give or take the dust drawn over the shade. */
        const val COLOR_SLACK = 28

        /** The shade of the opening at 0.55 at its top: grey 0x80 comes down to about 0x3A — well past a few levels. */
        const val SHADED = 40

        /** The white name over the shade, and the date at 0.7 — both brighter than the grey 0x80 of the frame, 0.5. */
        const val NAME_BRIGHT = 0.85
        const val DATE_BRIGHT = 0.6

        /** Anti-aliasing round the em box of the opening's text. */
        const val EM_SLACK_U = 0.3f

        /** «Менуэт соль мажор» is about 60 u of a column of 84; with its date it would fill the column. */
        const val HEADING_SHARE = 0.9f

        /** The sign's own colour, past the anti-aliasing over the glass. */
        const val SIGN_SLACK = 40

        /**
         * Above and under the middle of the tag: inside the dot (0.8 u round), across an arrow's head (2.6 u wide at its base,
         * about 1.9 u here) on one side and its stem (0.8 u) on the other.
         */
        const val SIGN_PROBE_U = 0.6f

        /** The dot is as wide both sides, give or take a pixel or two; an arrow's head is a whole u wider than its stem. */
        const val DOT_SLACK_U = 0.4f

        /** The sun of the icon is about ffe0a0, its dusk cc826a: red leads and is bright. */
        const val WARM_RED = 170
        const val WARM_LEAD = 50

        const val WHITE = 0xFFFFFFFF.toInt()

        /** The letters of the line of the app — white at 0.45, its name at 0.6 — over grey 0x80, past the anti-aliasing. */
        const val TEXT_LIFT = 0.15

        /** Its lines end at the right edge of its room, give or take the side bearing of the last letter. */
        const val RIGHT_SLACK_U = 1f

        /** How far left of its room the shadow of a line as wide as the room may reach. */
        const val SHADOW_ROOM_U = 1.5f

        /** White under the shadow — black at 0.6 round the letters — is well under white. */
        const val SHADOWED = 0.8
        const val MIN_SHADOWED = 300

        /** One colour, give or take a level of the gradient. */
        const val PLAIN_SLACK = 2

        /** Grey 0x80 under a veil of 0.9 × 0.75 is about 49; a linear fade would leave about 75. */
        val EASED_HALFWAY = 40..60
    }
}
