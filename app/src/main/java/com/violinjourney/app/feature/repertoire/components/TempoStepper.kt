package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StepperHeight = 44.dp
private val ValueMinWidth = 64.dp
private const val HOLD_DELAY_MS = 600L
private const val HOLD_PERIOD_MS = 120L
private const val TAP_STEP = 1
private const val HOLD_STEP = 5

/**
 * «− 96 +» of the tempo (handoff 13e1): a tap moves by one, a hold — after a moment — by fives,
 * so that 60 → 120 is a second's work and 96 → 97 is still possible.
 */
@Composable
fun TempoStepper(value: String, onStep: (by: Int) -> Unit, downDescription: String, upDescription: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(StepperHeight)
            .background(colors.surfaceContainer, RoundedCornerShape(StepperHeight / 2)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", downDescription) { onStep(-it) }
        Box(modifier = Modifier.widthIn(min = ValueMinWidth), contentAlignment = Alignment.Center) {
            Text(
                text = value,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
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
            .size(StepperHeight)
            .clip(CircleShape)
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
        Text(sign, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
    }
}
