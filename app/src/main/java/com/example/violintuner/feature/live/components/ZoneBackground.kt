package com.example.violintuner.feature.live.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import com.example.violintuner.core.ui.theme.ZoneGradient

// Handoff: radial-gradient(ellipse 70% 60%) over a 412 × 560 dp layer, i.e. the horizontal
// radius is 0.7 of the screen width and the ellipse is 336 / 288 taller than wide.
private const val RADIUS_X_FRACTION = 0.7f
private const val ELLIPSE_ASPECT = 336f / 288f

/**
 * Radial zone gradient centered on the ring (spec 3.2). Colors cross-fade between zones;
 * [center] is read in the draw phase, so ring layout changes do not recompose anything.
 */
fun Modifier.zoneBackground(
    gradient: ZoneGradient,
    crossfadeMs: Int,
    center: () -> Offset,
): Modifier = composed {
    val start by animateColorAsState(gradient.start, tween(crossfadeMs), label = "zoneGradientStart")
    val mid by animateColorAsState(gradient.mid, tween(crossfadeMs), label = "zoneGradientMid")
    drawBehind {
        val ringCenter = center().takeIf { it.isSpecified } ?: return@drawBehind
        val radius = size.width * RADIUS_X_FRACTION
        val brush = Brush.radialGradient(
            colorStops = ZoneGradient(start, mid, gradient.end).colorStops,
            center = ringCenter,
            radius = radius,
        )
        scale(scaleX = 1f, scaleY = ELLIPSE_ASPECT, pivot = ringCenter) {
            drawCircle(brush = brush, radius = radius, center = ringCenter)
        }
    }
}
