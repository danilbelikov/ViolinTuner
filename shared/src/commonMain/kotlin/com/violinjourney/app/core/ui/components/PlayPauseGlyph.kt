package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ▶ / ❚❚ filled, for a player's accent button. Not from the icon set on purpose: its play and
 * pause are outlines, and an outline on a filled button is too faint to be the main control.
 */
@Composable
fun PlayPauseGlyph(playing: Boolean, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val unit = this.size.width / 20f
        if (playing) {
            // two bars 5 x 18, 4 apart
            val bar = Size(5 * unit, 18 * unit)
            val radius = CornerRadius(2 * unit)
            drawRoundRect(tint, Offset(3 * unit, unit), bar, radius)
            drawRoundRect(tint, Offset(12 * unit, unit), bar, radius)
        } else {
            // handoff path: M5 3 L17 10 L5 17 Z
            val triangle = Path().apply {
                moveTo(5 * unit, 3 * unit)
                lineTo(17 * unit, 10 * unit)
                lineTo(5 * unit, 17 * unit)
                close()
            }
            drawPath(triangle, tint)
        }
    }
}
