package com.example.violintuner.feature.live.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.ui.theme.ViolinTheme

private const val START_ANGLE_12_OCLOCK = -90f
private const val FULL_CIRCLE = 360f
private const val SHARP_SIGN = "#"

/**
 * Hold ring (spec 3.3): a muted outline that fills clockwise from 12 o'clock with [fillColor]
 * as [progress] goes 0..1. [content] is centered inside.
 */
@Composable
fun HoldRing(
    progress: Float,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = LiveDimens.RingSize,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = LiveDimens.RingStroke.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE_12_OCLOCK,
                sweepAngle = FULL_CIRCLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = START_ANGLE_12_OCLOCK,
                    sweepAngle = FULL_CIRCLE * progress.coerceAtMost(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Huge letter with accidental, octave smaller and muted at the baseline (spec 3.1). */
@Composable
fun NoteLabel(note: Note, modifier: Modifier = Modifier) {
    val typography = ViolinTheme.liveTypography
    Row(modifier = modifier) {
        Text(
            text = if (note.isSharp) "${note.letter}$SHARP_SIGN" else note.letter.toString(),
            modifier = Modifier.alignByBaseline(),
            color = MaterialTheme.colorScheme.onSurface,
            style = typography.note,
            maxLines = 1,
        )
        Text(
            text = note.octave.toString(),
            modifier = Modifier
                .alignByBaseline()
                .padding(start = LiveDimens.OctaveStartPadding),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = typography.octave,
            maxLines = 1,
        )
    }
}
