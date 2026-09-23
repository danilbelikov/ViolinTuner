package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptIntent
import java.time.ZoneId

private val DialogCorner = 28.dp
private val DialogPaddingTop = 28.dp
private val DialogPaddingSide = 24.dp
private val DialogPaddingBottom = 20.dp
private val IconSize = 44.dp
private val ClockGlyphSize = 28.dp
private val MainButtonHeight = 48.dp
private val MainButtonCorner = 24.dp
private val TextButtonHeight = 44.dp

/** Shows the forgotten-practice dialog or the summary sheet, whichever the app asks (spec 3.12). */
@Composable
fun PracticePromptHost(
    prompt: PracticePrompt?,
    stepMinutes: Int,
    onIntent: (PracticePromptIntent) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    when (prompt) {
        is PracticePrompt.Forgotten -> ForgottenPracticeDialog(prompt, onIntent, zone)
        is PracticePrompt.Summary -> SummarySheet(
            sheet = prompt.sheet,
            stepMinutes = stepMinutes,
            onIntent = { intent ->
                when (intent) {
                    is PracticeIntent.SummaryStepped -> onIntent(PracticePromptIntent.SummaryStepped(intent.steps))
                    PracticeIntent.SummarySaved -> onIntent(PracticePromptIntent.SummarySaved)
                    PracticeIntent.SummaryDiscarded -> onIntent(PracticePromptIntent.SummaryDiscarded)
                    else -> Unit
                }
            },
        )
        null -> Unit
    }
}

/** «Занятие не закончено» (handoff 10f1 with the last sound, 10f2 without). */
@Composable
fun ForgottenPracticeDialog(prompt: PracticePrompt.Forgotten, onIntent: (PracticePromptIntent) -> Unit, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    // Dismissing by a tap outside means "I am still practising": the safest of the answers.
    Dialog(
        onDismissRequest = { onIntent(PracticePromptIntent.Continue) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerHigh, RoundedCornerShape(DialogCorner))
                .padding(start = DialogPaddingSide, end = DialogPaddingSide, top = DialogPaddingTop, bottom = DialogPaddingBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ClockIcon()
            Text(
                text = stringResource(R.string.practice_forgotten_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            val elapsedText = stringResource(R.string.practice_forgotten_text, Formats.minutesInWords(prompt.elapsedMs))
            val lastSound = prompt.lastSoundEpochMs?.let { Formats.timeOfDay(it, zone) }
            Text(
                text = if (lastSound != null) {
                    elapsedText + " " + stringResource(R.string.practice_forgotten_sound, lastSound)
                } else {
                    elapsedText
                },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            )
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lastSound != null) {
                    MainButton(stringResource(R.string.practice_end_at, lastSound)) { onIntent(PracticePromptIntent.EndAtLastSound) }
                    SecondaryButton(stringResource(R.string.practice_end_now)) { onIntent(PracticePromptIntent.EndNow) }
                } else {
                    MainButton(stringResource(R.string.practice_end_now)) { onIntent(PracticePromptIntent.EndNow) }
                    SecondaryButton(stringResource(R.string.practice_edit_time)) { onIntent(PracticePromptIntent.EditTime) }
                }
                SecondaryButton(stringResource(R.string.practice_continue)) { onIntent(PracticePromptIntent.Continue) }
            }
        }
    }
}

@Composable
private fun ClockIcon() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(IconSize)
            .background(colors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.Clock, contentDescription = null, tint = colors.onPrimaryContainer, size = ClockGlyphSize)
    }
}

@Composable
private fun MainButton(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MainButtonHeight),
        shape = RoundedCornerShape(MainButtonCorner),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(TextButtonHeight),
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
    }
}
