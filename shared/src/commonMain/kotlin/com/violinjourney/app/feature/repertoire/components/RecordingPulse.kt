package com.violinjourney.app.feature.repertoire.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import com.violinjourney.app.feature.journey.art.awaitGridFrame
import kotlinx.coroutines.currentCoroutineContext

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The pulse of the red dot of a running take (spec 3.15), on the piece screen and on the stand: its alpha goes
 * from 1 to [minAlpha] and back over [periodMs], stepped on the postcards' grid, thirty times a second — a dot of
 * ten dp needs no more, and on iOS every frame waited for is a frame drawn. Read it where it is drawn
 * (`graphicsLayer { alpha = pulse.value }`): the words beside the dot do not recompose with it.
 */
@Composable
fun rememberRecordingPulse(periodMs: Int, minAlpha: Float): State<Float> {
    val alpha = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(periodMs, minAlpha) { runPulse(alpha, periodMs, minAlpha) }
    return alpha
}

/**
 * The alpha [ms] after the pulse began: what `infiniteRepeatable(tween(periodMs / 2), RepeatMode.Reverse)` from 1 to
 * [minAlpha] gave — the default easing of a tween one way, the same backwards.
 */
internal fun pulseAt(ms: Long, periodMs: Int, minAlpha: Float): Float {
    val half = (periodMs / 2).toLong()
    val lap = ms.mod(2 * half)
    val fraction = (if (lap <= half) lap else 2 * half - lap).toFloat() / half
    return 1f + (minAlpha - 1f) * FastOutSlowInEasing.transform(fraction)
}

/**
 * The clock of [rememberRecordingPulse]: a step on every cell of the postcards' grid ([awaitGridFrame]), written
 * inside the frame, at the animator's pace as the effect starts. With animations off (a duration scale of 0) the dot
 * stands at [minAlpha] and asks for no frames, as the infinite transition stood at its target.
 */
internal suspend fun runPulse(alpha: MutableFloatState, periodMs: Int, minAlpha: Float) {
    val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
    if (scale == 0f) {
        alpha.floatValue = minAlpha
        return
    }
    var start = -1L
    var shown = 0L
    while (true) {
        shown = awaitGridFrame(shown) { now ->
            if (start < 0) start = now
            alpha.floatValue = pulseAt(((now - start) / scale / NANOS_PER_MILLI).toLong(), periodMs, minAlpha)
        }
    }
}
