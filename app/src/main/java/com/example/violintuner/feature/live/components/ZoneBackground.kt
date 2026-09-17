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

/** Shape of the gradient ellipse: horizontal radius as a fraction of the screen width. */
enum class ZoneEllipse(val radiusXFraction: Float, val aspect: Float) {
    /** Handoff portrait: ellipse 70 % × 60 % of a 412 × 560 dp layer. */
    PORTRAIT(radiusXFraction = 0.7f, aspect = 336f / 288f),

    /** Handoff landscape: ellipse 60 % × 70 % of the 440 × 412 dp left panel of an 892 dp screen. */
    LANDSCAPE(radiusXFraction = 0.6f * 440f / 892f, aspect = 288f / 264f),
}

/**
 * Radial zone gradient centered on the ring (spec 3.2). Colors cross-fade between zones;
 * [center] is read in the draw phase, so ring layout changes do not recompose anything.
 */
fun Modifier.zoneBackground(
    gradient: ZoneGradient,
    crossfadeMs: Int,
    ellipse: ZoneEllipse,
    center: () -> Offset,
): Modifier = composed {
    val start by animateColorAsState(gradient.start, tween(crossfadeMs), label = "zoneGradientStart")
    val mid by animateColorAsState(gradient.mid, tween(crossfadeMs), label = "zoneGradientMid")
    drawBehind {
        val ringCenter = center().takeIf { it.isSpecified } ?: return@drawBehind
        val radius = size.width * ellipse.radiusXFraction
        val brush = Brush.radialGradient(
            colorStops = ZoneGradient(start, mid, gradient.end).colorStops,
            center = ringCenter,
            radius = radius,
        )
        scale(scaleX = 1f, scaleY = ellipse.aspect, pivot = ringCenter) {
            drawCircle(brush = brush, radius = radius, center = ringCenter)
        }
    }
}
