package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ButtonSize = 56.dp
private const val DISABLED_ALPHA = 0.3f
private const val TABULAR_FIGURES = "tnum"
private const val REPEAT_DELAY_MS = 400L
private const val VALUE_SIZE = 56
private const val MIN_VALUE_SIZE = 28
private const val REPEAT_PERIOD_MS = 120L

/** «− value +» with a big tabular number; holding a button repeats it (handoff `anims`, «Степпер ±5»). */
@Composable
fun Stepper(
    value: String,
    onStep: (steps: Int) -> Unit,
    canStepDown: Boolean,
    canStepUp: Boolean,
    downDescription: String,
    upDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatingButton(label = "−", enabled = canStepDown, description = downDescription, onFire = { onStep(-1) })
        // «1 ч 35 мин» is wider than «47 мин»: the number shrinks rather than wraps, so the
        // buttons never move under a finger that is holding one.
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_VALUE_SIZE.sp, maxFontSize = VALUE_SIZE.sp, stepSize = 2.sp),
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = VALUE_SIZE.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        RepeatingButton(label = "+", enabled = canStepUp, description = upDescription, onFire = { onStep(+1) })
    }
}

@Composable
private fun RepeatingButton(label: String, enabled: Boolean, description: String, onFire: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val currentOnFire by rememberUpdatedState(onFire)
    val currentEnabled by rememberUpdatedState(enabled)
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .border(1.dp, colors.outlineVariant, CircleShape)
            .semantics {
                this.role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick { currentOnFire(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!currentEnabled) return@detectTapGestures
                        currentOnFire()
                        coroutineScope {
                            val repeater = launch {
                                delay(REPEAT_DELAY_MS)
                                while (currentEnabled) {
                                    currentOnFire()
                                    delay(REPEAT_PERIOD_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeater.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** A column that keeps the stepper and its hint together. */
@Composable
fun StepperBlock(hint: String, modifier: Modifier = Modifier, stepper: @Composable () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stepper()
        Text(
            text = hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        )
    }
}
