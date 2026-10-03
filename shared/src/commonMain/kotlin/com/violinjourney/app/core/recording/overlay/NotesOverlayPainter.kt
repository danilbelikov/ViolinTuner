package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.ui.theme.DarkZoneColors
import com.violinjourney.app.core.ui.theme.Glass
import com.violinjourney.app.core.ui.theme.OverlayInk
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws one frame of «Видео с нотами» (spec 3.37, 5.30) over a picture of [width] × [height] pixels as the player shows it: the
 * lane of notes while the video runs, the opening title over its first seconds, the summary over its last frame after it. One
 * drawing for Android (into the bitmap of Media3), iOS (into the pixel buffer itself) and the previews. Text is laid out once
 * and kept: a painter serves one file, on one thread. A frame depends on its own time alone — the spinner and the dust too.
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
    private val icon = text.icon
    private val measurer = TextMeasurer(text.resolver, Density(1f), LayoutDirection.Ltr, cacheSize = 0)

    private val labels = HashMap<Int, TextLayoutResult>()
    private val tags = HashMap<Int, TextLayoutResult>()
    private val numbers = HashMap<String, TextLayoutResult>()
    private val badge by lazy { measure(words.badge, config.badgeTextU, FontWeight.Bold) }

    /** The dimmed line of the app right of the badge; null where the frame leaves it no room. */
    private val appLine: TextLayoutResult? by lazy {
        val room = geometry.appLineMaxWidth(geometry.badgeWidth(badge.size.width.toFloat()))
        if (room <= 0f) null else measureLines(appWords(config.appLineAlpha, config.appLineNameAlpha), config.appLineTextU, config.appLineHeightU, room)
    }

    private val openingTitle by lazy { measure(overlay.heading, config.openingTitleU, FontWeight.ExtraBold, maxWidth = geometry.columnWidth) }
    private val openingDate by lazy { measure(overlay.date, config.openingDateU, FontWeight.SemiBold, maxWidth = geometry.columnWidth) }

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

    /** Whether the frame of [nowMs] of a video that ends at [videoEndMs] carries the opening title. */
    fun openingShows(nowMs: Long, videoEndMs: Long): Boolean = overlay.openingAt(nowMs, videoEndMs) != null

    /**
     * The share of the em above the baseline: a single line of text with no line height of its own is the font's ascent and
     * descent, so its first baseline over its height is that share.
     */
    private val emAscent by lazy { openingDate.firstBaseline / openingDate.size.height.coerceAtLeast(1) }

    /** How far down from the top the opening title draws: its shade, or its date where that reaches lower. */
    val openingBottom: Float
        get() = max(geometry.openingScrimHeight, geometry.openingDateBaseline(emAscent) - openingDate.firstBaseline + openingDate.size.height)

    /** The frame of the moment [nowMs] of a video that ends at [videoEndMs]; after it — the summary, coming in over the lane. */
    fun draw(scope: DrawScope, nowMs: Long, videoEndMs: Long) {
        if (nowMs < videoEndMs) {
            scope.lane(nowMs, fade = 1f)
            // the opening is over a second before the end of the video: the summary never meets it
            scope.opening(nowMs, videoEndMs)
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
        // only what is ahead: a capsule that reaches the playhead crumbles into dust there (since 0.90)
        clipRect(left = g.headX, top = 0f, right = g.width, bottom = g.height) {
            for (note in overlay.notesBetween(g.shownFromMs(nowMs), g.shownToMs(nowMs))) {
                val left = g.x(note.startMs, nowMs)
                val right = g.pillRight(note.endMs, nowMs)
                // the part still to be seen: right of the playhead
                val from = max(left, g.headX)
                if (right <= from) continue
                val centerY = g.pillCenterY(note.midi, overlay.lowMidi, overlay.highMidi)
                val alpha = fade * if (note == current) 1f else config.otherNotesAlpha
                drawRoundRect(zoneColor(note.zone), Offset(left, centerY - pill / 2), Size(right - left, pill), CornerRadius(pill / 2), alpha = alpha)
                // the name keeps to the start of what is left and goes while that still takes it: no stub of a letter at the playhead
                val label = labels.getOrPut(note.midi) { measure(note.name, config.labelU, FontWeight.ExtraBold) }
                if (g.labelFits(label.size.width.toFloat(), right - from)) {
                    drawText(label, OverlayInk, Offset(from + g.labelInset, centerY - label.size.height / 2f), alpha)
                }
            }
        }
        NotesOverlayDust.particlesAt(nowMs, overlay, g).forEach { particle ->
            val side = particle.side
            drawRoundRect(
                zoneColor(particle.zone),
                Offset(particle.x - side / 2, particle.y - side / 2),
                Size(side, side),
                CornerRadius(side * config.dustCorner),
                alpha = particle.alpha * fade,
            )
        }
        drawRoundRect(
            Color.White,
            Offset(g.headX - g.playheadWidth / 2, g.playheadTop),
            Size(g.playheadWidth, g.playheadBottom - g.playheadTop),
            CornerRadius(g.playheadWidth / 2),
            alpha = config.playheadAlpha * fade,
        )
        overlay.tagAt(nowMs)?.let { tag(it, fade) }
        badge(nowMs, fade)
    }

    /** The tag over the playhead: the name in the colour of its zone, then — the arrow or the dot of in tune — and the grey number. */
    private fun DrawScope.tag(tag: OverlayTag, fade: Float) {
        val g = geometry
        val note = tag.note
        val sign = note.sign
        val inTune = sign == OverlaySign.DOT
        val alpha = tag.alpha * fade
        val color = zoneColor(note.zone)
        val name = tags.getOrPut(note.midi) { measure(note.name, config.tagTextU, FontWeight.ExtraBold) }
        val cents = note.centsText
        val number = numbers.getOrPut(cents) { measure(cents, config.tagNumberU, FontWeight.Bold, tabular = true) }
        val nameWidth = name.size.width.toFloat()
        val line = g.tagLineWidth(nameWidth, number.size.width.toFloat(), inTune)
        val tagWidth = g.tagWidth(line)
        val top = g.tagBottom - g.tagHeight
        val middle = top + g.tagHeight / 2
        drawRoundRect(Glass, Offset(g.headX - tagWidth / 2, top), Size(tagWidth, g.tagHeight), CornerRadius(g.tagHeight / 2), alpha = alpha)
        val nameTop = top + (g.tagHeight - name.size.height) / 2
        drawText(name, color, Offset(g.headX - line / 2, nameTop), alpha)
        val signX = g.tagSignX(line, nameWidth)
        if (inTune) {
            val radius = config.tagDotU * u / 2
            drawCircle(color, radius = radius, center = Offset(signX + radius, middle), alpha = alpha)
        } else {
            // the arrow of «Что уходит», smaller, standing in the middle of the tag
            val scale = config.tagArrowU / config.arrowU
            arrow(signX, middle + config.arrowHeightU * scale * u / 2, sign == OverlaySign.UP, color, alpha, scale)
        }
        // on the baseline of the name
        val baseline = nameTop + name.firstBaseline
        drawText(number, Color.White, Offset(g.tagNumberX(line, nameWidth, inTune), baseline - number.firstBaseline), config.tagNumberAlpha * alpha)
    }

    /** The badge: a spinner turning by the time of the frame and «Анализ игры» on the glass, the dimmed line of the app beside it. */
    private fun DrawScope.badge(nowMs: Long, fade: Float) {
        val g = geometry
        val badgeWidth = g.badgeWidth(badge.size.width.toFloat())
        drawRoundRect(Glass, Offset(g.badgeLeft, g.badgeTop), Size(badgeWidth, g.badgeHeight), CornerRadius(g.badgeHeight / 2), alpha = fade)
        val turned = (nowMs % config.spinnerTurnMs).toFloat() / config.spinnerTurnMs * FULL_TURN
        val radius = g.spinnerRadius
        drawArc(
            DarkZoneColors.inTune,
            startAngle = turned,
            sweepAngle = config.spinnerSweepDegrees,
            useCenter = false,
            topLeft = Offset(g.spinnerCenterX - radius, g.badgeCenterY - radius),
            size = Size(radius * 2, radius * 2),
            alpha = fade,
            style = Stroke(width = g.spinnerLine, cap = StrokeCap.Round),
        )
        drawText(badge, Color.White, Offset(g.badgeTextX, g.badgeCenterY - badge.size.height / 2f), fade)
        appLine?.let { line -> drawFaded(line, Offset(g.appLineLeft(badgeWidth), g.badgeCenterY - line.size.height / 2f), fade) }
    }

    /** The opening title: a shade from the top, the name and the date under it, coming in, staying and going out (spec 5.30). */
    private fun DrawScope.opening(nowMs: Long, videoEndMs: Long) {
        val shown = overlay.openingAt(nowMs, videoEndMs) ?: return
        val g = geometry
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black.copy(alpha = config.openingScrimAlpha), 1f to Color.Transparent, startY = 0f, endY = g.openingScrimHeight),
            size = Size(g.width, g.openingScrimHeight),
            alpha = shown.alpha,
        )
        val raise = shown.raiseU * u
        val title = openingTitle
        val date = openingDate
        // by the baseline: the spec places the em boxes, and the top of a laid-out line is the font's ascent, higher than the em's
        val titleTop = g.openingTitleBaseline(emAscent) - title.firstBaseline - raise
        val dateTop = g.openingDateBaseline(emAscent) - date.firstBaseline - raise
        // centred in the column: in a tall frame it stops short of the buttons on the right (since 0.91)
        drawText(title, Color.White, Offset(g.centredLeft(title.size.width.toFloat()), titleTop), shown.alpha)
        drawText(date, Color.White, Offset(g.centredLeft(date.size.width.toFloat()), dateTop), config.openingDateAlpha * shown.alpha)
    }

    private fun DrawScope.summary(shown: Float) {
        if (shown <= 0f) return
        val g = geometry
        val text = summary
        // sized to the frame, not to the scope: a preview draws the frame scaled down
        drawRect(OverlayInk, size = Size(g.width, g.height), alpha = config.veilAlpha * shown)
        val signatureHeight = g.signatureHeight(text.signature.size.height.toFloat())
        signature(text, signatureHeight, shown)
        val top = g.summaryTop(text.rows.size, signatureHeight)
        val left = g.columnLeft
        drawText(text.title, Color.White, Offset(left, g.titleBaseline(top) - text.title.firstBaseline), config.textAlpha * shown)
        drawText(text.score, Color.White, Offset(left, g.scoreBaseline(top) - text.score.firstBaseline), shown)
        drawText(
            text.percent,
            Color.White,
            Offset(left + text.score.size.width + config.percentGapU * u, g.scoreBaseline(top) - text.percent.firstBaseline),
            config.percentAlpha * shown,
        )
        drawText(text.tolerance, Color.White, Offset(left, g.toleranceBaseline(top) - text.tolerance.firstBaseline), config.textAlpha * shown)

        val stripTop = g.stripTop(top)
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
                drawRect(Color.White, Offset(left, g.rowTop(top, index)), Size(g.columnWidth, config.ruleU * u), alpha = config.ruleAlpha * shown)
            }
            val baseline = g.rowBaseline(top, index)
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

    /**
     * The signature at the bottom of the summary (since 0.90): the icon in its rounded square and the line of the app beside it,
     * centred together; the icon in the middle of the text's height.
     */
    private fun DrawScope.signature(text: SummaryText, height: Float, shown: Float) {
        val g = geometry
        val line = text.signature
        val top = g.signatureTop(height)
        val textWidth = text.signatureWidth
        val side = g.iconSize.roundToInt()
        val iconLeft = g.iconLeft(textWidth).roundToInt()
        val iconTop = (top + (height - g.iconSize) / 2).roundToInt()
        val corner = Path().apply {
            addRoundRect(RoundRect(iconLeft.toFloat(), iconTop.toFloat(), (iconLeft + side).toFloat(), (iconTop + side).toFloat(), CornerRadius(g.iconCorner)))
        }
        clipPath(corner) {
            drawImage(
                icon,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(icon.width, icon.height),
                dstOffset = IntOffset(iconLeft, iconTop),
                dstSize = IntSize(side, side),
                alpha = shown,
                filterQuality = FilterQuality.High,
            )
        }
        drawFaded(line, Offset(g.signatureTextLeft(textWidth), top + (height - line.size.height) / 2), shown)
    }

    /**
     * Text whose colours are its own — of spans, which the alpha of `drawText` does not reach — shown at [alpha]: through a layer
     * the size of the text while it fades, straight when whole.
     */
    private fun DrawScope.drawFaded(layout: TextLayoutResult, topLeft: Offset, alpha: Float) {
        if (alpha <= 0f) return
        if (alpha >= 1f) {
            drawText(layout, topLeft = topLeft)
            return
        }
        val bounds = Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat())).inflate(u)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
            drawText(layout, topLeft = topLeft)
            canvas.restore()
        }
    }

    /**
     * «Анализируй свою игру в приложении Violin Journey»: the words at [alpha], the name of the app bolder at [nameAlpha] and held
     * together — a line breaks before it or after it, never inside.
     */
    private fun appWords(alpha: Float, nameAlpha: Float): AnnotatedString {
        val name = OverlayWords.APP_NAME.replace(SPACE, NO_BREAK_SPACE)
        val text = words.signature.replace(OverlayWords.APP_NAME, name)
        val at = text.indexOf(name)
        val plain = SpanStyle(color = Color.White.copy(alpha = alpha))
        return buildAnnotatedString {
            if (at < 0) {
                withStyle(plain) { append(text) }
            } else {
                withStyle(plain) { append(text.substring(0, at)) }
                withStyle(SpanStyle(color = Color.White.copy(alpha = nameAlpha), fontWeight = FontWeight.ExtraBold)) { append(name) }
                withStyle(plain) { append(text.substring(at + name.length)) }
            }
        }
    }

    /** The arrow of «Что уходит»: up — the note sits high, down — low (spec 3.37); a head on a stem, standing on the baseline. */
    private fun DrawScope.arrow(x: Float, baseline: Float, up: Boolean, color: Color, alpha: Float, scale: Float = 1f) {
        val width = config.arrowU * scale * u
        val head = config.arrowHeadU * scale * u
        val top = baseline - config.arrowHeightU * scale * u
        val stem = config.arrowStemU * scale * u
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

    /** Text in lines no wider than [maxWidth], at most [NotesVideoConfig.appLineMaxLines], every line [lineHeightU] high with the text in its middle. */
    private fun measureLines(text: AnnotatedString, sizeU: Float, lineHeightU: Float, maxWidth: Float): TextLayoutResult =
        measurer.measure(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = (sizeU * u).sp,
                lineHeight = (lineHeightU * u).sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = config.appLineMaxLines,
            constraints = Constraints(maxWidth = ceil(maxWidth).toInt().coerceAtLeast(1)),
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
        val signature = measureLines(appWords(config.signatureAlpha, config.signatureNameAlpha), config.signatureTextU, config.signatureLineU, geometry.signatureTextMaxWidth)

        /** The widest line of the signature: a line wrapped short of the width centres by what it holds. */
        val signatureWidth: Float = (0 until signature.lineCount).maxOf { signature.getLineRight(it) - signature.getLineLeft(it) }
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
        const val SPACE = ' '
        const val NO_BREAK_SPACE = '\u00A0'
        const val FULL_TURN = 360f

        /** A pixel's overlap of two shapes that meet, so the frame never shows between them. */
        const val SEAM = 1f

        fun zoneColor(zone: Zone): Color = DarkZoneColors.colorFor(zone)

        /** Slowing towards the end (spec 5.30). */
        fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
    }
}
