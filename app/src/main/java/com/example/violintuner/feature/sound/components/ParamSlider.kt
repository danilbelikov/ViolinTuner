package com.example.violintuner.feature.sound.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RowHeight = 48.dp
private val StepButton = 40.dp
private val TrackHeight = 4.dp
private val Thumb = 20.dp
private val DefaultMark = 12.dp
private val BubbleLift = 30.dp
private const val DISABLED_ALPHA = 0.38f
private const val THUMB_TRAVEL_MS = 150
private const val RESET_TRAVEL_MS = 200
private const val BUBBLE_LINGER_MS = 300L
private const val REPEAT_AFTER_MS = 400L
private const val REPEAT_EVERY_MS = 125L // eight steps a second
private const val TABULAR_FIGURES = "tnum"

/** What a slider shows and where it stands; all fractions are 0…1 along the track. */
data class SliderModel(
    val label: String,
    val hint: String?,
    /** The value as text, with its unit. */
    val valueText: String,
    val fraction: Float,
    val defaultFraction: Float,
    /** The fill grows from the middle — a gain of ±12 dB — rather than from the left. */
    val bipolar: Boolean,
    /** Marks under the track with a word each: «чуть», «заметно», «сильно». */
    val marks: List<Pair<Float, String>> = emptyList(),
    /** The knob has let go of its numbers («своё»): the thumb is dimmed, the track still takes a touch. */
    val detached: Boolean = false,
)

/**
 * The one control of a value on the «Звук» screen (handoff 18g): twenty parameters, one slider —
 * the eye stops seeing the control and reads the captions. The whole row takes the touch, the
 * value stands as a number with its unit and rides above the finger while dragged, − and +
 * step finely (held — they repeat), the default has a mark, a double tap returns to it.
 */
