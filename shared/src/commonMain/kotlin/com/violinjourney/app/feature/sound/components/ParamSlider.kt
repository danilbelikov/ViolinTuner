package com.violinjourney.app.feature.sound.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ButtonFit
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.sound_reset
import com.violinjourney.app.shared.resources.sound_slider_minus
import com.violinjourney.app.shared.resources.sound_slider_off
import com.violinjourney.app.shared.resources.sound_slider_plus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// The one slider of «Звук» (spec 3.17; 5.29 R5: a row of 52 pressed whole, − and + seen 36 in a touch of 48, a track of 6).
private val RowHeight = 52.dp
private val StepTarget = 48.dp
private val StepVisible = 36.dp
private val StepCorner = 10.dp
private val StepIcon = 16.dp
private val TrackHeight = 6.dp
private val Thumb = 20.dp
private val ThumbHalo = 4.dp
private const val HALO_ALPHA = 0.35f
private val DefaultMarkHeight = 16.dp
private val DefaultMarkWidth = 2.dp
private val LabelGap = 8.dp

/** The name over the track: 14 sp, 700, on up to two lines; where its widest word does not stand whole, a step smaller down to 12. */
private const val LABEL_SP = 14f
private const val LABEL_MIN_SP = 12f
private const val LABEL_LINES = 2

/** The name is measured in whole pixels: a word that fits only by a hair is not trusted. */
private val LabelSlack = 1.dp
private val BubbleLift = 30.dp
private const val DISABLED_ALPHA = 0.38f
private const val THUMB_TRAVEL_MS = 150
private const val RESET_TRAVEL_MS = 200
private const val BUBBLE_LINGER_MS = 300L
private const val REPEAT_AFTER_MS = 400L
private const val REPEAT_EVERY_MS = 125L // eight steps a second
private const val TABULAR_FIGURES = "tnum"

/**
 * Where a fraction stands along a slider [width] pixels wide whose thumb keeps [inset] pixels from either end — the
 * touch, the drawing, the bubble and the words under the track all ask here, so they stand where the thumb does. Pure.
 */
internal object SliderGeometry {
    fun thumbCentre(fraction: Float, width: Float, inset: Float): Float = inset + (width - inset * 2) * fraction

    fun fractionAt(x: Float, width: Float, inset: Float): Float = ((x - inset) / (width - inset * 2)).coerceIn(0f, 1f)

    /** The left edge of a word [labelWidth] wide centred under the thumb at [fraction], kept within the track. */
    fun markLeft(fraction: Float, labelWidth: Int, width: Int, inset: Float): Int =
        (thumbCentre(fraction, width.toFloat(), inset) - labelWidth / 2f).roundToInt().coerceIn(0, (width - labelWidth).coerceAtLeast(0))
}

/**
 * The line over a slider's track (spec 3.36.5, 5.29 R5): the value at the right is measured first and keeps the room of the widest
 * text it can take ([SliderModel.valueReserve]) — it is never cut, and the name beside it does not move, nor the track under the
 * finger, while it changes; the name takes what is left, on up to two lines at its spaces; the hint follows a name of one line, where
 * at least its widest word stands — otherwise it is left out, not shown as «…». Pure; pixels.
 */
internal object SliderHead {
    /** The room of the name: what the room of the value and the gap before it leave of the line. */
    fun labelRoom(width: Int, valueRoom: Int, gap: Int): Int = (width - valueRoom - gap).coerceAtLeast(0)

    /**
     * The room of the hint after a name of [label] px on [oneLine], or null: the name took two lines, or less than [hintLeast] — the
     * widest word of the hint — is left after it and the gap.
     */
    fun hintRoom(labelRoom: Int, label: Int, gap: Int, hintLeast: Int, oneLine: Boolean): Int? =
        (labelRoom - label - gap).takeIf { oneLine && it >= hintLeast }
}

/**
 * How long the thumb travels to [target]: to where a reset sent it ([resetTo]) — a double tap, the reader's «Сбросить» —
 * in 200 ms, anywhere else in 150 (handoff `anims` of «Звук»). Pure.
 */
internal fun sliderTravelMs(target: Float, resetTo: Float?): Int = if (resetTo == target) RESET_TRAVEL_MS else THUMB_TRAVEL_MS

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
    /**
     * Where a reset — a double tap, the reader's «Сбросить» — sends the value: the default mark, unless the slider
     * resets elsewhere («Сдвиг» of a backing: to the shift the take was recorded with).
     */
    val resetFraction: Float = defaultFraction,
    /**
     * The widest texts the value can take (`SliderValues`): the value keeps the room of the widest of them — or of [valueText], if
     * that is wider — at the right of the name, so that the name does not move while the value changes. Empty — the room of
     * [valueText] alone.
     */
    val valueReserve: List<String> = emptyList(),
)

