package com.violinjourney.app.feature.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.recording.overlay.NotesOverlay
import com.violinjourney.app.core.recording.overlay.NotesOverlayGeometry
import com.violinjourney.app.core.recording.overlay.NotesOverlayPainter
import com.violinjourney.app.core.recording.overlay.NotesOverlays
import com.violinjourney.app.core.recording.overlay.NotesVideoConfig
import com.violinjourney.app.core.recording.overlay.OverlayDrift
import com.violinjourney.app.core.recording.overlay.OverlayNote
import com.violinjourney.app.core.recording.overlay.OverlayPrevious
import com.violinjourney.app.core.recording.overlay.OverlayText
import com.violinjourney.app.core.recording.overlay.OverlayWords
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.Manrope
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.overlay_app_icon
import org.jetbrains.compose.resources.imageResource

// «Видео с нотами» (spec 3.37, 5.30; overlay.html): frames of the file in their true pixels, scaled down — the lane while the video
// runs, the opening over its first seconds, the summary after it. The picture under them is a stand-in for a video.

private val Config = NotesVideoConfig()

/** The first eight bars of the Minuet in G: name, beats, mean cents; a quarter is 620 ms, a change of bow 50 ms. */
private val Minuet = listOf(
    "D5" to (1.0 to -3), "G4" to (0.5 to 2), "A4" to (0.5 to 4), "B4" to (0.5 to -6), "C5" to (0.5 to -12),
    "D5" to (1.0 to 1), "G4" to (1.0 to 3), "G4" to (1.0 to -2),
    "E5" to (1.0 to -4), "C5" to (0.5 to -10), "D5" to (0.5 to 2), "E5" to (0.5 to 5), "F#5" to (0.5 to 24),
    "G5" to (1.0 to 6), "G4" to (1.0 to 1), "G4" to (1.0 to -1),
    "C5" to (1.0 to -14), "D5" to (0.5 to 3), "C5" to (0.5 to -9), "B4" to (0.5 to -2), "A4" to (0.5 to 1),
    "B4" to (1.0 to -5), "C5" to (0.5 to -11), "B4" to (0.5 to -3), "A4" to (0.5 to 2), "G4" to (0.5 to 0),
    "F#4" to (1.0 to 14), "G4" to (0.5 to 2), "A4" to (0.5 to -1), "B4" to (0.5 to 3), "G4" to (0.5 to -2),
    "A4" to (3.0 to -4),
)
private const val QUARTER_MS = 620L
private const val BOW_MS = 50L
private const val LEAD_MS = 800L
private const val TAIL_MS = 1_200L
private const val BUCKET_MS = 50L
private val Midi = mapOf("F#4" to 66, "G4" to 67, "A4" to 69, "B4" to 71, "C5" to 72, "D5" to 74, "E5" to 76, "F#5" to 78, "G5" to 79)

private fun zoneOf(cents: Int): Zone = ZoneClassifier.classify(cents.toDouble(), IntonationConfig())

private val MinuetNotes: List<OverlayNote> = buildList {
    var at = LEAD_MS
    Minuet.forEach { (name, beatsAndCents) ->
        val (beats, cents) = beatsAndCents
        val length = (beats * QUARTER_MS).toLong()
        add(OverlayNote(Midi.getValue(name), at, at + length - BOW_MS, zoneOf(cents), cents.toDouble()))
        at += length
    }
}
private val VideoEndMs = MinuetNotes.last().endMs + TAIL_MS

private fun overlay(previous: Boolean, drift: Boolean): NotesOverlay {
    val (low, high) = NotesOverlays.heights(MinuetNotes.map { it.midi }, Config)
    return NotesOverlay(
        notes = MinuetNotes,
        lowMidi = low,
        highMidi = high,
        scorePercent = 82,
        toleranceCents = 8,
        title = "Менуэт соль мажор · 3 октября",
        heading = "Менуэт соль мажор",
        date = "3 октября",
        bestMidi = Midi.getValue("G4"),
        drift = if (drift) OverlayDrift(Midi.getValue("F#5"), 24.0, Zone.OFF) else null,
        previous = if (previous) OverlayPrevious(74, 8) else null,
        ribbon = MinuetNotes.map { RecordingBar(((it.endMs - it.startMs) / BUCKET_MS).toInt(), it.zone) },
        config = Config,
    )
}

/** The words as `OverlayWords.of` reads them, from the resources of the preview's language. */
@Composable
private fun words(overlay: NotesOverlay) = OverlayWords(
    badge = stringResource(R.string.overlay_badge),
    signature = stringResource(R.string.overlay_signature, OverlayWords.APP_NAME),
    toleranceLine = stringResource(R.string.session_summary_in_tune) + stringResource(R.string.dot_separator) +
        stringResource(R.string.session_summary_tolerance, overlay.toleranceCents),
    bestNote = stringResource(R.string.overlay_best_note),
    drift = stringResource(R.string.session_drift_title),
    driftCents = overlay.drift?.let { stringResource(R.string.session_cents_value, Formats.signedCents(it.meanCents)) },
    driftNone = stringResource(R.string.overlay_drift_none),
    previousTake = stringResource(R.string.overlay_previous_take),
    previousScore = overlay.previous?.let { stringResource(R.string.session_percent, it.scorePercent) },
)

