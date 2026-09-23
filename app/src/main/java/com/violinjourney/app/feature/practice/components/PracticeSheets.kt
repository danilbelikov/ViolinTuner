package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.practice.PlayedLine
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeSheet

private val PlayedRowHeight = 44.dp
private val PlayedTick = 16.dp
/** More than this and «Что играли» scrolls inside itself. */
private const val PLAYED_ROWS_IN_SIGHT = 4
private const val TABULAR_FIGURES = "tnum"
private val SheetPadding = 24.dp
private val SheetBottom = 32.dp
private val PrimaryButtonHeight = 56.dp
private val PrimaryCorner = 28.dp
private val TextButtonHeight = 40.dp
private val ChipHeight = 36.dp
private val ChipCorner = 8.dp
private const val CHIP_TEN = 10
private const val CHIP_THIRTY = 30
private const val CHIP_HOUR = 60

/** "Закончить занятие": the timed length with a stepper to trim it (spec 3.12, handoff 10d). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(sheet: PracticeSheet.Summary, stepMinutes: Int, onIntent: (PracticeIntent) -> Unit) {
    ModalBottomSheet(
        // hiding the sheet is not an answer: saving and throwing away are its two buttons alone
        onDismissRequest = { onIntent(PracticeIntent.SummaryHidden) },
        // with «Что играли» the sheet is taller than half a screen: it opens whole, «Не сохранять» never under the fold
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        SummarySheetContent(sheet, stepMinutes, onIntent)
    }
}

@Composable
internal fun SummarySheetContent(sheet: PracticeSheet.Summary, stepMinutes: Int, onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    SheetColumn(modifier) {
        SheetTitle(stringResource(R.string.practice_summary_title))
        StepperBlock(hint = stringResource(R.string.practice_step_hint, stepMinutes)) {
            Stepper(
                value = Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE),
                onStep = { onIntent(PracticeIntent.SummaryStepped(it)) },
                canStepDown = sheet.minutes > sheet.minMinutes,
                canStepUp = sheet.minutes < sheet.maxMinutes,
                downDescription = stringResource(R.string.practice_step_down, stepMinutes),
                upDescription = stringResource(R.string.practice_step_up, stepMinutes),
            )
        }
        if (sheet.played.isNotEmpty()) PlayedBlock(sheet.played)
        PrimaryButton(text = stringResource(R.string.practice_save), onClick = { onIntent(PracticeIntent.SummarySaved) })
        SecondaryButton(text = stringResource(R.string.practice_discard), onClick = { onIntent(PracticeIntent.SummaryDiscarded) })
    }
}

/**
 * «Что играли» (spec 3.28, handoff 30g): the blocks of the practice in the order they were played — the element,
 * its time, the tick of one played to its goal. No takts: they come as one «+N» on «Занятия». A long list scrolls
 * inside itself, so the stepper and «Сохранить» stay in sight in a low window.
 */
@Composable
private fun PlayedBlock(played: List<PlayedLine>) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = stringResource(R.string.block_played_title),
            modifier = Modifier.padding(bottom = 6.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Column(
            modifier = Modifier
                .heightIn(max = PlayedRowHeight * PLAYED_ROWS_IN_SIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            played.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PlayedRowHeight)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = line.title,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = if (line.done) Formats.minutesInWords(line.goalMinutes * MS_PER_MINUTE) else stringResource(R.string.block_part_of, line.minutes, line.goalMinutes),
                        color = if (line.done) colors.onSurface else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                    if (line.done) {
                        AppIcon(AppIcons.Check, contentDescription = stringResource(R.string.block_done), size = PlayedTick, tint = colors.onSurface)
                    } else {
                        Spacer(Modifier.size(PlayedTick))
                    }
                }
            }
        }
    }
}

/** "Изменить время" of a day: the whole day's time (spec 3.12, handoff 10e). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimeSheet(sheet: PracticeSheet.EditTime, stepMinutes: Int, onIntent: (PracticeIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(PracticeIntent.EditTimeCancelled) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        EditTimeSheetContent(sheet, stepMinutes, onIntent)
    }
}

@Composable
internal fun EditTimeSheetContent(sheet: PracticeSheet.EditTime, stepMinutes: Int, onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SheetColumn(modifier) {
        Column {
            SheetTitle(stringResource(R.string.practice_edit_title))
            Text(
                text = Formats.dayWithWeekday(sheet.date),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )
        }
        StepperBlock(hint = stringResource(R.string.practice_step_hint, stepMinutes)) {
            Stepper(
                value = Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE),
                onStep = { onIntent(PracticeIntent.EditTimeStepped(it)) },
                canStepDown = sheet.minutes > 0,
                canStepUp = sheet.minutes < sheet.maxMinutes,
                downDescription = stringResource(R.string.practice_step_down, stepMinutes),
                upDescription = stringResource(R.string.practice_step_up, stepMinutes),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.practice_chip_plus_minutes, CHIP_TEN)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_TEN)) }
            Chip(stringResource(R.string.practice_chip_plus_minutes, CHIP_THIRTY)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_THIRTY)) }
            Chip(stringResource(R.string.practice_chip_plus_hour)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_HOUR)) }
            Chip(stringResource(R.string.practice_chip_zero)) { onIntent(PracticeIntent.EditTimeCleared) }
        }
        Text(
            text = stringResource(R.string.practice_edit_hint),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        )
        PrimaryButton(text = stringResource(R.string.practice_save), onClick = { onIntent(PracticeIntent.EditTimeSaved) })
        SecondaryButton(text = stringResource(R.string.dialog_cancel), onClick = { onIntent(PracticeIntent.EditTimeCancelled) })
    }
}

@Composable
internal fun SheetColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SheetPadding, end = SheetPadding, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        content()
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    )
}


@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ChipCorner)
    Box(
        modifier = Modifier
            .height(ChipHeight)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(PrimaryButtonHeight),
        shape = RoundedCornerShape(PrimaryCorner),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
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
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        )
    }
}
