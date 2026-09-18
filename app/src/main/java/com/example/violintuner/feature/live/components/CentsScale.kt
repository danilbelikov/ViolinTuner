package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.MarkerSpring
import kotlinx.coroutines.flow.first

/**
 * Cents scale (spec 3.1): thin track, green in-tune pill in the middle, zero tick and a white
 * marker with a halo of the zone color. No labels or numbers. [markerFraction] is 0..1 along
 * the track, null hides the marker; [inTuneFraction] is the pill width as a track fraction.
 */
@Composable
fun CentsScale(
    markerFraction: Float?,
    inTuneFraction: Float,
    haloColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pillColor = ViolinTheme.zoneColors.inTune
    // After silence the marker shows up where the pitch is instead of travelling from its old
    // place; while sounding it rides a spring. It stays put while fading out. The spring is
    // stepped frame by frame ([MarkerSpring] says why) and read in the draw phase.
    val target by rememberUpdatedState(markerFraction)
    val spring = remember { MarkerSpring(LiveMotion.MARKER_DAMPING, LiveMotion.MARKER_STIFFNESS, markerFraction ?: CENTER) }
    val position = remember { mutableFloatStateOf(spring.position) }
    LaunchedEffect(Unit) {
        var wasVisible = target != null
        while (true) {
            // Asleep while there is nothing to do: no frames are spent on a marker at rest.
            val goal = snapshotFlow { target }.first { it != null && (!wasVisible || !spring.isAtRest(it)) }!!
            if (!wasVisible) spring.snapTo(goal)
            wasVisible = true
            var last = withFrameMillis { it }
            while (true) {
                val current = target
                if (current == null) {
                    wasVisible = false
                    break
                }
                if (spring.isAtRest(current)) break
                val now = withFrameMillis { it }
                spring.advance(current, (now - last).toFloat())
                last = now
                position.floatValue = spring.position
            }
            position.floatValue = spring.position
        }
    }
    val markerAlpha by animateFloatAsState(
        targetValue = if (markerFraction != null) 1f else 0f,
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "markerAlpha",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDimens.ScaleHeight),
    ) {
        centeredBar(colors.surfaceContainerHigh, CENTER, size.width, LiveDimens.ScaleTrackHeight)
        centeredBar(pillColor, CENTER, size.width * inTuneFraction, LiveDimens.ScalePillHeight)
        centeredBar(
            colors.outlineVariant, CENTER, LiveDimens.ScaleTickWidth.toPx(), LiveDimens.ScaleTickHeight,
            rounded = false,
        )
        if (markerAlpha > 0f) {
            val animatedMarker = position.floatValue
            val feather = LiveDimens.HaloFeather
            centeredBar(
                haloColor.copy(alpha = LiveDimens.HALO_ALPHA_FEATHER * markerAlpha), animatedMarker,
                (LiveDimens.HaloWidth + feather * 2).toPx(), LiveDimens.HaloHeight + feather * 2,
            )
            centeredBar(
                haloColor.copy(alpha = LiveDimens.HALO_ALPHA_CORE * markerAlpha), animatedMarker,
                LiveDimens.HaloWidth.toPx(), LiveDimens.HaloHeight,
            )
            val outline = LiveDimens.MarkerOutline
            centeredBar(
                colors.surface.copy(alpha = markerAlpha), animatedMarker,
                (LiveDimens.MarkerWidth + outline * 2).toPx(), LiveDimens.MarkerHeight + outline * 2,
            )
            centeredBar(
                colors.onSurface.copy(alpha = markerAlpha), animatedMarker,
                LiveDimens.MarkerWidth.toPx(), LiveDimens.MarkerHeight,
            )
        }
    }
}

private const val CENTER = 0.5f

/** A pill centered vertically on the scale and horizontally at [fraction] of its width. */
private fun DrawScope.centeredBar(
    color: Color,
    fraction: Float,
    widthPx: Float,
    height: Dp,
    rounded: Boolean = true,
) {
    val heightPx = height.toPx()
    val radius = if (rounded) minOf(widthPx, heightPx) / 2 else 0f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * fraction - widthPx / 2, (size.height - heightPx) / 2),
        size = Size(widthPx, heightPx),
        cornerRadius = CornerRadius(radius, radius),
    )
}
