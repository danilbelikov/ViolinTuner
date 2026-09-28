package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.DayCaption
import com.violinjourney.app.feature.practice.PlayedLine
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeSheet
import com.violinjourney.app.feature.practice.SummaryHint
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_part_of
import com.violinjourney.app.shared.resources.block_played_title
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.practice_chip_plus_hour
import com.violinjourney.app.shared.resources.practice_chip_plus_minutes
import com.violinjourney.app.shared.resources.practice_chip_zero
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_day_add_length
import com.violinjourney.app.shared.resources.practice_day_add_reason
import com.violinjourney.app.shared.resources.practice_day_no_phone
import com.violinjourney.app.shared.resources.practice_day_was
import com.violinjourney.app.shared.resources.practice_discard
import com.violinjourney.app.shared.resources.practice_edit_hint
import com.violinjourney.app.shared.resources.practice_edit_hint_empty
import com.violinjourney.app.shared.resources.practice_edit_title
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_played_done_description
import com.violinjourney.app.shared.resources.practice_played_dropped
import com.violinjourney.app.shared.resources.practice_played_dropped_description
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.practice_save_length
import com.violinjourney.app.shared.resources.practice_span
import com.violinjourney.app.shared.resources.practice_span_was
import com.violinjourney.app.shared.resources.practice_step_down
import com.violinjourney.app.shared.resources.practice_step_up
import com.violinjourney.app.shared.resources.practice_stepper_description
import com.violinjourney.app.shared.resources.practice_stepper_description_was
import com.violinjourney.app.shared.resources.practice_stop
import com.violinjourney.app.shared.resources.practice_summary_hint
import com.violinjourney.app.shared.resources.practice_summary_hint_cut
import com.violinjourney.app.shared.resources.practice_summary_hint_short
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

// The sheets of time (spec 3.36.3, 5.29 R3; practice-sheets.html 1 and 4).
private val StepperTop = 12.dp
private val StepperBottom = 4.dp
private val HintTop = 12.dp
private val DateTop = 4.dp
private const val DATE_SIZE = 15
private const val HINT_SIZE = 13
private const val HINT_LINE_HEIGHT = 1.45
private val ChipsTop = 14.dp
private val ChipMinWidth = 60.dp
private val ChipGap = 8.dp
private const val CHIP_TEN = 10
private const val CHIP_THIRTY = 30
private const val CHIP_HOUR = 60

// «Что играли»: a group on the ground of the screen (5.29 R3).
private val PlayedTop = 14.dp
private val GroupPaddingVertical = 12.dp
private val GroupPaddingHorizontal = 14.dp
private val PlayedRowMinHeight = 44.dp
private val PlayedRowGap = 10.dp
private val PlayedIcon = 18.dp
private val PlayedTick = 16.dp
private val PlayedTickGap = 4.dp
private val DividerThickness = 1.dp
private const val PLAYED_SIZE = 14

/** More than this and «Что играли» scrolls inside itself. */
private const val PLAYED_ROWS_IN_SIGHT = 4
private const val TABULAR_FIGURES = "tnum"

/**
 * «Закончить занятие» (spec 3.36.3; practice-sheets.html 1): the label, the stepper with «17:55 — 18:42» under the number — and «· было
 * 47 мин» once it moved — the hint, and «Что играли» without a title of its own. Its buttons are [SummaryButtons], at the bottom of the
 * sheet. Not tied to one owner: «Занятия» and the prompt of the app both show it, each hearing the stepper in [onStep].
 */
@Composable
fun SummarySheetContent(
    sheet: PracticeSheet.Summary,
    stepMinutes: Int,
    zone: TimeZone,
    onStep: (steps: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.practice_stop)
    val span = PracticeReducer.spanOf(sheet)
    val from = Formats.timeOfDay(span.startEpochMs, zone)
    val to = Formats.timeOfDay(span.endEpochMs, zone)
    val value = Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE)
    val was = span.wasMs?.let(Formats::minutesInWords)
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        SectionLabel(title)
        Stepper(
            value = value,
            onStep = onStep,
            canStepDown = sheet.minutes > sheet.minMinutes,
            canStepUp = sheet.minutes < sheet.maxMinutes,
            downDescription = stringResource(Res.string.practice_step_down, stepMinutes),
            upDescription = stringResource(Res.string.practice_step_up, stepMinutes),
            modifier = Modifier.padding(top = StepperTop, bottom = StepperBottom),
            caption = if (was == null) stringResource(Res.string.practice_span, from, to) else stringResource(Res.string.practice_span_was, from, to, was),
            // the longest this caption gets: «· было …» with the length the sheet opened with
            captionReserve = stringResource(Res.string.practice_span_was, from, to, Formats.minutesInWords(sheet.actualMs)),
            valueDescription = if (was == null) {
                stringResource(Res.string.practice_stepper_description, value, from, to)
            } else {
                stringResource(Res.string.practice_stepper_description_was, value, from, to, was)
            },
        )
        val hint = PracticeReducer.hintOf(sheet)
        Hint(
            text = hintText(hint, sheet.floorMinutes),
            // «Подходы, что не влезли…» and «Забыли остановить?…» take a different number of lines: the place of the taller one stays
            reserve = PracticeReducer.hintsOf(sheet).filter { it != hint }.map { hintText(it, sheet.floorMinutes) },
        )
        if (sheet.played.isNotEmpty()) PlayedGroup(sheet.played, Modifier.padding(top = PlayedTop))
    }
}

