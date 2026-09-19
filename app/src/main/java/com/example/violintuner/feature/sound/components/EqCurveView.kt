package com.example.violintuner.feature.sound.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.audio.fx.EqCurve
import com.example.violintuner.core.domain.sound.EqBand
import com.example.violintuner.core.domain.sound.EqSettings
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.sound.SoundFormats
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

private val CurveHeight = 168.dp
private val VerticalMargin = 18.dp
private val PointRadius = 9.dp
private val TouchedRadius = 12.dp
private val DraggedRadius = 14.dp
private val GrabZone = 44.dp
private val BubbleLift = 56.dp
private const val RANGE_DB = 12.0
private const val POINTS = 160
private const val DISPLAY_RATE = 48_000
private const val POINT_GROW_MS = 100
private val GRID_HZ = doubleArrayOf(100.0, 1_000.0, 10_000.0)

/** Where a frequency and a gain fall in a box of the curve — and back. Pure, shared by the drawing and the dragging. */
internal object EqCurveGeometry {
    private val LOG_SPAN = ln(EqCurve.MAX_HZ / EqCurve.MIN_HZ)

    fun xOf(hz: Double, width: Float): Float = (ln(hz.coerceIn(EqCurve.MIN_HZ, EqCurve.MAX_HZ) / EqCurve.MIN_HZ) / LOG_SPAN).toFloat() * width

    fun hzAt(x: Float, width: Float): Double = EqCurve.MIN_HZ * exp((x / width).coerceIn(0f, 1f) * LOG_SPAN)

    fun yOf(db: Double, height: Float, margin: Float): Float = margin + ((RANGE_DB - db.coerceIn(-RANGE_DB, RANGE_DB)) / (2 * RANGE_DB)).toFloat() * (height - 2 * margin)

    fun dbAt(y: Float, height: Float, margin: Float): Double = RANGE_DB - ((y - margin) / (height - 2 * margin)).coerceIn(0f, 1f) * 2 * RANGE_DB
}

/**
 * The curve of the equalizer (handoff 18d2) — the true response of the settings, reckoned by the
 * same formulas the sound is made with, redrawn as the numbers change. Its points can be taken
 * and dragged: frequency along, gain up and down; the low cut is a square on the zero line and
 * moves along only. A finger hides the point it holds, so a bubble above it says where it is.
 */