/**
 * A frame of [width] × [height] pixels at the moment [nowMs], shown [shownWidthDp] wide; [cropTo] — only that part of it, enlarged;
 * [showSafe] — the safe zone of a tall frame dashed over it (since 0.91), as overlay.html (section 7) draws it.
 */
@Composable
private fun Frame(width: Int, height: Int, nowMs: Long, shownWidthDp: Int, overlay: NotesOverlay, cropTo: Rect? = null, showSafe: Boolean = false) {
    val words = words(overlay)
    val text = OverlayText(LocalFontFamilyResolver.current, Manrope, imageResource(Res.drawable.overlay_app_icon))
    val painter = remember(overlay, words, width, height) { NotesOverlayPainter(overlay, words, text, width.toFloat(), height.toFloat()) }
    val shown = cropTo ?: Rect(0f, 0f, width.toFloat(), height.toFloat())
    Canvas(Modifier.size(shownWidthDp.dp, (shownWidthDp * shown.height / shown.width).dp).clipToBounds()) {
        scale(size.width / shown.width, pivot = Offset.Zero) {
            translate(-shown.left, -shown.top) {
                picture(width.toFloat(), height.toFloat())
                painter.draw(this, nowMs, VideoEndMs)
                painter.geometry.safe?.takeIf { showSafe }?.let { safe ->
                    drawRect(
                        SafeLine,
                        safe.topLeft,
                        safe.size,
                        style = Stroke(width = SAFE_LINE_PX, pathEffect = PathEffect.dashPathEffect(floatArrayOf(SAFE_DASH_PX, SAFE_GAP_PX))),
                    )
                }
            }
        }
    }
}

/** The dashes of the safe zone, in pixels of the frame: a mark of the preview, never in a file. */
private val SafeLine = Color(0xCCFF5FA0)
private const val SAFE_LINE_PX = 4f
private const val SAFE_DASH_PX = 24f
private const val SAFE_GAP_PX = 16f

/** A warm room and a player with a violin: something to draw over. */
private fun DrawScope.picture(width: Float, height: Float) {
    val short = minOf(width, height)
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFF6A4E3A), 0.5f to Color(0xFF2E2420), 1f to Color(0xFF120F0F),
            center = Offset(width / 2, height * 0.32f), radius = maxOf(width, height) * 0.8f,
        ),
        size = Size(width, height),
    )
    drawRect(Color(0x2EFFECC8), Offset(width * 0.06f, height * 0.08f), Size(width * 0.22f, height * 0.34f))
    drawRect(Color(0x38D2C8BE), Offset(0f, height * 0.78f), Size(width, height * 0.22f))
    drawOval(Color(0xFF3B2D2A), Offset(width * 0.52f - short * 0.2f, height * 0.46f - short * 0.22f), Size(short * 0.4f, short * 0.68f))
    drawCircle(Color(0xFF3B2D2A), radius = short * 0.09f, center = Offset(width * 0.52f, height * 0.46f - short * 0.28f))
    rotate(degrees = 28f, pivot = Offset(width * 0.53f, height * 0.40f)) {
        drawOval(Color(0xFF8A5B3A), Offset(width * 0.53f - short * 0.07f, height * 0.40f - short * 0.16f), Size(short * 0.14f, short * 0.32f))
    }
}

