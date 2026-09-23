package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlin.math.min
import kotlinx.coroutines.channels.Channel

/** Sizes of the flame (handoff `sizes`): smaller for a young streak, and wherever the card is small. */
object StreakFlameSize {
    val Full = 24.dp
    val Small = 20.dp
}

private class Wake(val flare: Boolean, val appearing: Boolean)

/**
 * The flame of «Дней подряд» (spec 3.18, handoff 19g): a candle, not a bonfire. It sways for a
 * few seconds after it flares — a streak that grew before the eyes — and then comes to rest; the
 * opening of the screen belongs to the living «Начать занятие» (0.66). While a practice runs it
 * stands still, the timer is what lives there. All three movements at once read as a shop window
 * (handoff 19h), so there are never three: [onSway] tells the start button to rest while it sways.
 *
 * [scope] is what makes numbers "other numbers, not these ones grown" — the same as for
 * `rolledValue`: loading and another month do not flare. Draws nothing below three days, but
 * stays in the composition to see the streak reach them. Everything is read in the draw phase;
 * at rest no frames are spent.
 */
@Composable
fun StreakFlame(
    streakDays: Int,
    running: Boolean,
    scope: Any?,
    modifier: Modifier = Modifier,
    maxSize: Dp = StreakFlameSize.Full,
    onSway: (Boolean) -> Unit = {},
) {
    val still = LocalReduceMotion.current
    val stage = FlameMath.stageOf(streakDays)
    val colors = ViolinTheme.practiceColors
    val outer = remember { FlameArt.Outer.d.toPath() }
    val core = remember { FlameArt.Core.d.toPath() }

    val pose = remember { mutableStateOf(FlameMath.Pose.REST) }
    val flare = remember { mutableFloatStateOf(1f) }
    val alpha = remember { mutableFloatStateOf(1f) }
    val wakes = remember { Channel<Wake>(Channel.CONFLATED) }
    val runningNow by rememberUpdatedState(running)
    val swaying by rememberUpdatedState(onSway)

    val known = remember(scope) { mutableIntStateOf(streakDays) }
    // A flame that has just been earned must not flash at full strength for the frame it takes the
    // effect below to start its appearance: it is hidden here, before it is first drawn. The value
    // is read only in the draw phase, so writing it while composing recomposes nothing.
    if (!still && streakDays > known.intValue && stage != FlameMath.Stage.NONE && FlameMath.stageOf(known.intValue) == FlameMath.Stage.NONE) {
        alpha.floatValue = 0f
    }
    LaunchedEffect(streakDays, scope) {
        if (streakDays > known.intValue && FlameMath.stageOf(streakDays) != FlameMath.Stage.NONE) {
            wakes.trySend(Wake(flare = true, appearing = FlameMath.stageOf(known.intValue) == FlameMath.Stage.NONE))
        }
        known.intValue = streakDays
    }

    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var next: Wake? = Wake(flare = false, appearing = false).takeIf { PracticeMotion.FLAME_SWAY_ON_OPEN && !runningNow }
        try {
            while (true) {
                val wake = next ?: wakes.receive()
                next = null
                swaying(true)
                val appearMs = if (wake.appearing) PracticeMotion.FLAME_APPEAR_MS else 0L
                val flareMs = if (wake.flare) PracticeMotion.FLAME_FLARE_MS.toLong() else 0L
                var start = -1L
                var cutAt = -1L
                var done = false
                while (!done) {
                    withFrameMillis { now ->
                        if (start < 0) start = now
                        val t = now - start
                        alpha.floatValue = if (wake.appearing) FlameMath.appearAlphaAt(t) else 1f
                        flare.floatValue = FlameMath.flareScaleAt(t - appearMs)
                        val sway = t - appearMs - flareMs
                        if (sway >= 0) {
                            // A practice that started meanwhile takes the floor: the sway dies down at once, just as softly.
                            if (runningNow && cutAt < 0) cutAt = sway
                            val amplitude = if (cutAt < 0) {
                                FlameMath.amplitudeAt(sway)
                            } else {
                                min(FlameMath.amplitudeAt(sway), FlameMath.amplitudeAt(PracticeMotion.FLAME_ALIVE_MS + (sway - cutAt)))
                            }
                            pose.value = FlameMath.poseAt(FlameMath.phaseAt(sway), amplitude)
                            if (amplitude <= 0f) done = true
                        }
                    }
                    wakes.tryReceive().getOrNull()?.let {
                        next = it
                        done = true
                    }
                }
                pose.value = FlameMath.Pose.REST
                flare.floatValue = 1f
                alpha.floatValue = 1f
                swaying(false)
            }
        } finally {
            swaying(false)
        }
    }

    val size = when (stage) {
        FlameMath.Stage.NONE -> 0.dp
        FlameMath.Stage.SMALL -> StreakFlameSize.Small
        else -> maxSize
    }
    if (size == 0.dp) return
    val coreColor = if (stage == FlameMath.Stage.HOT) colors.flameHot else colors.flameCore
    Box(
        modifier
            .size(min(size.value, maxSize.value).dp)
            .drawBehind {
                val unit = this.size.minDimension / FlameArt.GRID
                val now = if (still) FlameMath.Pose.REST else pose.value
                val shown = if (still) 1f else alpha.floatValue
                val grown = if (still) 1f else flare.floatValue
                withTransform({
                    scale(unit, unit, pivot = Offset.Zero)
                    scale(grown, grown, pivot = Offset(FlameArt.Outer.pivotX, FlameArt.Outer.pivotY))
                }) {
                    layer(outer, FlameArt.Outer, colors.flameOuter, shown, now.tongueDegrees, now.tongueScaleY)
                    layer(core, FlameArt.Core, coreColor, shown, 0f, now.coreScaleY)
                }
            },
    )
}

private fun DrawScope.layer(path: Path, art: FlameArt.Layer, color: androidx.compose.ui.graphics.Color, alpha: Float, degrees: Float, scaleY: Float) {
    val pivot = Offset(art.pivotX, art.pivotY)
    withTransform({
        rotate(degrees, pivot)
        scale(1f, scaleY, pivot)
    }) { drawPath(path, color, alpha = alpha) }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()