@Composable
fun EqCurveView(
    eq: EqSettings,
    points: List<Triple<EqBand, Double, Double>>,
    selected: EqBand,
    enabled: Boolean,
    config: SoundConfig,
    onDrag: (band: EqBand, hz: Double, gainDb: Double) -> Unit,
    onSelect: (EqBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val sound = ViolinTheme.soundColors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val names = stringArrayResource(R.array.sound_band_names)
    val description = stringResource(R.string.sound_eq_curve_description)
    val frequencies = remember { EqCurve.logFrequencies(POINTS) }
    // The curve of a switched-off equalizer is still drawn — dimmed — so that it can be set before it is heard.
    val response = remember(eq) { EqCurve.responseDb(eq.copy(enabled = true), DISPLAY_RATE, frequencies, config) }

    var held by remember { mutableStateOf<EqBand?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    val currentPoints by rememberUpdatedState(points)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val heldRadius by animateDpAsState(if (dragging) DraggedRadius else if (held != null) TouchedRadius else PointRadius, tween(POINT_GROW_MS), label = "eqPoint")
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = colors.onSurfaceVariant)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CurveHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(Unit) {
                val margin = VerticalMargin.toPx()
                val grab = GrabZone.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Only the nearest point answers, and only from within its zone.
                    val nearest = currentPoints.minByOrNull { (_, hz, db) ->
                        hypot(EqCurveGeometry.xOf(hz, size.width.toFloat()) - down.position.x, EqCurveGeometry.yOf(db, size.height.toFloat(), margin) - down.position.y)
                    } ?: return@awaitEachGesture
                    val distance = hypot(
                        EqCurveGeometry.xOf(nearest.second, size.width.toFloat()) - down.position.x,
                        EqCurveGeometry.yOf(nearest.third, size.height.toFloat(), margin) - down.position.y,
                    )
                    if (distance > grab) return@awaitEachGesture
                    down.consume()
                    held = nearest.first
                    finger = down.position
                    currentOnSelect(nearest.first)
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        travelled += (change.position - change.previousPosition).getDistance()
                        if (travelled > slop) dragging = true
                        if (dragging) {
                            change.consume()
                            finger = change.position
                            currentOnDrag(
                                nearest.first,
                                EqCurveGeometry.hzAt(change.position.x, size.width.toFloat()),
                                EqCurveGeometry.dbAt(change.position.y, size.height.toFloat(), margin),
                            )
                        }
                    }
                    held = null
                    dragging = false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val margin = VerticalMargin.toPx()
            val dim = if (enabled) 1f else 0.38f
            val zeroY = EqCurveGeometry.yOf(0.0, size.height, margin)
            GRID_HZ.forEach { hz ->
                val x = EqCurveGeometry.xOf(hz, size.width)
                drawLine(colors.outlineVariant, Offset(x, margin / 2), Offset(x, size.height - margin), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                val label = measurer.measure(SoundFormats.axis(hz), labelStyle)
                drawText(label, topLeft = Offset(x - label.size.width / 2f, size.height - label.size.height - 2.dp.toPx()))
            }
            drawLine(colors.outlineVariant, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())

            val line = Path()
            response.forEachIndexed { index, db ->
                val x = EqCurveGeometry.xOf(frequencies[index], size.width)
                val y = EqCurveGeometry.yOf(db, size.height, margin)
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, zeroY)
                lineTo(0f, zeroY)
                close()
            }
            drawPath(fill, sound.eqFill.copy(alpha = sound.eqFill.alpha * dim))
            drawPath(line, colors.primary.copy(alpha = dim), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            currentPoints.forEach { (band, hz, db) ->
                val centre = Offset(EqCurveGeometry.xOf(hz, size.width), EqCurveGeometry.yOf(db, size.height, margin))
                val radius = (if (band == held) heldRadius else PointRadius).toPx()
                val chosen = band == selected
                val body = if (chosen) colors.primary else colors.surfaceContainerHigh
                val ring = if (chosen) colors.onPrimary else colors.primary
                if (band == EqBand.LOW_CUT) {
                    // a square: it moves along the line only, and looks it
                    val side = Size(radius * 2 - 2.dp.toPx(), radius * 2 - 2.dp.toPx())
                    val topLeft = Offset(centre.x - side.width / 2, centre.y - side.height / 2)
                    drawRoundRect(body.copy(alpha = dim), topLeft, side, CornerRadius(3.dp.toPx()))
                    drawRoundRect(ring.copy(alpha = dim), topLeft, side, CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
                } else {
                    drawCircle(body.copy(alpha = dim), radius - 1.dp.toPx(), centre)
                    drawCircle(ring.copy(alpha = dim), radius - 1.dp.toPx(), centre, style = Stroke(2.dp.toPx()))
                }
            }
        }
        held?.let { band ->
            val point = currentPoints.firstOrNull { it.first == band } ?: return@let
            val text = if (band == EqBand.LOW_CUT) {
                "${names[band.ordinal]} · ${SoundFormats.hertz(point.second)}"
            } else {
                "${SoundFormats.hertz(point.second)} · ${SoundFormats.decibels(point.third, signed = true)}"
            }
            Box(
                modifier = Modifier
                    .offset {
                        val halfWidth = with(density) { 64.dp.roundToPx() }
                        IntOffset((finger.x.roundToInt() - halfWidth).coerceAtLeast(0), (finger.y - with(density) { BubbleLift.toPx() }).roundToInt())
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text, color = colors.onSurface, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
    }
}
