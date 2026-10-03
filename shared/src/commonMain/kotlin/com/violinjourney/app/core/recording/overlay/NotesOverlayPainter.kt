package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.ui.theme.DarkZoneColors
import com.violinjourney.app.core.ui.theme.Glass
import com.violinjourney.app.core.ui.theme.OverlayInk
import kotlin.math.ceil

/**
 * Draws one frame of «Видео с нотами» (spec 3.37, 5.30) over a picture of [width] × [height] pixels as the player shows it: the
 * lane of notes while the video runs, the summary over its last frame after it. One drawing for Android (into the bitmap of
 * Media3), iOS (into the pixel buffer itself) and the previews. Text is laid out once and kept: a painter serves one file, on
 * one thread.
 */
class NotesOverlayPainter(
    private val overlay: NotesOverlay,
    private val words: OverlayWords,
    text: OverlayText,
    width: Float,
    height: Float,
) {
    private val config = overlay.config
    val geometry = NotesOverlayGeometry(width, height, config)
    private val u = geometry.u
    private val family = text.family
    private val measurer = TextMeasurer(text.resolver, Density(1f), LayoutDirection.Ltr, cacheSize = 0)

    private val labels = HashMap<Int, TextLayoutResult>()
    private val tags = HashMap<Int, TextLayoutResult>()
    private val badge by lazy { measure(words.badge, config.badgeTextU, FontWeight.Bold, tabular = true) }

    /** The ribbon of the summary with neighbours of one zone joined: what is drawn, as shares of the width. */
    private val strip: List<Pair<Float, Zone>> by lazy {
        val total = overlay.ribbon.sumOf { it.buckets }.toFloat()
        val runs = ArrayList<Pair<Float, Zone>>()
        if (total > 0) {
            overlay.ribbon.forEach { piece ->
                val share = piece.buckets / total
                val last = runs.lastOrNull()
                if (last != null && last.second == piece.zone) runs[runs.lastIndex] = (last.first + share) to piece.zone else runs += share to piece.zone
            }
        }
        runs
    }

    private val summary by lazy { SummaryText() }

    /** The frame of the moment [nowMs] of a video that ends at [videoEndMs]; after it — the summary, coming in over the lane. */
    fun draw(scope: DrawScope, nowMs: Long, videoEndMs: Long) {
        if (nowMs < videoEndMs) {
            scope.lane(nowMs, fade = 1f)
        } else {
            val shown = easeOut(((nowMs - videoEndMs).toFloat() / config.summaryFadeMs).coerceIn(0f, 1f))
            // the lane stays as the last frame had it and gives way to the veil, so nothing jumps at the end of the picture
            scope.lane(videoEndMs, fade = 1f - shown)
            scope.summary(shown)
        }
    }

    private fun DrawScope.lane(nowMs: Long, fade: Float) {
        if (fade <= 0f) return
        val g = geometry
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                config.scrimMidStop to Color.Black.copy(alpha = config.scrimMidAlpha),
                1f to Color.Black.copy(alpha = config.scrimBottomAlpha),
                startY = g.scrimTop,
                endY = g.height,
            ),
            topLeft = Offset(0f, g.scrimTop),
            size = Size(g.width, g.height - g.scrimTop),
            alpha = fade,
        )
        val current = overlay.currentAt(nowMs)
        val pill = g.pillHeight
        for (note in overlay.notesBetween(g.shownFromMs(nowMs), g.shownToMs(nowMs))) {
            val left = g.x(note.startMs, nowMs)
            val right = g.pillRight(note.endMs, nowMs)
            if (right <= left) continue
            val centerY = g.pillCenterY(note.midi, overlay.lowMidi, overlay.highMidi)
            val alpha = fade * if (note == current) 1f else config.otherNotesAlpha
            drawRoundRect(zoneColor(note.zone), Offset(left, centerY - pill / 2), Size(right - left, pill), CornerRadius(pill / 2), alpha = alpha)
            val label = labels.getOrPut(note.midi) { measure(note.name, config.labelU, FontWeight.ExtraBold) }
            if (g.labelFits(label.size.width.toFloat(), right - left)) {
                drawText(label, OverlayInk, Offset(left + g.labelInset, centerY - label.size.height / 2f), alpha)
            }
        }
        drawRoundRect(
            Color.White,
            Offset(g.headX - g.playheadWidth / 2, g.playheadTop),
            Size(g.playheadWidth, g.playheadBottom - g.playheadTop),
            CornerRadius(g.playheadWidth / 2),
            alpha = config.playheadAlpha * fade,
        )
        overlay.tagAt(nowMs)?.let { tag ->
            val name = tags.getOrPut(tag.note.midi) { measure(tag.note.name, config.tagTextU, FontWeight.ExtraBold) }
            val tagWidth = g.tagWidth(name.size.width.toFloat())
            val top = g.tagBottom - g.tagHeight
            drawRoundRect(Glass, Offset(g.headX - tagWidth / 2, top), Size(tagWidth, g.tagHeight), CornerRadius(g.tagHeight / 2), alpha = tag.alpha * fade)
            drawText(name, zoneColor(tag.note.zone), Offset(g.headX - name.size.width / 2f, top + (g.tagHeight - name.size.height) / 2), tag.alpha * fade)
        }
        val badgeWidth = g.badgeWidth(badge.size.width.toFloat())
        drawRoundRect(Glass, Offset(g.badgeLeft, g.badgeTop), Size(badgeWidth, g.badgeHeight), CornerRadius(g.badgeHeight / 2), alpha = fade)
        drawCircle(DarkZoneColors.inTune, radius = config.badgeDotU * u / 2, center = Offset(g.badgeDotCenterX, g.badgeTop + g.badgeHeight / 2), alpha = fade)
        drawText(badge, Color.White, Offset(g.badgeTextX, g.badgeTop + (g.badgeHeight - badge.size.height) / 2), fade)
    }

    private fun DrawScope.summary(shown: Float) {
        if (shown <= 0f) return
        val g = geometry
        val text = summary
        val rows = text.rows.size
        // sized to the frame, not to the scope: a preview draws the frame scaled down
        drawRect(OverlayInk, size = Size(g.width, g.height), alpha = config.veilAlpha * shown)
        val left = g.columnLeft
        drawText(text.title, Color.White, Offset(left, g.titleBaseline(rows) - text.title.firstBaseline), config.textAlpha * shown)
        drawText(text.score, Color.White, Offset(left, g.scoreBaseline(rows) - text.score.firstBaseline), shown)
        drawText(
            text.percent,
            Color.White,
            Offset(left + text.score.size.width + config.percentGapU * u, g.scoreBaseline(rows) - text.percent.firstBaseline),
            config.percentAlpha * shown,
        )
        drawText(text.tolerance, Color.White, Offset(left, g.toleranceBaseline(rows) - text.tolerance.firstBaseline), config.textAlpha * shown)

        val stripTop = g.stripTop(rows)
        val stripPath = Path().apply {
            addRoundRect(RoundRect(left, stripTop, left + g.columnWidth, stripTop + g.stripHeight, CornerRadius(g.stripHeight / 2)))
        }
        clipPath(stripPath) {
            var x = left
            strip.forEach { (share, zone) ->
                val w = share * g.columnWidth
                // a hair over the next piece: two pieces never leave a seam of the frame between them
                drawRect(zoneColor(zone), Offset(x, stripTop), Size(w + SEAM, g.stripHeight), alpha = shown)
                x += w
            }
        }

        text.rows.forEachIndexed { index, row ->
            if (index > 0) {
                drawRect(Color.White, Offset(left, g.rowTop(rows, index)), Size(g.columnWidth, config.ruleU * u), alpha = config.ruleAlpha * shown)
            }
            val baseline = g.rowBaseline(rows, index)
            drawText(row.label, Color.White, Offset(left, baseline - row.label.firstBaseline), config.textAlpha * shown)
            var x = left + g.columnWidth - row.valueWidth
            when (row.sign) {
                RowSign.UP, RowSign.DOWN -> {
                    arrow(x, baseline, row.sign == RowSign.UP, row.color, shown)
                    x += (config.arrowU + config.arrowGapU) * u
                }
                RowSign.DOT -> {
                    drawCircle(row.color, radius = config.dotU * u / 2, center = Offset(x + config.dotU * u / 2, baseline - config.dotRaiseU * u), alpha = shown)
                    x += (config.dotU + config.dotGapU) * u
                }
                RowSign.NONE -> Unit
            }
            drawText(row.value, row.color, Offset(x, baseline - row.value.firstBaseline), shown)
            row.gain?.let { gain ->
                drawText(gain, DarkZoneColors.inTune, Offset(x + row.value.size.width, baseline - gain.firstBaseline), shown)
            }
        }
    }

    /** The arrow of «Что уходит»: up — the note sits high, down — low (spec 3.37); a head on a stem, standing on the baseline. */
    private fun DrawScope.arrow(x: Float, baseline: Float, up: Boolean, color: Color, alpha: Float) {
        val width = config.arrowU * u
        val head = config.arrowHeadU * u
        val top = baseline - config.arrowHeightU * u
        val stem = config.arrowStemU * u
        val stemLeft = x + (width - stem) / 2
        val path = Path()
        if (up) {
            path.moveTo(x + width / 2, top)
            path.lineTo(x + width, top + head)
            path.lineTo(x, top + head)
            drawRect(color, Offset(stemLeft, top + head - SEAM), Size(stem, baseline - top - head + SEAM), alpha = alpha)
        } else {
            path.moveTo(x + width / 2, baseline)
            path.lineTo(x + width, baseline - head)
            path.lineTo(x, baseline - head)
            drawRect(color, Offset(stemLeft, top), Size(stem, baseline - top - head + SEAM), alpha = alpha)
        }
        path.close()
        drawPath(path, color, alpha = alpha)
    }

    private fun measure(text: String, sizeU: Float, weight: FontWeight, tabular: Boolean = false, maxWidth: Float? = null): TextLayoutResult =
        measurer.measure(
            text = text,
            style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = (sizeU * u).sp, fontFeatureSettings = if (tabular) TABULAR else null),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = maxWidth?.let { Constraints(maxWidth = ceil(it).toInt().coerceAtLeast(1)) } ?: Constraints(),
        )

    private enum class RowSign { NONE, UP, DOWN, DOT }

    private class Row(val label: TextLayoutResult, val value: TextLayoutResult, val gain: TextLayoutResult?, val sign: RowSign, val color: Color, val valueWidth: Float)

    /** The text of the summary, laid out the first time the summary is drawn. */
    private inner class SummaryText {
        private val column = geometry.columnWidth
        val title = measure(overlay.title, config.textU, FontWeight.SemiBold, maxWidth = column)
        val score = measure(overlay.scorePercent.toString(), config.scoreU, FontWeight.ExtraBold, tabular = true)
        val percent = measure(PERCENT_SIGN, config.percentU, FontWeight.ExtraBold)
        val tolerance = measure(words.toleranceLine, config.textU, FontWeight.SemiBold, maxWidth = column)
        val rows: List<Row> = buildList {
            overlay.bestMidi?.let { add(row(words.bestNote, Note(it).name, RowSign.NONE, DarkZoneColors.inTune)) }
            val drift = overlay.drift
            add(
                if (drift != null && words.driftCents != null) {
                    row(words.drift, drift.name + VALUE_SPACE + words.driftCents, if (drift.meanCents > 0) RowSign.UP else RowSign.DOWN, zoneColor(drift.zone))
                } else {
                    row(words.drift, words.driftNone, RowSign.DOT, DarkZoneColors.inTune)
                },
            )
            val previous = overlay.previous
            if (previous != null && words.previousScore != null) {
                add(row(words.previousTake, words.previousScore, RowSign.NONE, Color.White, previous.gainPercent?.let { GAIN_SIGN + it }))
            }
        }

        private fun row(label: String, value: String, sign: RowSign, color: Color, gain: String? = null): Row {
            val valueText = measure(value, config.rowValueU, FontWeight.ExtraBold, tabular = true)
            val gainText = gain?.let { measure(VALUE_SPACE + it, config.gainU, FontWeight.ExtraBold, tabular = true) }
            val signWidth = when (sign) {
                RowSign.UP, RowSign.DOWN -> (config.arrowU + config.arrowGapU) * u
                RowSign.DOT -> (config.dotU + config.dotGapU) * u
                RowSign.NONE -> 0f
            }
            val valueWidth = signWidth + valueText.size.width + (gainText?.size?.width ?: 0)
            val labelText = measure(label, config.textU, FontWeight.SemiBold, maxWidth = column - valueWidth - config.rowGapU * u)
            return Row(labelText, valueText, gainText, sign, color, valueWidth)
        }
    }

    private companion object {
        const val TABULAR = "tnum"
        const val PERCENT_SIGN = "%"
        const val GAIN_SIGN = "+"
        const val VALUE_SPACE = " "

        /** A pixel's overlap of two shapes that meet, so the frame never shows between them. */
        const val SEAM = 1f

        fun zoneColor(zone: Zone): Color = DarkZoneColors.colorFor(zone)

        /** Slowing towards the end (spec 5.30). */
        fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
    }
}
