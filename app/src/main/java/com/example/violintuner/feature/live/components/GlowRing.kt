package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.example.violintuner.feature.live.GlowMath
import com.example.violintuner.feature.live.WaveGate
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One wave on its way out: [progress] 0..1 of its life, [startAlpha] says which kind it is. */
private class Wave(val startAlpha: Float) {
    val progress = Animatable(0f)
}

/**
 * How strongly the ring glows (spec 5.8): it moves towards [glowTarget] by itself, faster up than
 * down, so that a note slipping out of the zone for a moment does not put it out — nothing on this
 * screen blinks. With [reduceMotion] the glow only changes in steps. Hoisted out of the ring: the
 * picture behind Live is lit by the same number (spec 3.27). What is already true when the screen
 * appears is shown as it is: no climb from zero after a rotation or a return to the tab.
 */
@Composable
fun rememberRingGlow(glowTarget: Float, reduceMotion: Boolean): State<Float> {
    val target by rememberUpdatedState(glowTarget)
    val glow = remember { mutableFloatStateOf(glowTarget) }
    LaunchedEffect(reduceMotion) {
        val riseMs = if (reduceMotion) LiveMotion.GLOW_STEP_MS else LiveMotion.GLOW_RISE_MS
        val fallMs = if (reduceMotion) LiveMotion.GLOW_STEP_MS else LiveMotion.GLOW_FALL_MS
        while (true) {
            if (abs(target - glow.floatValue) < GLOW_SETTLED) {
                glow.floatValue = target
                // Nothing to do until the target moves: no frames are spent on a ring at rest.
                snapshotFlow { target }.first { abs(it - glow.floatValue) >= GLOW_SETTLED }
            }
            var last = withFrameMillis { it }
            while (abs(target - glow.floatValue) >= GLOW_SETTLED) {
                val now = withFrameMillis { it }
                glow.floatValue = GlowMath.follow(glow.floatValue, target, (now - last).toFloat(), riseMs, fallMs)
                last = now
            }
        }
    }
    return glow
}

/**
 * The ring of Live (spec 3.14, handoff 12a, 12h): an outline that shines with the zone color, as
 * strongly as [glow] says ([rememberRingGlow]). The halo breathes with [level]; a wave leaves the
 * ring when [noteSerial] moves and when [holdComplete] turns true. With [reduceMotion] there are no
 * waves and no breath.
 *
 * Everything that changes many times a second is read in the draw phase: the ring redraws, it
 * does not recompose. The halo reaches past the bounds ([GlowMath.EXTENT] × the radius) and is
 * not clipped on purpose; the layout leaves it room.
 */
@Composable
fun GlowRing(
    glow: State<Float>,
    level: Float,
    zoneColor: Color,
    noteSerial: Int,
    holdComplete: Boolean,
    reduceMotion: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val loudness by rememberUpdatedState(if (reduceMotion) 0f else level)
    val color by rememberUpdatedState(zoneColor)
    val waves = remember { mutableStateListOf<Wave>() }
    val gate = remember { WaveGate(LiveMotion.WAVE_MIN_INTERVAL_MS) }

    suspend fun wave(startAlpha: Float) {
        if (reduceMotion || !gate.tryStart(withFrameMillis { it })) return
        val wave = Wave(startAlpha)
        waves += wave
        try {
            wave.progress.animateTo(1f, tween(LiveMotion.WAVE_MS, easing = LinearOutSlowInEasing))
        } finally {
            waves -= wave
        }
    }

    val firstSerial = remember { noteSerial }
    LaunchedEffect(noteSerial) {
        if (LiveMotion.NEW_NOTE_WAVE && noteSerial != firstSerial) launch { wave(GlowMath.WAVE_ALPHA_NEW_NOTE) }
    }
    LaunchedEffect(holdComplete) {
        if (holdComplete) launch { wave(GlowMath.WAVE_ALPHA_REWARD) }
    }

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawRing(glow.value, loudness, color)
                waves.forEach { drawWave(it.startAlpha, it.progress.value, color) }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val GLOW_SETTLED = 0.003f

private fun DrawScope.outlineRadius(): Float = (size.minDimension - LiveDimens.RingStroke.toPx()) / 2

private fun DrawScope.drawRing(glow: Float, level: Float, zoneColor: Color) {
    val radius = outlineRadius()
    val outline = LiveDimens.RingStroke.toPx()
    if (glow > 0f) {
        val haloRadius = radius * GlowMath.haloRadius(level)
        drawCircle(brush = radial(GlowMath.haloStops(glow, level), zoneColor, haloRadius), radius = haloRadius)
        drawCircle(brush = radial(GlowMath.innerStops(glow), zoneColor, radius), radius = radius)
        drawCircle(
            color = zoneColor,
            radius = radius,
            alpha = GlowMath.softStrokeAlpha(glow),
            style = Stroke(GlowMath.softStrokeWidthDp(LiveDimens.RingStroke.value, glow) * density),
        )
    }
    drawCircle(
        color = lerp(zoneColor, Color.White, GlowMath.outlineWhiteShare(glow)),
        radius = radius,
        alpha = GlowMath.outlineAlpha(glow),
        style = Stroke(outline),
    )
}

private fun DrawScope.drawWave(startAlpha: Float, progress: Float, zoneColor: Color) {
    drawCircle(
        color = zoneColor,
        radius = outlineRadius() * GlowMath.waveRadius(progress),
        alpha = GlowMath.waveAlpha(startAlpha, progress),
        style = Stroke(LiveDimens.WaveStroke.toPx()),
    )
}

private fun DrawScope.radial(stops: List<GlowMath.AlphaStop>, color: Color, radius: Float): Brush =
    Brush.radialGradient(
        colorStops = stops.map { it.position to color.copy(alpha = color.alpha * it.alpha) }.toTypedArray(),
        center = Offset(size.width / 2, size.height / 2),
        radius = radius,
    )
