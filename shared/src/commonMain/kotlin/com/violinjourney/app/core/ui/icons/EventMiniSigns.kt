package com.violinjourney.app.core.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.violinjourney.app.core.domain.events.KindSign

/**
 * The mini signs of the kinds of events (spec 3.36.9, 5.29 R9): a filled silhouette of each of the sixteen signs on a grid of 12 —
 * the mark of a kind in a cell of the calendar (10, 9 or 8 dp), in «ещё N» of the reminder (10) and in the legend (12) — and the
 * «+» of a fourth event, a cross of two strokes in a box of 6 × 10. The outlines are those of the table «Для реализации» of redesign
 * events-kinds.html, as they are: filled by the nonzero rule (the holes of the masks go round the other way). Each is parsed once,
 * on first use, and a cell draws them without making anything.
 */
object EventMiniSigns {
    /** The grid of a mini sign: a sign of [size] is it scaled by size / 12. */
    const val GRID = 12f

    /** The box of «+»: 6 × 10, a stroke of 1.6 with round ends — all of them scaled with the box. */
    const val MORE_WIDTH = 6f
    const val MORE_HEIGHT = 10f
    const val MORE_STROKE = 1.6f

    /** The outline of every sign, `d` of an SVG path on the grid of 12. */
    val OUTLINES: Map<KindSign, String> = mapOf(
        // the built-in four: ■ ● ▲ ◆
        KindSign.LESSON to "M3.5 1.5h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z",
        KindSign.REHEARSAL to "M6 1a5 5 0 1 1 0 10A5 5 0 0 1 6 1z",
        KindSign.PERFORMANCE to "M6.87 1.5l4.5 8a1 1 0 0 1-.87 1.5h-9a1 1 0 0 1-.87-1.5l4.5-8a1 1 0 0 1 1.74 0z",
        KindSign.OTHER to "M6.7.9l4.4 4.4a1 1 0 0 1 0 1.4L6.7 11.1a1 1 0 0 1-1.4 0L.9 6.7a1 1 0 0 1 0-1.4L5.3.9a1 1 0 0 1 1.4 0z",
        // the twelve of one's own
        KindSign.BOOK to "M3 1h6a1 1 0 0 1 1 1v9.1a.6.6 0 0 1-.95.5L6 9.3l-3.05 2.3A.6.6 0 0 1 2 11.1V2a1 1 0 0 1 1-1z",
        KindSign.HAT to "M6 1l5.8 2.9L6 6.8.2 3.9z M2.6 5.6 6 7.3l3.4-1.7v2.8c0 1.4-1.5 2.6-3.4 2.6S2.6 9.8 2.6 8.4z",
        KindSign.KEYS to "M1.9 1.5h.6a.9.9 0 0 1 .9.9v7.2a.9.9 0 0 1-.9.9h-.6a.9.9 0 0 1-.9-.9V2.4a.9.9 0 0 1 .9-.9z " +
            "M5.7 1.5h.6a.9.9 0 0 1 .9.9v7.2a.9.9 0 0 1-.9.9h-.6a.9.9 0 0 1-.9-.9V2.4a.9.9 0 0 1 .9-.9z " +
            "M9.5 1.5h.6a.9.9 0 0 1 .9.9v7.2a.9.9 0 0 1-.9.9h-.6a.9.9 0 0 1-.9-.9V2.4a.9.9 0 0 1 .9-.9z",
        KindSign.MASK to "M1 1.5h10v4.3a5 5 0 0 1-10 0z M3 4v1.7h2.2V4z M6.8 4v1.7H9V4z",
        KindSign.TICKET to "M1.5 2.5h9a1 1 0 0 1 1 1v1.2a1.3 1.3 0 0 0 0 2.6v1.2a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V7.3a1.3 1.3 0 0 0 0-2.6V3.5a1 1 0 0 1 1-1z",
        KindSign.CHAT to "M6 1.3c2.9 0 5.2 2 5.2 4.4S8.9 10.1 6 10.1c-.6 0-1.2-.1-1.8-.2l-3 1.3.9-2.4C1.3 8 .8 6.9.8 5.7.8 3.3 3.1 1.3 6 1.3z",
        KindSign.HEART to "M6 10.8C2.6 8.4 1 6.6 1 4.6A2.7 2.7 0 0 1 6 3.1a2.7 2.7 0 0 1 5 1.5c0 2-1.6 3.8-5 6.2z",
        KindSign.MOON to "M5.2 1.3A5 5 0 1 0 10.95 6.8 4.1 4.1 0 0 1 5.2 1.3z",
        KindSign.LEAF to "M1.2 10.8C1.2 5 5 1.2 10.8 1.2c0 5.8-3.8 9.6-9.6 9.6z",
        KindSign.BOLT to "M7.3.6 1.6 7h3.7l-.9 4.4L10.4 5H6.7z",
        KindSign.BOWTIE to "M1.4 2.6 5 4.6h2l3.6-2a.6.6 0 0 1 .9.5v5.8a.6.6 0 0 1-.9.5L7 7.4H5L1.4 9.4a.6.6 0 0 1-.9-.5V3.1a.6.6 0 0 1 .9-.5z",
        KindSign.ARC to "M.8 10.4V6.8a5.2 5.2 0 0 1 10.4 0v3.6H8.4V6.8a2.4 2.4 0 0 0-4.8 0v3.6z",
    )

    /** «+» of a fourth event and further (spec 5.28): two strokes, `m-more` of events-kinds.html. */
    const val MORE = "M3 2.2v5.6M.4 5h5.2"

    private val paths: Map<KindSign, Path> by lazy { OUTLINES.mapValues { (_, d) -> PathParser().parsePathString(d).toPath() } }

    /** The parsed cross of [MORE], on its grid of 6 × 10. */
    internal val morePath: Path by lazy { PathParser().parsePathString(MORE).toPath() }

    /** The stroke of «+» on its own grid: scaled with the canvas, as the stroke of the SVG is with its box. */
    internal val MoreStroke = Stroke(width = MORE_STROKE, cap = StrokeCap.Round)

    /** The parsed outline of [sign], on the grid of 12. */
    fun path(sign: KindSign): Path = paths.getValue(sign)
}

/** [sign] filled with [color], its grid of 12 scaled to a square of [size] px at [topLeft] ([EventMiniSigns]). */
fun DrawScope.drawMiniSign(sign: KindSign, color: Color, topLeft: Offset, size: Float) {
    val path = EventMiniSigns.path(sign)
    translate(topLeft.x, topLeft.y) {
        scale(size / EventMiniSigns.GRID, size / EventMiniSigns.GRID, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

/**
 * «+» in [color] in a box of [width] × [height] px at [topLeft] ([EventMiniSigns.MORE]): the cross scaled as a whole to fit the box —
 * by height / 10 in a box of 6 × 10 — in its middle, its stroke scaled with it, as the SVG of the mockup draws it in its box.
 */
fun DrawScope.drawMore(color: Color, topLeft: Offset, width: Float, height: Float) {
    val factor = minOf(width / EventMiniSigns.MORE_WIDTH, height / EventMiniSigns.MORE_HEIGHT)
    val left = topLeft.x + (width - EventMiniSigns.MORE_WIDTH * factor) / 2
    val top = topLeft.y + (height - EventMiniSigns.MORE_HEIGHT * factor) / 2
    translate(left, top) {
        scale(factor, factor, pivot = Offset.Zero) { drawPath(EventMiniSigns.morePath, color, style = EventMiniSigns.MoreStroke) }
    }
}
