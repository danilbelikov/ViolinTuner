package com.example.violintuner.feature.practice.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.ui.motion.LocalReduceMotion

private val Container = 20.dp
private val Dot = 8.dp
private val RingStroke = 1.5.dp
private const val DEGREES = 360f
private const val TOP = -90f

/**
 * The accent dot beside «Занятие идёт» (spec 3.16): it breathes, and a quarter arc around it goes
 * round once a minute — seconds without digits. The arc is led by [elapsedMs] of the timer, not
 * by a free cycle: after a return to the screen it stands where it should. Between the ticks of
 * the timer it is carried on by frames. Everything is read in the draw phase.
 */
@Composable
fun RunningDot(elapsedMs: Long, color: Color, ringColor: Color, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val breath = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "runningDot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = InfiniteRepeatableSpec(tween(PracticeMotion.DOT_HALF_BREATH_MS, easing = PracticeMotion.Breath), RepeatMode.Reverse),
            label = "breath",
        )
    }
    val tick by rememberUpdatedState(elapsedMs)
    val angle = remember { mutableFloatStateOf(angleOf(elapsedMs)) }
    if (!still) {
        LaunchedEffect(Unit) {
            var lastTick = -1L
            var tickFrame = 0L
            while (true) {
                withFrameMillis { now ->
                    if (tick != lastTick) {
                        lastTick = tick
                        tickFrame = now
                    }
                    angle.floatValue = angleOf(lastTick + (now - tickFrame))
                }
            }
        }
    }
    Box(
        modifier = modifier
            .size(Container)
            .drawBehind {
                val phase = breath?.value ?: 1f
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

private fun angleOf(elapsedMs: Long): Float = (elapsedMs % PracticeMotion.RING_TURN_MS) * DEGREES / PracticeMotion.RING_TURN_MS
