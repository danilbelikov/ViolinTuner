package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The stepper of «Закончить занятие» and «Время за день» (spec 3.36.3, 5.29 R3), and of the sheet «Длительность» of an event (3.36.9).
private val ButtonSize = 64.dp
private val GlyphSize = 28.dp
private val ValueGap = 12.dp
private const val DISABLED_ALPHA = 0.38f

/** The size of the number of R3: 40 sp. The sheet «Длительность» of an event draws it 32 (5.29 R9: «2 ч 30 мин» in 40 does not fit). */
const val STEPPER_VALUE_SIZE = 40
private const val VALUE_STEP = 2
private const val VALUE_TRACKING = -0.03

/**
 * The line of the number, whatever size it is drawn at: 21/20 of the full size — 42 sp of 40 — so the row does not change its height
 * with the number. Whole numbers, so that 42 of 40 stays 42 exactly.
 */
private const val VALUE_LINE_TWENTIETHS = 21
private const val TWENTIETHS = 20f

/**
 * The smallest the number gets, whatever the size of the system font: «11 Std. 55 Min.» and «11 小时 55 分钟» (≈ 6.8 em of Manrope
 * 800) fit the 168 dp its column has in a sheet 360 wide (149 dp). A floor of 28 sp cut them — 190 dp at the usual font, and more as
 * the font grew with it.
 */
private val MinValueSize = 22.dp
private const val CAPTION_SIZE = 13
private const val CAPTION_LINE_HEIGHT = 18
private const val CAPTION_MAX_LINES = 2
private const val TABULAR_FIGURES = "tnum"
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_PERIOD_MS = 120L

/**
 * «− 47 мин +» (spec 3.36.3, 5.29 R3): «−» and «+» of 64 in circles of surfaceContainerHigh, the number [valueSize] sp / 800 in tabular
 * figures between them — 40 of R3, 32 in the sheet «Длительность» of an event (5.29 R9) — smaller down to 22 dp rather than on two lines
 * or cut, on a line of one height at any size — and under it the [caption] («17:55 — 18:42 · было 47 мин»), up to two lines. The place
 * of the caption is held by [captionReserve], the longest the caption of this sheet can be, drawn unseen: a caption that appears or
 * grows, or a number that gets smaller, never moves the buttons under a finger. A tap steps once when the finger lifts, holding repeats;
 * a button at its limit is dimmed to 0.38. TalkBack reads the number with its caption as one [valueDescription].
 */
@Composable
fun Stepper(
    value: String,
    onStep: (steps: Int) -> Unit,
    canStepDown: Boolean,
    canStepUp: Boolean,
    downDescription: String,
    upDescription: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionReserve: String? = caption,
    valueDescription: String = value,
    valueSize: Int = STEPPER_VALUE_SIZE,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val smallest = remember(density, valueSize) { smallestValueSp(with(density) { MinValueSize.toSp() }.value, valueSize).sp }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ValueGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatingButton(icon = AppIcons.Minus, enabled = canStepDown, description = downDescription, onFire = { onStep(-1) })
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = valueDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // «1 ч 35 мин» is wider than «47 мин»: the number shrinks rather than wraps
            Text(
                text = value,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = valueSize.sp, stepSize = VALUE_STEP.sp),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = valueSize.sp,
                    // in sp, not em: a smaller number keeps the line, the column and the buttons beside it where they were
                    lineHeight = (valueSize * VALUE_LINE_TWENTIETHS / TWENTIETHS).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = VALUE_TRACKING.em,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            if (caption != null || captionReserve != null) {
                Box(contentAlignment = Alignment.TopCenter) {
                    // the place the caption keeps, seen or not
                    if (captionReserve != null) Caption(captionReserve, Modifier.alpha(0f))
                    if (caption != null) Caption(caption)
                }
            }
        }
        RepeatingButton(icon = AppIcons.Plus, enabled = canStepUp, description = upDescription, onFire = { onStep(+1) })
    }
}

/**
 * The smallest size of the number in sp, given [floorSp] — [MinValueSize] in the sp of the screen — and the full size [valueSp]: not
 * above the floor, on the grid of [VALUE_STEP] down from the full size, so that the full size stays 40 sp (or 32) exactly — the sizes of
 * `TextAutoSize.StepBased` are its smallest plus whole steps.
 */
internal fun smallestValueSp(floorSp: Float, valueSp: Int = STEPPER_VALUE_SIZE): Float {
    val steps = ceil((valueSp - floorSp) / VALUE_STEP).coerceIn(0f, (valueSp / VALUE_STEP).toFloat())
    return valueSp - steps * VALUE_STEP
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = CAPTION_MAX_LINES,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = CAPTION_SIZE.sp,
            lineHeight = CAPTION_LINE_HEIGHT.sp,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
}

/**
 * A tap is one step when the finger lifts — a press the sheet takes for its drag, one that began on «−» or «+», changes
 * nothing; holding repeats from [REPEAT_DELAY_MS], every [REPEAT_PERIOD_MS] (as the tempo stepper of a piece).
 */
@Composable
private fun RepeatingButton(icon: ImageVector, enabled: Boolean, description: String, onFire: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val currentOnFire by rememberUpdatedState(onFire)
    val currentEnabled by rememberUpdatedState(enabled)
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            // the ripple of every other button, round within the clip
            .indication(interactions, LocalIndication.current)
            .semantics {
                this.role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick { currentOnFire(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        if (!currentEnabled) return@detectTapGestures
                        val press = PressInteraction.Press(offset)
                        interactions.tryEmit(press)
                        var repeated = false
                        var released = false
                        try {
                            released = coroutineScope {
                                val repeater = launch {
                                    delay(REPEAT_DELAY_MS)
                                    while (currentEnabled) {
                                        repeated = true
                                        currentOnFire()
                                        delay(REPEAT_PERIOD_MS)
                                    }
                                }
                                tryAwaitRelease().also { repeater.cancel() }
                            }
                        } finally {
                            interactions.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        }
                        if (released && !repeated && currentEnabled) currentOnFire()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, size = GlyphSize, tint = colors.onSurface)
    }
}
