package com.example.violintuner.feature.repertoire.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DashLength = 6.dp
private val DashGap = 5.dp
private val DashStroke = 1.5.dp

/** The outline of something that is not there yet and can be added: a page, a note (handoff 13c2). */
fun Modifier.dashedBorder(color: Color, corner: Dp): Modifier = drawBehind {
    val stroke = DashStroke.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGap.toPx()))),
    )
}