@Preview(name = "Видео с нотами · лента, портрет 3 : 4, 1080 × 1440: ярлык ноты над чертой, бейдж «Анализ игры» и строка приложения", locale = "ru", widthDp = 300, heightDp = 400)
@Composable
private fun LanePortraitPreview() = Frame(1_080, 1_440, nowMs = 7_200, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · лента, высокий кадр 1080 × 1920: всё в безопасной зоне (пунктир), бейдж над ярлыком", locale = "ru", widthDp = 300, heightDp = 534)
@Composable
private fun LaneTallPreview() = Frame(1_080, 1_920, nowMs = 7_200, shownWidthDp = 300, overlay = overlay(previous = true, drift = true), showSafe = true)

@Preview(name = "Видео с нотами · лента, landscape 1920 × 1080: полоса ниже, на ширину ≈ 16 с", locale = "ru", widthDp = 560, heightDp = 315)
@Composable
private fun LaneLandscapePreview() = Frame(1_920, 1_080, nowMs = 7_200, shownWidthDp = 560, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · лента, квадрат — по правилам портрета; пауза: ярлыка нет", locale = "ru", widthDp = 360, heightDp = 360)
@Composable
private fun LaneSquarePausePreview() = Frame(1_080, 1_080, nowMs = 400, shownWidthDp = 360, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · итог, портрет 3 : 4: балл, допуск, полоска, три строки", locale = "ru", widthDp = 300, heightDp = 400)
@Composable
private fun SummaryPortraitPreview() = Frame(1_080, 1_440, nowMs = VideoEndMs + 1_000, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · итог, высокий кадр: блок и подпись в безопасной зоне (пунктир)", locale = "ru", widthDp = 300, heightDp = 534)
@Composable
private fun SummaryTallPreview() = Frame(1_080, 1_920, nowMs = VideoEndMs + 1_000, shownWidthDp = 300, overlay = overlay(previous = true, drift = true), showSafe = true)

@Preview(name = "Видео с нотами · итог посреди проявления (200 мс), landscape", locale = "ru", widthDp = 560, heightDp = 315)
@Composable
private fun SummaryFadingPreview() = Frame(1_920, 1_080, nowMs = VideoEndMs + 200, shownWidthDp = 560, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · итог без прошлого дубля и без «уходящих»: «ничего»", locale = "ru", widthDp = 560, heightDp = 315)
@Composable
private fun SummaryShortPreview() = Frame(1_920, 1_080, nowMs = VideoEndMs + 1_000, shownWidthDp = 560, overlay = overlay(previous = false, drift = false))

@Preview(name = "Video with notes · summary in English", locale = "en", widthDp = 300, heightDp = 534)
@Composable
private fun SummaryEnglishPreview() = Frame(1_080, 1_920, nowMs = VideoEndMs + 1_000, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · итог по-немецки: длинные названия строк", locale = "de", widthDp = 300, heightDp = 534)
@Composable
private fun SummaryGermanPreview() = Frame(1_080, 1_920, nowMs = VideoEndMs + 1_000, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

/** A tall frame of 1080 × 1920 cropped around its playhead, as overlay.html (section 6) crops it: the tag, the lane and the dust. */
private val AroundThePlayhead = NotesOverlayGeometry(1_080f, 1_920f, Config).let { g ->
    Rect(g.headX - 26 * g.u, g.laneBottom - 39.6f * g.u, g.headX + 14 * g.u, g.laneBottom + 9.4f * g.u)
}

@Preview(name = "Видео с нотами · заставка, портрет 3 : 4: название и дата наверху, 1,5 с", locale = "ru", widthDp = 300, heightDp = 400)
@Composable
private fun OpeningPortraitPreview() = Frame(1_080, 1_440, nowMs = 1_500, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · заставка, высокий кадр: под верхним интерфейсом, 1,5 с", locale = "ru", widthDp = 300, heightDp = 534)
@Composable
private fun OpeningTallPreview() = Frame(1_080, 1_920, nowMs = 1_500, shownWidthDp = 300, overlay = overlay(previous = true, drift = true), showSafe = true)

@Preview(name = "Видео с нотами · заставка проявляется, опускаясь на место: 0,6 с", locale = "ru", widthDp = 300, heightDp = 534)
@Composable
private fun OpeningComingPreview() = Frame(1_080, 1_920, nowMs = 600, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · заставка, landscape", locale = "ru", widthDp = 560, heightDp = 315)
@Composable
private fun OpeningLandscapePreview() = Frame(1_920, 1_080, nowMs = 1_500, shownWidthDp = 560, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · ярлык «выше»: F#5 ↑ +24, рассыпание у черты, крупно", locale = "ru", widthDp = 324, heightDp = 397)
@Composable
private fun TagSharpPreview() = Frame(1_080, 1_920, nowMs = MinuetNotes.first { it.midi == Midi.getValue("F#5") }.startMs + 150, shownWidthDp = 324, overlay = overlay(previous = true, drift = true), cropTo = AroundThePlayhead)

@Preview(name = "Видео с нотами · ярлык «в строе»: G5 ● +6 и пыль прошлой ноты, крупно", locale = "ru", widthDp = 324, heightDp = 397)
@Composable
private fun TagInTunePreview() = Frame(1_080, 1_920, nowMs = MinuetNotes.first { it.midi == Midi.getValue("G5") }.startMs + 250, shownWidthDp = 324, overlay = overlay(previous = true, drift = true), cropTo = AroundThePlayhead)

@Preview(name = "Видео с нотами · рассыпание посреди долгой ноты: около 60 частиц, крупно", locale = "ru", widthDp = 324, heightDp = 397)
@Composable
private fun DustPreview() = Frame(1_080, 1_920, nowMs = MinuetNotes.last().startMs + 1_200, shownWidthDp = 324, overlay = overlay(previous = true, drift = true), cropTo = AroundThePlayhead)

@Preview(name = "Видео с нотами · итог с подписью приложения, landscape", locale = "ru", widthDp = 560, heightDp = 315)
@Composable
private fun SummarySignatureLandscapePreview() = Frame(1_920, 1_080, nowMs = VideoEndMs + 1_000, shownWidthDp = 560, overlay = overlay(previous = true, drift = true))

@Preview(name = "Видео с нотами · лента по-немецки: «Spielanalyse» и строка приложения в две строки", locale = "de", widthDp = 300, heightDp = 534)
@Composable
private fun LaneGermanPreview() = Frame(1_080, 1_920, nowMs = 7_200, shownWidthDp = 300, overlay = overlay(previous = true, drift = true))
