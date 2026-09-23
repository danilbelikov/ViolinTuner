package com.violinjourney.app.feature.live.block

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.feature.live.components.LiveMotion
import com.violinjourney.app.feature.repertoire.sections.sectionName

/**
 * The sheets of blocks on Live (spec 3.28, handoff 30e, 30f): «Сначала — занятие» without a practice,
 * «Что играем» with one. One sheet: when the practice starts from the offer, its content cross-fades
 * into the choice and the sheet grows — it is not closed and opened again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheetHost(sheet: BlockSheet?, landscape: Boolean, onIntent: (BlockIntent) -> Unit, reduceMotion: Boolean = false) {
    if (sheet == null) return
    // a practice ended under the open choice turns it back into the offer: the choice fades out as it was
    var lastPicker by remember { mutableStateOf<BlockSheet.Picker?>(null) }
    if (sheet is BlockSheet.Picker) SideEffect { lastPicker = sheet }
    ModalBottomSheet(
        onDismissRequest = { onIntent(BlockIntent.SheetDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // in landscape the list and the goal stand side by side over the whole width (handoff 30e5)
        sheetMaxWidth = if (landscape) Dp.Unspecified else SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = sheet is BlockSheet.Offer,
            transitionSpec = {
                val swap = if (reduceMotion) 0 else LiveMotion.BLOCK_SHEET_SWAP_MS
                fadeIn(tween(swap)) togetherWith fadeOut(tween(swap)) using
                    SizeTransform { _, _ -> tween(if (reduceMotion) 0 else LiveMotion.BLOCK_SHEET_GROW_MS, easing = EaseInOutCubic) }
            },
            label = "blockSheet",
        ) { offer ->
            if (offer) {
                OfferContent(onIntent)
            } else {
                ((sheet as? BlockSheet.Picker) ?: lastPicker)?.let { picker ->
                    if (landscape) PickerLandscape(picker, onIntent) else PickerPortrait(picker, onIntent)
                }
            }
        }
    }
}

@Composable
internal fun OfferContent(onIntent: (BlockIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.block_offer_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
        )
        Text(
            text = stringResource(R.string.block_offer_text),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { onIntent(BlockIntent.StartPracticeClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PrimaryHeight),
                shape = RoundedCornerShape(PrimaryHeight / 2),
            ) {
                IconLabel(
                    icon = AppIcons.Timer,
                    text = stringResource(R.string.practice_start),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    iconSize = 20.dp,
                )
            }
            TextButton(
                onClick = { onIntent(BlockIntent.NotNowClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.block_offer_later), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
internal fun PickerPortrait(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    // the sheet stands 104 dp below the top of the base screen (handoff `sizes`): Live's switcher and tag stay in sight
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(PICKER_HEIGHT),
    ) {
        Title()
        picker.now?.let { NowCard(it, onIntent, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) }
        if (picker.sections.isEmpty()) {
            EmptyRepertoire(onIntent, Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                pickerItems(picker, onIntent)
            }
        }
        // the goal comes with the choice: before it there is no panel at all, so no button is ever greyed out
        AnimatedVisibility(visible = picker.selectedId != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            GoalPanel(picker, onIntent)
        }
    }
}

@Composable
internal fun PickerLandscape(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
        Column(Modifier.weight(1f)) {
            Title()
            picker.now?.let { NowCard(it, onIntent, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) }
            if (picker.sections.isEmpty()) {
                EmptyRepertoire(onIntent, Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 18.dp, bottom = 12.dp)) {
                    pickerItems(picker, onIntent)
                }
            }
        }
        AnimatedVisibility(visible = picker.selectedId != null, enter = fadeIn(), exit = fadeOut()) {
            Row {
                VerticalDivider(color = colors.outlineVariant)
                Column(
                    modifier = Modifier
                        .width(LandscapeGoalWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column {
                        Text(stringResource(R.string.block_selected), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp))
                        Text(
                            text = picker.selectedTitle.orEmpty(),
                            modifier = Modifier.padding(top = 3.dp),
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    GoalCells(picker, onIntent, columns = LANDSCAPE_GOAL_COLUMNS)
                    Spacer(Modifier.weight(1f))
                    StartButton(picker, onIntent)
                }
            }
        }
    }
}

@Composable
private fun Title() {
    Text(
        text = stringResource(R.string.block_sheet_title),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    )
}

/** «Сейчас: Концерт ля минор · ещё 7 мин» and «Остановить» (handoff 30e1). */
@Composable
private fun NowCard(now: NowLine, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.block_now, now.title),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(Formats.plural(now.minutesLeft, R.string.block_left_one, R.string.block_left_few, R.string.block_left_many), now.minutesLeft),
                modifier = Modifier.padding(top = 2.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        OutlinedButton(
            onClick = { onIntent(BlockIntent.StopClicked) },
            modifier = Modifier.height(40.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.block_stop), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

private fun LazyListScope.pickerItems(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    picker.sections.forEach { section ->
        item(key = "head-" + section.ref) { SectionHead(section) }
        items(section.pieces, key = { it.id }) { piece ->
            PieceRow(piece, selected = piece.id == picker.selectedId, onClick = { onIntent(BlockIntent.PieceClicked(piece.id)) })
        }
    }
}

/** «Гаммы · сегодня 1»: the count only when it is not zero (handoff 30e). */
@Composable
private fun SectionHead(section: PickerSection) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = sectionName(section.ref, section.name),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em),
        )
        if (section.doneToday > 0) {
            Text(
                text = stringResource(R.string.block_section_today, section.doneToday),
                modifier = Modifier.graphicsLayer { alpha = SECTION_COUNT_ALPHA },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

@Composable
private fun PieceRow(piece: PickerPiece, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val running = piece.today == TodayMark.Running
    val markDescription = when (val mark = piece.today) {
        TodayMark.None -> null
        is TodayMark.Played -> stringResource(R.string.block_mark_played_description, Formats.minutesInWords(mark.ms))
        is TodayMark.Done -> stringResource(R.string.block_mark_done_description, Formats.minutesInWords(mark.ms))
        TodayMark.Running -> stringResource(R.string.block_running)
    }
    val description = listOfNotNull(piece.title, piece.composer, markDescription).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(colors.primary.copy(alpha = SELECTED_ALPHA)).border(1.dp, colors.primaryContainer, shape) else Modifier)
            // the element whose block runs cannot be picked again (spec 3.28)
            .clickable(enabled = !running, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = piece.title,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            piece.composer?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                )
            }
        }
        TodayMarkView(piece.today)
    }
}