/**
 * The one control of a value on the «Звук» screen (handoff 18g; spec 3.36.5, 5.29 R5): twenty parameters, one slider — the eye
 * stops seeing the control and reads the captions. The name (14 sp, 700) and the value (14 sp, 800) above, at the right — the value
 * whole in the room of its widest text, the name in what is left ([SliderHead]); under them «−», the track and «+». The whole row of
 * 52 takes the touch, the value stands as a number with its unit and rides above the finger
 * while dragged, − and + — 36 seen on the ground of the screen in a touch of 48 — step finely (held — they repeat), the default has
 * a mark of 2 × 16 in the third level of text, a double tap returns to it; the thumb is white in a halo of the accent.
 *
 * To TalkBack and VoiceOver the track is a slider moved by swiping; the way back to the default is the action «Сбросить»
 * of the actions menu. Their activation — the double tap of a reader — does nothing: it resets no setting by the way, and
 * without an action of its own it would come as a touch in the middle of the track and set the value there.
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
    // where a reset has sent the value: the thumb goes there a little slower
    var resetTo by remember { mutableStateOf<Float?>(null) }

    // Under the finger the thumb is the finger; otherwise it travels to wherever the value went (a tap on the track, a double tap, a preset).
    LaunchedEffect(model.fraction, dragging) {
        if (dragging) shown.snapTo(model.fraction) else shown.animateTo(model.fraction, tween(sliderTravelMs(model.fraction, resetTo)))
        resetTo = null
    }

    val currentOnFraction by rememberUpdatedState(onFraction)
    val currentOnReset by rememberUpdatedState(onReset)
    val currentOnStep by rememberUpdatedState(onStep)
    val currentResetFraction by rememberUpdatedState(model.resetFraction)
    val resetLabel = stringResource(Res.string.sound_reset)
    val offText = stringResource(Res.string.sound_slider_off)

    Column(modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        SliderHeadLine(model)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(up = false, label = stringResource(Res.string.sound_slider_minus, model.label), enabled = enabled) { currentOnStep(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(RowHeight)
                    .onSizeChanged { trackWidth = it.width }
                    .semantics(mergeDescendants = true) {
                        contentDescription = model.label
                        stateDescription = if (enabled) model.valueText else offText
                        progressBarRangeInfo = ProgressBarRangeInfo(model.fraction, 0f..1f)
                        if (enabled) {
                            setProgress { target -> currentOnFraction(target.coerceIn(0f, 1f)); true }
                            onClick { true }
                            customActions = listOf(
                                CustomAccessibilityAction(resetLabel) {
                                    resetTo = model.resetFraction
                                    currentOnReset()
                                    true
                                },
                            )
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
                            fun fractionAt(x: Float) = SliderGeometry.fractionAt(x, size.width.toFloat(), inset)
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
                                    resetTo = currentResetFraction
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
                val mark = ViolinTheme.textTertiary
                val halo = colors.primary.copy(alpha = HALO_ALPHA)
                // a knob that has let go of its numbers is not the white one: the value is not on the track
                val knob = if (model.detached) mark else Color.White
                Canvas(Modifier.fillMaxSize()) {
                    val inset = Thumb.toPx() / 2
                    val width = size.width - inset * 2
                    val centreY = size.height / 2
                    val height = TrackHeight.toPx()
                    drawRoundRect(track, Offset(inset, centreY - height / 2), Size(width, height), CornerRadius(height / 2))
                    val at = SliderGeometry.thumbCentre(shown.value, size.width, inset)
                    val from = if (model.bipolar) inset + width / 2 else inset
                    drawRoundRect(fill, Offset(minOf(from, at), centreY - height / 2), Size(abs(at - from), height), CornerRadius(height / 2))
                    val defaultX = inset + width * model.defaultFraction
                    val markWidth = DefaultMarkWidth.toPx()
                    val markHeight = DefaultMarkHeight.toPx()
                    drawRoundRect(mark, Offset(defaultX - markWidth / 2, centreY - markHeight / 2), Size(markWidth, markHeight), CornerRadius(markWidth / 2))
                    if (enabled && !model.detached) drawCircle(halo, radius = Thumb.toPx() / 2 + ThumbHalo.toPx(), center = Offset(at, centreY))
                    drawCircle(knob, radius = Thumb.toPx() / 2, center = Offset(at, centreY))
                }
                if (bubble && enabled) {
                    val density = LocalDensity.current
                    val x = with(density) { SliderGeometry.thumbCentre(shown.value, trackWidth.toFloat(), Thumb.toPx() / 2).roundToInt() }
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
            StepButton(up = true, label = stringResource(Res.string.sound_slider_plus, model.label), enabled = enabled) { currentOnStep(true) }
        }
        if (model.marks.isNotEmpty()) {
            // as wide as the track, each word centred under the place of the thumb at its fraction (spec 5.11)
            Layout(
                content = {
                    model.marks.forEach { (_, word) ->
                        Text(text = word, color = colors.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StepTarget)
                    .height(16.dp)
                    .clearAndSetSemantics { },
            ) { measurables, constraints ->
                val words = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                val inset = Thumb.toPx() / 2
                layout(constraints.maxWidth, constraints.maxHeight) {
                    words.forEachIndexed { index, word ->
                        word.place(SliderGeometry.markLeft(model.marks[index].first, word.width, constraints.maxWidth, inset), 0)
                    }
                }
            }
        }
    }
}

/**
 * The name, its hint and the value over the track ([SliderHead]): the value — 14 sp, 800, tabular figures — stands at the right in the
 * room of the widest text it can take; the name — 14 sp, 700 — on up to two lines in what is left, a step smaller at a time down to
 * 12 sp where its widest word would not stand whole (German «Ausgangsverstärkung» in the narrow column of a phone lying, at the font
 * 1.3); the hint after a name of one line. The value and the hint stand on the baseline of the last line of the name.
 */
