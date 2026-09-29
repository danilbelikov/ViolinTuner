package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The stepper of the tempo (spec 5.29 R4, «Формы»).
private val StepperHeight = 48.dp

/** «−» and «+» are 44 wide to the eye; Compose widens the touch of a target under 48 to 48 by itself, 2 into the air at each side. */
private val StepWidth = 44.dp
private val ValueMinWidth = 72.dp
private val MetronomeIcon = 16.dp
private val MetronomeGap = 4.dp
private const val SIGN_SIZE = 20
private const val VALUE_SIZE = 16
private const val HOLD_DELAY_MS = 600L
private const val HOLD_PERIOD_MS = 120L
private const val TAP_STEP = 1
private const val HOLD_STEP = 5

/**
 * «− [метроном] 100 +» of the tempo (spec 3.36.4, 5.29 R4): 48 high at a corner of 14 on surfaceContainer, «−» and «+» 44 wide and
 * pressed over 48 × 48, the value of 16 sp / 800 in tabular figures no narrower than 72 — [value] the number, or «—» without a tempo — after the
 * metronome, the sign of a tempo everywhere in the app (3.16). A tap moves by one, a hold — after a moment — by fives, so that 60 → 120
 * is a second's work and 96 → 97 is still possible. The stepper never takes the tempo away: the chip «—» beside it does (3.15).
 * TalkBack hears the value as [valueDescription] («темп 100», «Без темпа») and the signs as buttons [downDescription] and
 * [upDescription].
 */
@Composable
fun TempoStepper(
    value: String,
    valueDescription: String,
    onStep: (by: Int) -> Unit,
    downDescription: String,
    upDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(StepperHeight)
            .background(colors.surfaceContainer, AppShapes.Control),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", downDescription) { onStep(-it) }
        Row(
            modifier = Modifier
                .widthIn(min = ValueMinWidth)
                .clearAndSetSemantics { contentDescription = valueDescription },
            horizontalArrangement = Arrangement.spacedBy(MetronomeGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(AppIcons.Metronome, contentDescription = null, size = MetronomeIcon, tint = colors.onSurface)
            Text(
                text = value,
                color = colors.onSurface,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = VALUE_SIZE.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            )
        }
        StepButton("+", upDescription) { onStep(it) }
    }
}

@Composable
private fun StepButton(sign: String, description: String, onStep: (size: Int) -> Unit) {
    val currentOnStep by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .width(StepWidth)
            .height(StepperHeight)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var held = false
                        val repeat: Job = scope.launch {
                            delay(HOLD_DELAY_MS)
                            held = true
                            while (true) {
                                currentOnStep(HOLD_STEP)
                                delay(HOLD_PERIOD_MS)
                            }
                        }
                        val released = tryAwaitRelease()
                        repeat.cancel()
                        if (released && !held) currentOnStep(TAP_STEP)
                    },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    currentOnStep(TAP_STEP)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = SIGN_SIZE.sp, fontWeight = FontWeight.Bold))
    }
}
