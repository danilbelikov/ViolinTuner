package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.example.violintuner.core.ui.theme.ViolinTheme

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
    // place; while sounding it moves on a spring. It stays put while fading out.
    val position = remember { Animatable(markerFraction ?: CENTER) }
    var wasVisible by remember { mutableStateOf(markerFraction != null) }
    LaunchedEffect(markerFraction) {
        val target = markerFraction
        if (target != null) {
            if (wasVisible) {
                position.animateTo(target, spring(LiveMotion.MARKER_DAMPING, LiveMotion.MARKER_STIFFNESS))
            } else {
                position.snapTo(target)
            }
        }
        wasVisible = target != null
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
            val animatedMarker = position.value
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
