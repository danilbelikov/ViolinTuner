package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The dashed frame of the next trophy (spec 5.29 R2, R3): 1.5 at a corner of 18, dashes of 4 with gaps of 3.
private val DashWidth = 1.5.dp
private val DashOn = 4.dp
private val DashOff = 3.dp
internal val DashedFrameCorner = 18.dp

/**
 * The frame of the trophy that comes next — the tile in «Мой путь» and the row in «Трофеи» (spec 3.36.2, 3.36.3): the one highlight of
 * its list, drawn inside the bounds in [color]. [width] — the line: 1.5, or 2 of the tile «Свой вид» of the form of an event (5.29 R9).
 */
internal fun Modifier.dashedFrame(color: Color, corner: Dp = DashedFrameCorner, width: Dp = DashWidth): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx() - stroke / 2),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
    )
}

/**
 * The same dashed frame along the outline of [shape] — a capsule that stays one at any height: the chips of what a copy left out,
 * «без видео» (spec 3.36.8, 5.29 R8). Drawn inside the bounds, as [dashedFrame] is.
 */
internal fun Modifier.dashedFrame(color: Color, shape: Shape): Modifier = drawBehind {
    val stroke = DashWidth.toPx()
    val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
    translate(stroke / 2, stroke / 2) {
        drawOutline(outline, color, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))))
    }
}
