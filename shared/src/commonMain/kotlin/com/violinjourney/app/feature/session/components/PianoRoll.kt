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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.PianoRollMath
import com.violinjourney.app.feature.session.SessionContent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_roll_description
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** The roll's own card: 12 at the sides and at the top, 8 under the rows (spec 3.10). */
private val RollPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)

/** Between the label of the card and the time line. */
private val LabelBottom = 10.dp
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
 * wide canvas: an hour at 30 dp per second is more pixels than a layout may measure. Three canvases
 * lie one over the other: the row lines and their labels, the bars, and on top the cursor in a
 * layer of its own — where it crosses the note that sounds, the place that matters most, it is seen
 * over the bar, not hidden under it (spec 3.10). It takes no touch, so taps reach the bars beneath.
 * The cursor reads [cursorMs] where it is drawn, so while the sound plays only its thin layer is
 * redrawn; the rest waits for a scroll or a tap. The row labels are measured once.
 *
 * The card (spec 3.36.5): a corner of 18; [label] — the label of the card «Ноты» over the time line, with [contentPadding] the fields
 * of the other cards of the screen. The note picked ([selectedSegment]) is brought into view — the rows scrolled to it, and its row
 * where there are more than twelve — whether a tap picked it or a row of «Что уходит» did; one already in view stays where it is.
 * While a note is picked the roll does not follow the cursor, playing or not: the sheet of the note is open over it, and the roll
 * keeps the note it outlines in view; once the note is let go (the sheet hidden, «Слушать это место»), it follows the cursor again.
 */
@Composable
fun PianoRoll(
    content: SessionContent,
    selectedSegment: Int?,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Where playback is, read where the cursor is drawn; null hides the cursor. While [followCursor] and no note is picked the roll scrolls after it. */
    cursorMs: (() -> Long)? = null,
    followCursor: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = RollPadding,
) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val textMeasurer = rememberTextMeasurer()
    val labelFamily = MaterialTheme.typography.labelSmall.fontFamily
    val labelStyle = remember(colors.onSurfaceVariant, labelFamily) {
        TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = labelFamily,
            fontFeatureSettings = TABULAR_FIGURES,
        )
    }
    val description = stringResource(Res.string.session_roll_description)
    val rowOf = remember(content.rollNotes) { content.rollNotes.withIndex().associate { (row, note) -> note to row } }
    val bars = remember(content.segments, rowOf) {
        content.segments.map { Triple(rowOf.getValue(it.note), it.startMs, it.endMs) }
    }
    val rowLabels = remember(content.rollNotes, labelStyle, textMeasurer) {
        content.rollNotes.map { textMeasurer.measure(it.name, labelStyle, softWrap = false, maxLines = 1) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .padding(contentPadding),
    ) {
        if (label != null) {
            Box(Modifier.padding(bottom = LabelBottom)) { label() }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportDp = (maxWidth - LabelGutter).value
            val math = remember(content.durationMs, content.rollNotes.size, viewportDp) {
                PianoRollMath(content.durationMs, content.rollNotes.size, viewportDp)
            }
            val density = LocalDensity.current.density
            var scrollDp by remember(math) { mutableFloatStateOf(0f) }
            val rowsScroll = rememberScrollState()
            // the note picked holds the roll: following the cursor would take it out of view with the next chunk of sound
            val following = followCursor && selectedSegment == null
            // in an effect, not in composition: this writes the state the composition reads
            LaunchedEffect(following, math, cursorMs) {
                if (following && cursorMs != null) {
                    snapshotFlow { cursorMs() }.collect { at -> math.scrollToFollow(at, scrollDp)?.let { scrollDp = it } }
                }
            }
            // the note picked comes into view: from «Что уходит» it may be far along the roll or among the rows below the twelfth
            LaunchedEffect(selectedSegment, math) {
                val segment = selectedSegment?.let { content.segments.getOrNull(it) } ?: return@LaunchedEffect
                math.scrollToShow(segment.startMs, segment.endMs, scrollDp)?.let { scrollDp = it }
                val row = rowOf[segment.note] ?: return@LaunchedEffect
                math.rowScrollToShow(row, rowsScroll.value / density)?.let { rowsScroll.scrollTo((it * density).roundToInt()) }
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
                        for (tickMs in math.visibleTickTimesMs(scrollDp)) {
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
                        .verticalScroll(rowsScroll),
                ) {
                    val rollSize = Modifier
                        .fillMaxWidth()
                        .height(math.contentHeight.dp)
                    // under the bars: the row lines and the names of the notes
                    Canvas(rollSize) {
                        content.rollNotes.indices.forEach { row ->
                            val y = math.rowLineY(row).dp.toPx()
                            drawLine(colors.surfaceContainerHigh, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            val label = rowLabels[row]
                            drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
                        }
                    }
                    // the bars, their contours and the note picked
                    Canvas(
                        // Keyed by the geometry: the scroll is remembered per [math], so a new width (a rotation on iOS, where the
                        // composition lives on) brings a new scroll, and a detector of the old one would read an offset that no
                        // longer moves. The density is the scope's own for the same reason.
                        modifier = rollSize.pointerInput(math) {
                            detectTapGestures { tap ->
                                val gutter = LabelGutter.toPx()
                                if (tap.x < gutter) return@detectTapGestures
                                val x = (tap.x - gutter).toDp().value + scrollDp
                                currentMath.hitTest(x, tap.y.toDp().value, currentBars)?.let(currentOnClick)
                            }
                        },
                    ) {
                        val gutter = LabelGutter.toPx()
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
                    // over the bars, last: the cursor is seen on the very note it is playing
                    if (cursorMs != null) {
                        Canvas(rollSize.graphicsLayer()) {
                            val gutter = LabelGutter.toPx()
                            val x = gutter + (math.x(cursorMs()) - scrollDp).dp.toPx()
                            if (x >= gutter && x <= size.width) {
                                drawLine(colors.primary, Offset(x, 0f), Offset(x, size.height), CursorWidth.toPx())
                            }
                        }
                    }
                }
            }
        }
    }
}
