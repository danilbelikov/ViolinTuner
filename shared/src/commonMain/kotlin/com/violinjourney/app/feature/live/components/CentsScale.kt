package com.violinjourney.app.feature.live.components

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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.MarkerSpring
import com.violinjourney.app.feature.live.MarkerTrack
import kotlinx.coroutines.flow.first

/**
 * Cents scale of «Настройка» (spec 3.1, 3.36.6): a light thin line across, the green pill of the tolerance in the middle, the tick
 * of zero, and the bone slider with a halo of the zone color. No labels or numbers: the slider stops at ±50, the cents go to ±99
 * (spec 5.8). The marker shows while [markerVisible]; [markerFraction] is where, 0..1 along the track, or null before the pitch is
 * known — it moves with every frame of sound, so it is read only in the loop of the spring, and the halo colour only while drawing.
 * [inTuneFraction] is the pill width as a track fraction; [height] — 36, 28 in a low landscape window.
 */
@Composable
fun CentsScale(
    markerVisible: Boolean,
    markerFraction: () -> Float?,
    inTuneFraction: Float,
    haloColor: () -> Color,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.ScaleHeight,
) {
    val bone = LiveTheme.venueColors.bone
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = LiveDimens.SCALE_LINE_ALPHA)
    val pillColor = LiveTheme.zoneColors.inTune.copy(alpha = LiveDimens.SCALE_PILL_ALPHA)
    // After silence the marker shows up where the pitch is instead of travelling from its old
    // place; while sounding it rides a spring. It stays put while fading out. The spring is
    // stepped frame by frame ([MarkerSpring] says why) and read in the draw phase.
    val visible by rememberUpdatedState(markerVisible)
    val fraction by rememberUpdatedState(markerFraction)
    val target = { if (visible) fraction() else null }
    val spring = remember {
        MarkerSpring(LiveMotion.MARKER_DAMPING, LiveMotion.MARKER_STIFFNESS, Snapshot.withoutReadObservation { target() } ?: CENTER)
    }
    val position = remember { mutableFloatStateOf(spring.position) }
    val track = remember { MarkerTrack(spring, visible = Snapshot.withoutReadObservation { target() } != null) }
    LaunchedEffect(Unit) {
        while (true) {
            // Asleep while there is nothing to do: no frames are spent on a marker at rest. The silences are seen
            // here too, so the next note makes the marker jump even when the spring was asleep before it.
            snapshotFlow { target() }.first { track.follow(it) }
            position.floatValue = spring.position
            var last = withFrameMillis { it }
            while (true) {
                val current = target()
                if (!track.follow(current)) break
                val now = withFrameMillis { it }
                spring.advance(current!!, (now - last).toFloat())
                last = now
                position.floatValue = spring.position
            }
            position.floatValue = spring.position
        }
    }
    val markerAlpha by animateFloatAsState(
        targetValue = if (markerVisible) 1f else 0f,
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "markerAlpha",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // the line across, the green pill of the tolerance on it and the zero over the pill; the colours of the zones stay the
        // readings' own — only the pill of the tolerance is one (spec 3.1)
        centeredBar(line, CENTER, size.width, LiveDimens.ScaleLine)
        centeredBar(pillColor, CENTER, size.width * inTuneFraction, LiveDimens.ScalePillHeight)
        centeredBar(line, CENTER, LiveDimens.ScaleTickWidth.toPx(), LiveDimens.ScaleTickHeight, rounded = false)
        if (markerAlpha > 0f) {
            val animatedMarker = position.floatValue
            val feather = LiveDimens.HaloFeather
            val haloColor = haloColor()
            centeredBar(
                haloColor.copy(alpha = LiveDimens.HALO_ALPHA_FEATHER * markerAlpha), animatedMarker,
                (LiveDimens.HaloWidth + feather * 2).toPx(), LiveDimens.HaloHeight + feather * 2,
            )
            centeredBar(
                haloColor.copy(alpha = LiveDimens.HALO_ALPHA_CORE * markerAlpha), animatedMarker,
                LiveDimens.HaloWidth.toPx(), LiveDimens.HaloHeight,
            )
            // the bone slider
            centeredBar(
                bone.copy(alpha = markerAlpha), animatedMarker,
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