@Composable
private fun hintText(hint: SummaryHint, floorMinutes: Int): String = when (hint) {
    SummaryHint.Forgot -> stringResource(Res.string.practice_summary_hint)
    SummaryHint.Cut -> stringResource(Res.string.practice_summary_hint_cut)
    SummaryHint.TooShort -> stringResource(Res.string.practice_summary_hint_short, floorMinutes)
}

/**
 * The buttons of «Закончить занятие» (spec 3.36.3): «Сохранить» with the tick — «Сохранить 37 мин» while the number is below the one
 * it opened with, exactly what goes into the calendar — and «Не сохранять» said quietly under it, not in red.
 */
@Composable
fun SummaryButtons(sheet: PracticeSheet.Summary, onSave: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    val length = PracticeReducer.saveLengthMs(sheet)
    AppSheetButtons(
        main = if (length == null) stringResource(Res.string.practice_save) else stringResource(Res.string.practice_save_length, Formats.minutesInWords(length)),
        onMain = onSave,
        modifier = modifier,
        mainIcon = AppIcons.Check,
        quiet = stringResource(Res.string.practice_discard),
        onQuiet = onDiscard,
    )
}

/**
 * «Что играли» (spec 3.28, 3.36.3): the blocks of the practice in the order they were played, a row each — the note, the element and
 * on the right its time: «15 мин ✓» in the brass of «сделано», «7 из 15 мин», or «— не вошёл» for one the stepper cut off. No title
 * over the group; TalkBack names it «Что играли» and reads each row as one sentence. Four rows in sight, the rest scroll inside it.
 */
@Composable
private fun PlayedGroup(played: List<PlayedLine>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val groupName = stringResource(Res.string.block_played_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AppShapes.M)
            .padding(horizontal = GroupPaddingHorizontal, vertical = GroupPaddingVertical)
            .heightIn(max = PlayedRowMinHeight * PLAYED_ROWS_IN_SIGHT + DividerThickness * (PLAYED_ROWS_IN_SIGHT - 1))
            .verticalScroll(rememberScrollState())
            .semantics {
                contentDescription = groupName
                collectionInfo = CollectionInfo(rowCount = played.size, columnCount = 1)
            },
    ) {
        played.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider(thickness = DividerThickness, color = colors.outlineVariant)
            PlayedRow(line, index)
        }
    }
}

@Composable
private fun PlayedRow(line: PlayedLine, index: Int) {
    val colors = MaterialTheme.colorScheme
    val done = ViolinTheme.done
    val goal = Formats.minutesInWords(line.goalMinutes * MS_PER_MINUTE)
    val part = stringResource(Res.string.block_part_of, line.minutes, line.goalMinutes)
    val description = when {
        line.dropped -> stringResource(Res.string.practice_played_dropped_description, line.title)
        line.done -> stringResource(Res.string.practice_played_done_description, line.title, goal)
        else -> stringResource(Res.string.practice_pair_description, line.title, part)
    }
    val words = MaterialTheme.typography.bodyMedium.copy(fontSize = PLAYED_SIZE.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_FIGURES)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PlayedRowMinHeight)
            .clearAndSetSemantics {
                contentDescription = description
                collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlayedRowGap),
    ) {
        AppIcon(AppIcons.NoteOne, contentDescription = null, size = PlayedIcon, tint = colors.onSurfaceVariant)
        Text(
            text = line.title,
            modifier = Modifier.weight(1f),
            color = if (line.dropped) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = words.copy(fontWeight = FontWeight.Medium),
        )
        when {
            line.dropped -> Text(stringResource(Res.string.practice_played_dropped), color = colors.onSurfaceVariant, maxLines = 1, style = words.copy(fontWeight = FontWeight.Bold))
            line.done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PlayedTickGap)) {
                Text(goal, color = done, maxLines = 1, style = words.copy(fontWeight = FontWeight.ExtraBold))
                AppIcon(AppIcons.Check, contentDescription = null, size = PlayedTick, tint = done)
            }
            else -> Text(part, color = colors.onSurfaceVariant, maxLines = 1, style = words.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * «Время за день» of a day (spec 3.36.3; practice-sheets.html 4): the label, the date, the stepper — «было 1 ч 15 мин» under the
 * number once it moved, «занятие без телефона» for an empty day — the chips «+10 · +30 · +1 ч · 0» and the hint. Its buttons are
 * [EditTimeButtons], at the bottom of the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTimeSheetContent(sheet: PracticeSheet.EditTime, stepMinutes: Int, onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.practice_edit_title)
    val value = Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE)
    val empty = sheet.dayTotalMs == 0L
    val wasText = stringResource(Res.string.practice_day_was, Formats.minutesInWords(sheet.dayTotalMs))
    val noPhone = stringResource(Res.string.practice_day_no_phone)
    val caption = when (val shown = PracticeReducer.dayCaptionOf(sheet)) {
        DayCaption.None -> null
        DayCaption.NoPhone -> noPhone
        is DayCaption.Was -> stringResource(Res.string.practice_day_was, Formats.minutesInWords(shown.ms))
    }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        SectionLabel(title)
        Text(
            text = Formats.dayWithWeekday(sheet.date),
            modifier = Modifier.padding(top = DateTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = DATE_SIZE.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        )
        Stepper(
            value = value,
            onStep = { onIntent(PracticeIntent.EditTimeStepped(it)) },
            canStepDown = sheet.minutes > 0,
            canStepUp = sheet.minutes < sheet.maxMinutes,
            downDescription = stringResource(Res.string.practice_step_down, stepMinutes),
            upDescription = stringResource(Res.string.practice_step_up, stepMinutes),
            modifier = Modifier.padding(top = StepperTop, bottom = StepperBottom),
            caption = caption,
            captionReserve = if (empty) noPhone else wasText,
            valueDescription = caption?.let { stringResource(Res.string.practice_pair_description, value, it) } ?: value,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = ChipsTop),
            horizontalArrangement = Arrangement.spacedBy(ChipGap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(ChipGap),
        ) {
            Chip(stringResource(Res.string.practice_chip_plus_minutes, CHIP_TEN)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_TEN)) }
            Chip(stringResource(Res.string.practice_chip_plus_minutes, CHIP_THIRTY)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_THIRTY)) }
            Chip(stringResource(Res.string.practice_chip_plus_hour)) { onIntent(PracticeIntent.EditTimeAdded(CHIP_HOUR)) }
            Chip(stringResource(Res.string.practice_chip_zero)) { onIntent(PracticeIntent.EditTimeCleared) }
        }
        Hint(stringResource(if (empty) Res.string.practice_edit_hint_empty else Res.string.practice_edit_hint))
    }
}