/** Different by shape, not only by colour (principle 5): a pill with a tick, bare minutes, an outlined «идёт». */
@Composable
private fun TodayMarkView(mark: TodayMark) {
    val colors = MaterialTheme.colorScheme
    when (mark) {
        TodayMark.None -> Unit
        is TodayMark.Done -> Row(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainer)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AppIcon(AppIcons.Check, contentDescription = null, size = 15.dp, tint = colors.onSurface)
            Text(Formats.minutesInWords(mark.ms), color = colors.onSurface, style = markStyle().copy(fontWeight = FontWeight.Bold))
        }
        is TodayMark.Played -> Text(Formats.minutesInWords(mark.ms), color = colors.onSurfaceVariant, style = markStyle())
        TodayMark.Running -> Box(
            modifier = Modifier
                .height(28.dp)
                .border(1.dp, colors.primary, RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.block_running), color = colors.primary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun markStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES)

@Composable
private fun EmptyRepertoire(onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.block_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
        Button(
            onClick = { onIntent(BlockIntent.OpenRepertoireClicked) },
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) {
            Text(stringResource(R.string.block_open_repertoire), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** The sticky goal: −5 · 5 · 10 · 15 · 20 · 30 · +5 and «Начать · 15 мин» (handoff 30e2). */
@Composable
private fun GoalPanel(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh)) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GoalCells(picker, onIntent, columns = null)
            StartButton(picker, onIntent)
        }
    }
}

/**
 * The cells of the goal: a single row that shrinks its squares on a narrow screen, or a grid of [columns]
 * in the landscape column (there the handoff forgot «−5»; it is here, first, as in the row).
 */
@Composable
private fun GoalCells(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, columns: Int?) {
    val cells = buildList {
        add(GoalCell.Step(-1))
        picker.quickGoals.forEach { add(GoalCell.Quick(it)) }
        add(GoalCell.Step(+1))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = columns ?: cells.size
        val cell = min(CellSize, (maxWidth - CellGap * (perRow - 1)) / perRow)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (columns == null) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            cells.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (columns == null) CellGap else 8.dp)) {
                    row.forEach { GoalCellView(it, picker, onIntent, cell) }
                }
            }
        }
    }
}

private sealed interface GoalCell {
    data class Step(val steps: Int) : GoalCell
    data class Quick(val minutes: Int) : GoalCell
}

@Composable
private fun GoalCellView(cell: GoalCell, picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    when (cell) {
        is GoalCell.Step -> {
            val up = cell.steps > 0
            val enabled = if (up) picker.canGoalUp else picker.canGoalDown
            val description = stringResource(if (up) R.string.practice_step_up else R.string.practice_step_down, picker.goalStep)
            Box(
                modifier = Modifier
                    .size(size, CellSize)
                    .clip(shape)
                    .border(1.dp, colors.outlineVariant, shape)
                    .clickable(enabled = enabled, role = Role.Button) { onIntent(BlockIntent.GoalStepped(cell.steps)) }
                    .semantics { contentDescription = description }
                    .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (up) R.string.block_goal_plus else R.string.block_goal_minus, picker.goalStep),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
        is GoalCell.Quick -> {
            val chosen = cell.minutes == picker.goalMinutes
            val description = stringResource(R.string.block_goal_option, cell.minutes)
            Box(
                modifier = Modifier
                    .size(size, CellSize)
                    .clip(shape)
                    .then(if (chosen) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.outlineVariant, shape))
                    .clickable(role = Role.Button) { onIntent(BlockIntent.GoalPicked(cell.minutes)) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.minutes.toString(),
                    color = if (chosen) colors.onPrimary else colors.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

@Composable
private fun StartButton(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    Button(
        onClick = { onIntent(BlockIntent.StartClicked) },
        modifier = Modifier
            .fillMaxWidth()
            .height(PrimaryHeight),
        shape = RoundedCornerShape(PrimaryHeight / 2),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(
            text = stringResource(R.string.block_start, picker.goalMinutes),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

private val SheetMaxWidth = 640.dp
private val PrimaryHeight = 56.dp
private val CellSize = 48.dp
private val CellGap = 5.dp
private val LandscapeGoalWidth = 300.dp
private const val LANDSCAPE_GOAL_COLUMNS = 4
/** 788 of 892: the top of the sheet 104 dp below the top of the base screen (handoff `sizes`). */
private const val PICKER_HEIGHT = 788f / 892f
private const val SELECTED_ALPHA = 0.14f
private const val SECTION_COUNT_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"
