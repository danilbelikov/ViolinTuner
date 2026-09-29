package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The dashed frame of the next trophy (spec 5.29 R2, R3): 1.5 at a corner of 18, dashes of 4 with gaps of 3.
private val DashWidth = 1.5.dp
private val DashOn = 4.dp
private val DashOff = 3.dp
internal val DashedFrameCorner = 18.dp

/**
 * The frame of the trophy that comes next — the tile in «Мой путь» and the row in «Трофеи» (spec 3.36.2, 3.36.3): the one highlight of
 * its list, drawn inside the bounds in [color].
 */
internal fun Modifier.dashedFrame(color: Color, corner: Dp = DashedFrameCorner): Modifier = drawBehind {
    val stroke = DashWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx() - stroke / 2),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
    )
}