@Composable
fun ParamSlider(
    model: SliderModel,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    onStep: (up: Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(model.fraction) }
    var dragging by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf(false) }
    var bubbleJob by remember { mutableStateOf<Job?>(null) }
    var trackWidth by remember { mutableIntStateOf(0) }

    // Under the finger the thumb is the finger; otherwise it travels to wherever the value went (a tap on the track, a double tap, a preset).
    LaunchedEffect(model.fraction, dragging) {
        if (dragging) shown.snapTo(model.fraction) else shown.animateTo(model.fraction, tween(THUMB_TRAVEL_MS))
    }

    val currentOnFraction by rememberUpdatedState(onFraction)
    val currentOnReset by rememberUpdatedState(onReset)
    val currentOnStep by rememberUpdatedState(onStep)
    val resetHint = stringResource(R.string.sound_slider_reset_hint)
    val offText = stringResource(R.string.sound_slider_off)

    Column(modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.label, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            Text(
                text = model.hint.orEmpty(),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = model.valueText,
                color = if (model.detached) colors.onSurfaceVariant else colors.primary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton(up = false, label = stringResource(R.string.sound_slider_minus, model.label), enabled = enabled) { currentOnStep(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(RowHeight)
                    .onSizeChanged { trackWidth = it.width }
                    .semantics(mergeDescendants = true) {
                        contentDescription = model.label
                        stateDescription = if (enabled) "${model.valueText}. $resetHint" else offText
                        progressBarRangeInfo = ProgressBarRangeInfo(model.fraction, 0f..1f)
                        if (enabled) {
                            setProgress { target -> currentOnFraction(target.coerceIn(0f, 1f)); true }
                            onClick(label = resetHint) { currentOnReset(); true }
                        } else {
                            disabled()
                        }
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        val slop = viewConfiguration.touchSlop
                        val doubleTap = viewConfiguration.doubleTapTimeoutMillis
                        var lastTapAt = 0L
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val inset = Thumb.toPx() / 2
                            fun fractionAt(x: Float) = ((x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                            bubbleJob?.cancel()
                            bubble = true
                            var moved = 0f
                            var vertical = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                moved += abs(change.positionChange().x)
                                vertical += abs(change.positionChange().y)
                                if (!dragging && moved > slop && moved > vertical) dragging = true
                                // a finger going up or down is scrolling the page, not setting a value
                                if (!dragging && vertical > slop * 2) break
                                if (dragging) {
                                    change.consume()
                                    currentOnFraction(fractionAt(change.position.x))
                                }
                            }
                            if (!dragging && moved <= slop && vertical <= slop) {
                                val now = down.uptimeMillis
                                if (now - lastTapAt < doubleTap) {
                                    lastTapAt = 0
                                    currentOnReset()
                                } else {
                                    lastTapAt = now
                                    currentOnFraction(fractionAt(down.position.x)) // the thumb travels to the finger
                                }
                            }
                            dragging = false
                            bubbleJob = scope.launch {
                                delay(BUBBLE_LINGER_MS)
                                bubble = false
                            }
                        }
                    },
            ) {
                val track = colors.surfaceContainerHigh
                val fill = if (model.detached) colors.outlineVariant else colors.primary
                val mark = colors.outline
                Canvas(Modifier.fillMaxSize()) {
                    val inset = Thumb.toPx() / 2
                    val width = size.width - inset * 2
                    val centreY = size.height / 2
                    val height = TrackHeight.toPx()
                    drawRoundRect(track, Offset(inset, centreY - height / 2), Size(width, height), CornerRadius(height / 2))
                    val at = inset + width * shown.value
                    val from = if (model.bipolar) inset + width / 2 else inset
                    drawRoundRect(fill, Offset(minOf(from, at), centreY - height / 2), Size(abs(at - from), height), CornerRadius(height / 2))
                    val defaultX = inset + width * model.defaultFraction
                    drawRoundRect(mark, Offset(defaultX - 1.dp.toPx(), centreY - DefaultMark.toPx() / 2), Size(2.dp.toPx(), DefaultMark.toPx()), CornerRadius(1.dp.toPx()))
                    drawCircle(if (enabled && !model.detached) fill else mark, radius = Thumb.toPx() / 2, center = Offset(at, centreY))
                }
                if (bubble && enabled) {
                    val density = LocalDensity.current
                    val x = with(density) { (Thumb.toPx() / 2 + (trackWidth - Thumb.toPx()) * shown.value).roundToInt() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x - with(density) { 36.dp.roundToPx() }, -with(density) { BubbleLift.roundToPx() }) }
                            .size(width = 72.dp, height = 28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceContainerHigh)
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(model.valueText, color = colors.onSurface, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES))
                    }
                }
            }
            StepButton(up = true, label = stringResource(R.string.sound_slider_plus, model.label), enabled = enabled) { currentOnStep(true) }
        }
        if (model.marks.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StepButton + 8.dp)
                    .height(16.dp)
                    .clearAndSetSemantics { },
            ) {
                var width by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize().onSizeChanged { width = it.width }) {
                    model.marks.forEach { (fraction, word) ->
                        val density = LocalDensity.current
                        Text(
                            text = word,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            modifier = Modifier.offset { IntOffset((width * fraction).roundToInt() - with(density) { 20.dp.roundToPx() }, 0) },
                        )
                    }
                }
            }
        }
    }
}

/** − or +: one step a press, eight a second while held. */
@Composable
private fun StepButton(up: Boolean, label: String, enabled: Boolean, onStep: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val currentOnStep by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(StepButton)
            .clip(CircleShape)
            .border(1.dp, colors.outlineVariant, CircleShape)
            .semantics {
                contentDescription = label
                if (enabled) onClick { currentOnStep(); true } else disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        currentOnStep()
                        // In a scope of its own: a `coroutineScope` here would wait for the loop, which never ends.
                        val repeating = scope.launch {
                            delay(REPEAT_AFTER_MS)
                            while (true) {
                                currentOnStep()
                                delay(REPEAT_EVERY_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeating.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(if (up) AppIcons.Plus else AppIcons.Minus, contentDescription = null, tint = colors.onSurfaceVariant, size = 18.dp)
    }
}