@Composable
private fun SliderHeadLine(model: SliderModel) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = LABEL_SP.sp, fontWeight = FontWeight.Bold)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES)
    val hintStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hint = model.hint?.takeIf { it.isNotBlank() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = with(density) { LabelGap.roundToPx() }
        // the room of the value whatever it says: its widest text, measured once for the slider — not at every step of a drag
        val reserve = remember(model.valueReserve, valueStyle, measurer) {
            model.valueReserve.maxOfOrNull { measurer.measure(it, valueStyle, softWrap = false, maxLines = 1).size.width } ?: 0
        }
        val room = SliderHead.labelRoom(constraints.maxWidth, reserve, gap)
        val labelSp = remember(model.label, room, labelStyle, measurer, density) {
            val words = model.label.split(' ').filter { it.isNotEmpty() }
            ButtonFit.size(room - with(density) { LabelSlack.toPx() }, LABEL_SP, LABEL_MIN_SP) { sp ->
                words.maxOfOrNull { measurer.measure(it, labelStyle.copy(fontSize = sp.sp), softWrap = false, maxLines = 1).size.width.toFloat() } ?: 0f
            }
        }
        Layout(
            contents = listOf(
                {
                    Text(
                        text = model.label,
                        color = colors.onSurface,
                        maxLines = LABEL_LINES,
                        overflow = TextOverflow.Ellipsis,
                        style = if (labelSp == LABEL_SP) labelStyle else labelStyle.copy(fontSize = labelSp.sp),
                    )
                },
                { if (hint != null) Text(hint, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = hintStyle) },
                {
                    Text(
                        text = model.valueText,
                        color = if (model.detached) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        style = valueStyle,
                    )
                },
            ),
        ) { (labels, hints, values), constraints ->
            val width = constraints.maxWidth
            // first the value, whole: nothing beside it can take its room
            val value = values.single().measure(Constraints(maxWidth = width))
            val labelRoom = SliderHead.labelRoom(width, maxOf(reserve, value.width), gap)
            val label = labels.single().measure(Constraints(maxWidth = labelRoom))
            val labelFirst = label[FirstBaseline]
            val labelLast = label[LastBaseline]
            val hintPlaced = hints.firstOrNull()?.let { measurable ->
                val least = measurable.minIntrinsicWidth(Constraints.Infinity)
                SliderHead.hintRoom(labelRoom, label.width, gap, least, oneLine = labelFirst == labelLast)?.let { measurable.measure(Constraints(maxWidth = it)) }
            }
            // one baseline for all three: the last line of the name
            val valueBase = value[FirstBaseline]
            val hintBase = hintPlaced?.get(FirstBaseline) ?: 0
            val baseline = maxOf(labelLast, valueBase, hintBase)
            val labelY = baseline - labelLast
            val valueY = baseline - valueBase
            val hintY = baseline - hintBase
            val height = maxOf(labelY + label.height, valueY + value.height, hintPlaced?.let { hintY + it.height } ?: 0)
            layout(width, height) {
                label.placeRelative(0, labelY)
                hintPlaced?.placeRelative(label.width + gap, hintY)
                value.placeRelative(width - value.width, valueY)
            }
        }
    }
}

/** − or +: one step a press, eight a second while held; 36 seen on the ground of the screen, 48 pressed. */
@Composable
private fun StepButton(up: Boolean, label: String, enabled: Boolean, onStep: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val currentOnStep by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(StepTarget)
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
                        // a pointerInput started anew (the block switched off by another finger) cancels the press
                        // instead of releasing it: the repeat stops however the gesture ends
                        try {
                            tryAwaitRelease()
                        } finally {
                            repeating.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(StepVisible)
                .background(colors.surface, RoundedCornerShape(StepCorner)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(if (up) AppIcons.Plus else AppIcons.Minus, contentDescription = null, tint = colors.onSurface, size = StepIcon)
        }
    }
}
