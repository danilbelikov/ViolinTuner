package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.ui.format.CentsFormat
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.status_flat
import com.violinjourney.app.shared.resources.status_in_tune
import com.violinjourney.app.shared.resources.status_sharp
import org.jetbrains.compose.resources.stringResource

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
 * the ring does not jump. [color] is read while drawing: the fade between zones does not compose the row.
 *
 * [step] — its sizes ([StatusFit]): full, or compact with a small ring upright; lying down, what the room of the column allows
 * ([rememberStatusStep]). [height] — its row, upright the row of the model; unspecified — as tall as its tallest line, whole: the
 * column lying down gives it only a room it stands in.
 */
@Composable
fun StatusRow(
    direction: Direction?,
    cents: Int,
    color: () -> Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.StatusRowHeight,
    step: StatusStep = StatusFit.FULL,
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
    val (wordStyle, centsStyle) = statusStyles(step)
    val arrowSize = step.arrowDp.dp.let { if (height.isSpecified) minOf(height, it) else it }
    val dotSize = step.dotDp.dp

    // the fade and the pop of the sign are read in the layer: they do not compose the row on every frame
    val alpha = animateFloatAsState(
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
            .then(if (height.isSpecified) Modifier.height(height) else Modifier)
            .graphicsLayer {
                // what Modifier.alpha does, read here
                this.alpha = alpha.value
                clip = alpha.value != 1f
            },
        horizontalArrangement = Arrangement.spacedBy(step.gapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        }
        when (shown) {
            // the dot as a background of that colour would draw it: the circle's outline filled
            null -> Box(
                iconModifier
                    .size(dotSize)
                    .drawBehind { drawOutline(CircleShape.createOutline(size, layoutDirection, this), color()) },
            )
            Direction.SHARP -> Arrow(color, pointsDown = false, size = arrowSize, iconModifier)
            Direction.FLAT -> Arrow(color, pointsDown = true, size = arrowSize, iconModifier)
        }
        BasicText(
            text = stringResource(
                when (shown) {
                    null -> Res.string.status_in_tune
                    Direction.SHARP -> Res.string.status_sharp
                    Direction.FLAT -> Res.string.status_flat
                },
            ),
            style = wordStyle,
            maxLines = 1,
            color = color,
        )
        // Room for a sign and two digits is always taken: "+3" and "−27" start at the same
        // place and the word does not shift as the number changes.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val reserved = remember(centsStyle, density) {
            with(density) { measurer.measure(WIDEST_CENTS, centsStyle, maxLines = 1).size.width.toDp() }
        }
        Text(
            text = CentsFormat.signed((if (visible) cents else lastCents).toDouble()),
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

/** The word and the cents at [step]: the styles of Live at its sizes — the same the row draws and [rememberStatusStep] measures. */
@Composable
private fun statusStyles(step: StatusStep): Pair<TextStyle, TextStyle> {
    val typography = LiveTheme.liveTypography
    return remember(typography, step) {
        typography.status.copy(fontSize = step.wordSp.sp) to typography.cents.copy(fontSize = step.centsSp.sp)
    }
}

/**
 * The step of the word and the cents that stands in a room [roomHeight] × [roomWidth] (spec 5.29 R6: the right column of landscape) —
 * [StatusFit.fit] over the words of the three zones and the widest cents, measured in the font and the scale of the screen; null —
 * not even the least stands, and the two give way. The lines are measured once for the screen; the room may change on every frame
 * of the strings unfolding, and only the choice is made again.
 */
@Composable
fun rememberStatusStep(roomHeight: Dp, roomWidth: Dp): StatusStep? {
    val typography = LiveTheme.liveTypography
    val words = listOf(stringResource(Res.string.status_in_tune), stringResource(Res.string.status_sharp), stringResource(Res.string.status_flat))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // per step: the height of its row and its width, in pixels — the words and the cents in whole lines, as a text lays them out
    val sizes = remember(typography, words, density, measurer) {
        StatusFit.steps.associateWith { step ->
            val word = typography.status.copy(fontSize = step.wordSp.sp)
            val cents = typography.cents.copy(fontSize = step.centsSp.sp)
            val wordLines = words.map { measurer.measure(it, word, maxLines = 1, softWrap = false).size }
            val centsLine = measurer.measure(WIDEST_CENTS, cents, maxLines = 1, softWrap = false).size
            val sign = with(density) { maxOf(step.arrowDp, step.dotDp).dp.toPx() }
            val gaps = with(density) { step.gapDp.dp.toPx() * 2 }
            val height = maxOf(wordLines.maxOf { it.height }.toFloat(), centsLine.height.toFloat(), sign)
            val width = sign + gaps + wordLines.maxOf { it.width } + centsLine.width
            height to width
        }
    }
    return with(density) {
        val room = roomHeight.toPx()
        val across = roomWidth.toPx()
        StatusFit.fit(room, across, heightOf = { sizes.getValue(it).first }, widthOf = { sizes.getValue(it).second })
    }
}

@Composable
private fun Arrow(color: () -> Color, pointsDown: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(scale = this.size.width / ARROW_VIEWPORT, pivot = Offset.Zero) {
            drawPath(if (pointsDown) ArrowDown else ArrowUp, color())
        }
    }
}
