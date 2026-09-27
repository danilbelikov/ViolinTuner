package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.feature.journey.art.awaitGridFrame
import kotlinx.coroutines.flow.first

private val Container = 20.dp
private val Dot = 8.dp
private val RingStroke = 1.5.dp
private const val DEGREES = 360f
private const val TOP = -90f
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The accent dot beside «Занятие идёт» (spec 3.16): it breathes, and a quarter arc around it goes
 * round once a minute — seconds without digits. The arc is led by [elapsedMs] of the timer, not
 * by a free cycle: after a return to the screen it stands where it should. Between the ticks of
 * the timer it is carried on by frames — the postcards' frames, thirty a second, like the breath
 * ([runDotClock]): both move by about a dp a second, so a step is under a twentieth of a dp.
 * Everything is read in the draw phase. Asleep while scrolled off the screen (spec 3.16): the arc
 * follows the timer, so it stands right when it comes back.
 */
@Composable
fun RunningDot(elapsedMs: Long, color: Color, ringColor: Color, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val tick by rememberUpdatedState(elapsedMs)
    val angle = remember { mutableFloatStateOf(angleOf(elapsedMs)) }
    val breath = remember { mutableFloatStateOf(0f) }
    // true until the layout says otherwise: the first frames come before the first word of it
    val seen = remember { mutableStateOf(true) }
    if (!still) {
        LaunchedEffect(Unit) { runDotClock(tick = { tick }, angle = angle, breath = breath, seen = { seen.value }) }
    }
    Box(
        modifier = modifier
            .size(Container)
            .onLayoutRectChanged { seen.value = it.fractionVisibleInWindow() > 0f }
            .drawBehind {
                val phase = if (still) 1f else breath.floatValue
                val centre = Offset(size.width / 2, size.height / 2)
                if (!still) {
                    val inset = RingStroke.toPx() / 2
                    drawArc(
                        color = ringColor,
                        startAngle = TOP + angle.floatValue,
                        sweepAngle = PracticeMotion.RING_ARC_DEGREES,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(RingStroke.toPx(), cap = StrokeCap.Round),
                    )
                }
                val scale = 1f + (PracticeMotion.DOT_SCALE_TO - 1f) * phase
                val alpha = PracticeMotion.DOT_ALPHA_FROM + (1f - PracticeMotion.DOT_ALPHA_FROM) * phase
                drawCircle(color.copy(alpha = if (still) 1f else alpha), radius = Dot.toPx() / 2 * if (still) 1f else scale, center = centre)
            },
    )
}

/**
 * The clock of the dot: on every cell of the postcards' grid ([awaitGridFrame]) the arc is carried on
 * from the last [tick] of the timer by the time since the frame that saw it, and the breath is read
 * from the time since the first frame. Asleep between the cells, and asleep altogether while the dot
 * is not [seen]: a timer scrolled away under the calendar asks for no frames.
 */
internal suspend fun runDotClock(tick: () -> Long, angle: MutableFloatState, breath: MutableFloatState, seen: () -> Boolean = { true }) {
    var lastTick = -1L
    var tickFrame = 0L
    var start = -1L
    var shown = 0L
    while (true) {
        snapshotFlow { seen() }.first { it }
        while (seen()) {
            shown = awaitGridFrame(shown) { now ->
                if (start < 0) start = now
                val elapsed = tick()
                if (elapsed != lastTick) {
                    lastTick = elapsed
                    tickFrame = now
                }
                angle.floatValue = angleOf(lastTick + (now - tickFrame) / NANOS_PER_MILLI)
                breath.floatValue = breathAt((now - start) / NANOS_PER_MILLI)
            }
        }
    }
}

/**
 * The breath of the dot [ms] after it began: from 0 to 1 and back, each way over
 * [PracticeMotion.DOT_HALF_BREATH_MS] with [PracticeMotion.Breath] — what
 * `infiniteRepeatable(tween(DOT_HALF_BREATH_MS, Breath), RepeatMode.Reverse)` from 0 to 1 gave.
 */
internal fun breathAt(ms: Long): Float {
    val half = PracticeMotion.DOT_HALF_BREATH_MS.toLong()
    val lap = ms.mod(2 * half)
    return PracticeMotion.Breath.transform((if (lap <= half) lap else 2 * half - lap).toFloat() / half)
}

private fun angleOf(elapsedMs: Long): Float = (elapsedMs % PracticeMotion.RING_TURN_MS) * DEGREES / PracticeMotion.RING_TURN_MS
