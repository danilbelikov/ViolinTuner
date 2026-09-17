package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
 * the color for color-blind players. [direction] null means in tune. Hidden with
 * [visible] = false: the row fades out showing what it showed last and keeps its height, so
 * the ring does not jump.
 */
@Composable
fun StatusRow(
    direction: Direction?,
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.StatusRowHeight,
    wordStyle: TextStyle = ViolinTheme.liveTypography.status,
) {
    var lastShown by remember { mutableStateOf(direction) }
    if (visible) SideEffect { lastShown = direction }
    val shown = if (visible) direction else lastShown

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "statusAlpha",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(shown) {
        pop.snapTo(LiveMotion.DIRECTION_POP_FROM)
        pop.animateTo(1f, tween(LiveMotion.DIRECTION_POP_MS))
    }

    Row(
        modifier = modifier
            .height(height)
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.StatusGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier.scale(pop.value)
        when (shown) {
            null -> Box(
                iconModifier
                    .size(LiveDimens.StatusDotSize)
                    .background(color, CircleShape),
            )
            Direction.SHARP -> Arrow(color, pointsDown = false, size = minOf(height, LiveDimens.StatusArrowSize), iconModifier)
            Direction.FLAT -> Arrow(color, pointsDown = true, size = minOf(height, LiveDimens.StatusArrowSize), iconModifier)
        }
        Text(
            text = stringResource(
                when (shown) {
                    null -> R.string.status_in_tune
                    Direction.SHARP -> R.string.status_sharp
                    Direction.FLAT -> R.string.status_flat
                },
            ),
            color = color,
            style = wordStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun Arrow(color: Color, pointsDown: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(scale = this.size.width / ARROW_VIEWPORT, pivot = Offset.Zero) {
            drawPath(if (pointsDown) ArrowDown else ArrowUp, color)
        }
    }
}
