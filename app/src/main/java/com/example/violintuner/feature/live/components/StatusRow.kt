package com.example.violintuner.feature.live.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.example.violintuner.R
import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.ui.theme.ViolinTheme

// Arrow outlines from the handoff SVGs, 56 × 56 viewport.
private const val ARROW_VIEWPORT = 56f
private val ArrowUp = Path().apply {
    moveTo(28f, 6f)
    lineTo(50f, 34f)
    lineTo(36f, 34f)
    lineTo(36f, 50f)
    lineTo(20f, 50f)
    lineTo(20f, 34f)
    lineTo(6f, 34f)
    close()
}
private val ArrowDown = Path().apply {
    moveTo(28f, 50f)
    lineTo(6f, 22f)
    lineTo(20f, 22f)
    lineTo(20f, 6f)
    lineTo(36f, 6f)
    lineTo(36f, 22f)
    lineTo(50f, 22f)
    close()
}

/**
 * Status line (spec 3.1): dot + "в строе", arrow up + "выше", arrow down + "ниже"; shape doubles
 * the color for color-blind players. [direction] null means in tune. Always takes its full
 * height so the ring does not jump when the status hides; pass [visible] = false for that.
 */
@Composable
fun StatusRow(
    direction: Direction?,
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(LiveDimens.StatusRowHeight),
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.StatusGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!visible) return@Row
        when (direction) {
            null -> Box(
                Modifier
                    .size(LiveDimens.StatusDotSize)
                    .background(color, CircleShape),
            )
            Direction.SHARP -> Arrow(color, pointsDown = false)
            Direction.FLAT -> Arrow(color, pointsDown = true)
        }
        Text(
            text = stringResource(
                when (direction) {
                    null -> R.string.status_in_tune
                    Direction.SHARP -> R.string.status_sharp
                    Direction.FLAT -> R.string.status_flat
                },
            ),
            color = color,
            style = ViolinTheme.liveTypography.status,
        )
    }
}

@Composable
private fun Arrow(color: Color, pointsDown: Boolean) {
    Canvas(Modifier.size(LiveDimens.StatusArrowSize)) {
        scale(scale = size.width / ARROW_VIEWPORT, pivot = Offset.Zero) {
            drawPath(if (pointsDown) ArrowDown else ArrowUp, color)
        }
    }
}
