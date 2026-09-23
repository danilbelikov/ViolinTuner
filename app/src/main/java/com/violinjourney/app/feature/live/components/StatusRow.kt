package com.violinjourney.app.feature.live.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme

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
 * Status row (spec 3.1, 3.14; handoff 12c1): dot + "в строе", arrow up + "выше", arrow down +
 * "ниже", and the cents to the right of the word. Shape doubles the color for color-blind
 * players; [direction] null means in tune. The word and its sign carry the zone color and are
 * what the corner of the eye reads; the cents are grey in every zone — for a direct look,
 * quieter than the word, and a "+3" in tune does not ask to be chased to zero. Hidden with
 * [visible] = false: the row fades out showing what it showed last and keeps its height, so
 * the ring does not jump.
 */
@Composable
fun StatusRow(
    direction: Direction?,
    cents: Int,
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.StatusRowHeight,
    compact: Boolean = false,
) {
    var lastShown by remember { mutableStateOf(direction) }
    var lastCents by remember { mutableIntStateOf(cents) }
    if (visible) {
        SideEffect {
            lastShown = direction
            lastCents = cents
        }
    }
    val shown = if (visible) direction else lastShown
    val typography = ViolinTheme.liveTypography
    val wordStyle = if (compact) typography.statusCompact else typography.status
    val centsStyle = if (compact) typography.centsCompact else typography.cents
    val arrowSize = if (compact) LiveDimens.StatusArrowSizeCompact else LiveDimens.StatusArrowSize
    val dotSize = if (compact) LiveDimens.StatusDotSizeCompact else LiveDimens.StatusDotSize

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
        horizontalArrangement = Arrangement.spacedBy(if (compact) LiveDimens.StatusGapCompact else LiveDimens.StatusGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier.scale(pop.value)
        when (shown) {
            null -> Box(
                iconModifier
                    .size(dotSize)
                    .background(color, CircleShape),
            )
            Direction.SHARP -> Arrow(color, pointsDown = false, size = minOf(height, arrowSize), iconModifier)
            Direction.FLAT -> Arrow(color, pointsDown = true, size = minOf(height, arrowSize), iconModifier)
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
        // Room for a sign and two digits is always taken: "+3" and "−27" start at the same
        // place and the word does not shift as the number changes.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val reserved = remember(centsStyle, density) {
            with(density) { measurer.measure(WIDEST_CENTS, centsStyle, maxLines = 1).size.width.toDp() }
        }
        Text(
            text = Formats.signedCents((if (visible) cents else lastCents).toDouble()),
            modifier = Modifier.widthIn(min = reserved),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = centsStyle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The widest the cents get: a real minus and two digits; digits are tabular, all as wide as a zero. */
private const val WIDEST_CENTS = "\u221200"

@Composable
private fun Arrow(color: Color, pointsDown: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(scale = this.size.width / ARROW_VIEWPORT, pivot = Offset.Zero) {
            drawPath(if (pointsDown) ArrowDown else ArrowUp, color)
        }
    }
}
