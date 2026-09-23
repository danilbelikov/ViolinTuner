package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.PianoRollMath
import com.violinjourney.app.feature.session.SessionContent

private val CardCorner = 20.dp
private val CardPaddingHorizontal = 12.dp
private val CardPaddingTop = 12.dp
private val CardPaddingBottom = 8.dp
private val LabelGutter = 36.dp
private val TicksHeight = 22.dp
private val BarCorner = 5.dp
private val ContourStroke = 1.6.dp
private val SelectionStroke = 3.dp
private val CursorWidth = 2.dp
private const val BAR_ALPHA = 0.9f
private const val TABULAR_FIGURES = "tnum"

/**
 * Piano roll of a session (spec 3.10): rows are the notes played, highest on top; a bar is a
 * note colored by the zone of its mean deviation, the dark line on it is the deviation over
 * time. Time scrolls horizontally; the note labels on the left stay put.
 *
 * The roll is drawn into a viewport-sized canvas with its own scroll offset instead of one very
 * wide canvas: an hour at 30 dp per second is more pixels than a layout may measure.
 */
@Composable
fun PianoRoll(
    content: SessionContent,
    selectedSegment: Int?,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Playback position; null hides the cursor. While [followCursor] the roll scrolls after it. */
    cursorMs: Long? = null,
    followCursor: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = colors.onSurfaceVariant,
        fontSize = 11.sp,
        fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val description = stringResource(R.string.session_roll_description)
    val rowOf = remember(content.rollNotes) { content.rollNotes.withIndex().associate { (row, note) -> note to row } }
    val bars = remember(content.segments, rowOf) {
        content.segments.map { Triple(rowOf.getValue(it.note), it.startMs, it.endMs) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(CardCorner))
            .padding(
                start = CardPaddingHorizontal,
                end = CardPaddingHorizontal,
                top = CardPaddingTop,
                bottom = CardPaddingBottom,
            ),
    ) {
        val viewportDp = (maxWidth - LabelGutter).value
        val math = remember(content.durationMs, content.rollNotes.size, viewportDp) {
            PianoRollMath(content.durationMs, content.rollNotes.size, viewportDp)
        }
        val density = LocalDensity.current.density
        var scrollDp by remember(math) { mutableFloatStateOf(0f) }
        // in an effect, not in composition: this writes the state the composition reads
        LaunchedEffect(cursorMs, followCursor, math) {
            if (followCursor && cursorMs != null) math.scrollToFollow(cursorMs, scrollDp)?.let { scrollDp = it }
        }
        val currentMath by rememberUpdatedState(math)
        val currentBars by rememberUpdatedState(bars)
        val currentOnClick by rememberUpdatedState(onSegmentClick)

        Column(
            modifier = Modifier
                .semantics { contentDescription = description }
                .scrollable(
                    orientation = Orientation.Horizontal,
                    // dragging left (negative delta) moves forward in time
                    state = rememberScrollableState { deltaPx ->
                        val before = scrollDp
                        scrollDp = (scrollDp - deltaPx / density).coerceIn(0f, currentMath.maxScroll)
                        (before - scrollDp) * density
                    },
                ),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TicksHeight),
            ) {
                val gutter = LabelGutter.toPx()
                clipRect(left = gutter) {
                    for (tickMs in math.tickTimesMs()) {
                        val x = gutter + (math.x(tickMs) - scrollDp).dp.toPx()
                        if (x < gutter - size.width || x > size.width) continue
                        // measured unconstrained: a label near the right edge is clipped, not wrapped
                        val label = textMeasurer.measure(Formats.duration(tickMs), labelStyle, softWrap = false, maxLines = 1)
                        drawText(label, topLeft = Offset(x, 0f))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(math.viewportHeight.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(math.contentHeight.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val x = (tap.x - LabelGutter.toPx()) / density + scrollDp
                                if (tap.x < LabelGutter.toPx()) return@detectTapGestures
                                currentMath.hitTest(x, tap.y / density, currentBars)?.let(currentOnClick)
                            }
                        },
                ) {
                    val gutter = LabelGutter.toPx()
                    content.rollNotes.forEachIndexed { row, note ->
                        val y = math.rowLineY(row).dp.toPx()
                        drawLine(colors.surfaceContainerHigh, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        val label = textMeasurer.measure(note.name, labelStyle, softWrap = false, maxLines = 1)
                        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
                    }
                    if (cursorMs != null) {
                        val x = gutter + (math.x(cursorMs) - scrollDp).dp.toPx()
                        if (x >= gutter && x <= size.width) {
                            drawLine(colors.primary, Offset(x, 0f), Offset(x, size.height), CursorWidth.toPx())
                        }
                    }
                    clipRect(left = gutter) {
                        content.segments.forEachIndexed { index, segment ->
                            val left = gutter + (math.x(segment.startMs) - scrollDp).dp.toPx()
                            val width = math.barWidth(segment.startMs, segment.endMs).dp.toPx()
                            if (left + width < gutter || left > size.width) return@forEachIndexed
                            val top = math.barTop(bars[index].first).dp.toPx()
                            val barSize = Size(width, PianoRollMath.BAR_HEIGHT.dp.toPx())
                            val corner = CornerRadius(BarCorner.toPx())
                            drawRoundRect(
                                color = zoneColors.colorFor(segment.zone).copy(alpha = BAR_ALPHA),
                                topLeft = Offset(left, top),
                                size = barSize,
                                cornerRadius = corner,
                            )
                            if (segment.contour.size > 1) {
                                val path = Path()
                                segment.contour.forEachIndexed { i, cents ->
                                    val x = left + width * i / (segment.contour.size - 1)
                                    val y = top + math.contourY(cents).dp.toPx()
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, colors.surface, style = Stroke(ContourStroke.toPx(), join = StrokeJoin.Round))
                            }
                            if (index == selectedSegment) {
                                drawRoundRect(
                                    color = colors.primary,
                                    topLeft = Offset(left, top),
                                    size = barSize,
                                    cornerRadius = corner,
                                    style = Stroke(SelectionStroke.toPx()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