/** A chip of choice of R1 in the sheet: an action, no state of choice; not narrower than 60 (5.29 R3). */
@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    AppChip.Choice(text = text, selected = null, onClick = onClick, modifier = Modifier.widthIn(min = ChipMinWidth), inSheet = true)
}

/**
 * The buttons of «Время за день» (spec 3.36.3): a day with time — «Сохранить», always pressed: at zero it removes the day, unchanged
 * it rewrites nothing; an empty day — «Добавить 30 мин» with the number, and at zero «Добавить» dimmed with its reason above it (the
 * place of the reason stays at any number). «Отмена» said quietly under it. Each gives the sheet of the day back.
 */
@Composable
fun EditTimeButtons(sheet: PracticeSheet.EditTime, onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    val empty = sheet.dayTotalMs == 0L
    val nothing = sheet.minutes == 0
    val reason = stringResource(Res.string.practice_day_add_reason)
    AppSheetButtons(
        main = when {
            !empty -> stringResource(Res.string.practice_save)
            nothing -> stringResource(Res.string.practice_day_add)
            else -> stringResource(Res.string.practice_day_add_length, Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE))
        },
        onMain = { onIntent(PracticeIntent.EditTimeSaved) },
        modifier = modifier,
        mainEnabled = !(empty && nothing),
        mainReason = if (empty && nothing) reason else null,
        // the reason comes at zero and goes at 5 min: its place stays, and the stepper and the chips above do not jump
        mainReasonReserve = if (empty) reason else null,
        quiet = stringResource(Res.string.dialog_cancel),
        onQuiet = { onIntent(PracticeIntent.EditTimeCancelled) },
    )
}

/**
 * The hint under the stepper and the chips: 13 sp in the second level of text, in the middle. [reserve] — every hint that can stand
 * here as the stepper moves, drawn unseen and unheard: the place of the tallest stays, and a hint that changes under a finger holding
 * «−» moves nothing above it (the sheet stands on the bottom of the screen and grows upwards).
 */
@Composable
private fun Hint(text: String, reserve: List<String> = emptyList()) {
    Box(Modifier.fillMaxWidth().padding(top = HintTop)) {
        reserve.forEach { HintText(it, Modifier.alpha(0f).clearAndSetSemantics {}) }
        HintText(text)
    }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = HINT_SIZE.sp, lineHeight = HINT_LINE_HEIGHT.em),
    )
}

// Until stage 107 — the gift, the recap and «Имя и фото» keep their windows and these parts of the old look (spec 3.36.3, R3 plan).
private val SheetPadding = 24.dp
private val SheetBottom = 32.dp
private val PrimaryButtonHeight = 56.dp
private val PrimaryCorner = 28.dp

/**
 * The column of a sheet of «Занятия» that is not in the frame of R3 yet — the gift, the recap, «Профиль», «Трофеи» (until stage 107).
 * Scrolls when the window is lower than the sheet — landscape, a large font: its buttons are the only answers to it (spec 3.12).
 * Nothing inside may scroll the same way without a bound.
 */
@Composable
fun SheetColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = SheetPadding, end = SheetPadding, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        content()
    }
}

/** The main button of those sheets, until stage 107 moves them to [AppSheetButtons]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
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
