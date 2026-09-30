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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.appButtonBeside
import com.violinjourney.app.core.ui.components.appButtonOneLineSize
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.live.components.LiveMotion
import com.violinjourney.app.feature.practice.components.StartPracticeButton
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_empty
import com.violinjourney.app.shared.resources.block_goal_minus
import com.violinjourney.app.shared.resources.block_goal_option
import com.violinjourney.app.shared.resources.block_goal_plus
import com.violinjourney.app.shared.resources.block_goal_range
import com.violinjourney.app.shared.resources.block_goal_title
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_line
import com.violinjourney.app.shared.resources.block_now
import com.violinjourney.app.shared.resources.block_now_label
import com.violinjourney.app.shared.resources.block_offer_later
import com.violinjourney.app.shared.resources.block_offer_text
import com.violinjourney.app.shared.resources.block_offer_title
import com.violinjourney.app.shared.resources.block_open_repertoire
import com.violinjourney.app.shared.resources.block_selected
import com.violinjourney.app.shared.resources.block_sheet_title
import com.violinjourney.app.shared.resources.block_start
import com.violinjourney.app.shared.resources.block_stop
import com.violinjourney.app.shared.resources.practice_chip_label
import com.violinjourney.app.shared.resources.practice_step_down
import com.violinjourney.app.shared.resources.practice_step_up
import org.jetbrains.compose.resources.stringResource

/**
 * What the frame of the sheet is given: the face alone — the offer or the choice — never the choice with its clock. The choice changes
 * every second (the time of the practice, the minutes of the block), and to [AppSheet] a value that changes while a swiped frame
 * slides down is a new value that brings the frame up again once it is down — unless its owner has dropped the sheet by then. The
 * view model of Live drops it in the same frame; an owner that answers a frame later (a state that comes through a flow on its own
 * frame) would have the frame bounce. The face stays the same through the ticks.
 */
private enum class BlockFace { OFFER, PICKER }

/**
 * The sheets of blocks on Live (spec 3.28, 3.36.6): «Сначала — занятие» without a practice, «Что играем» with one — on the frame of
 * R1 ([AppSheet]: the card colour, a corner of 28, the handle, the scrim; never wider than 640, in landscape too, in the middle). A
 * swipe, «назад» and a tap beside it only hide it: the running block and its goal stay as they were, nothing starts; the choice is
 * made anew the next time. One frame for both: when the practice starts from the offer, its content cross-fades into the choice and
 * the sheet grows (240 / 320 ms) — it is not closed and opened again. The frame itself does not scroll: the choice scrolls its list —
 * the header and «Сейчас» with it — over the goal pinned at its bottom, the offer scrolls on its own. [calm] — a note sounds or a take
 * records: the living «Начать занятие» of the offer stands still then (3.36.6).
 */
@Composable
fun BlockSheetHost(
    sheet: BlockSheet?,
    landscape: Boolean,
    onIntent: (BlockIntent) -> Unit,
    calm: () -> Boolean,
    reduceMotion: Boolean = false,
) {
    // the last choice: a practice ended under the open choice turns it back into the offer, and the choice fades out as it was
    var lastPicker by remember { mutableStateOf<BlockSheet.Picker?>(null) }
    if (sheet is BlockSheet.Picker) SideEffect { lastPicker = sheet }
    val face = when (sheet) {
        null -> null
        BlockSheet.Offer -> BlockFace.OFFER
        is BlockSheet.Picker -> BlockFace.PICKER
    }
    AppSheet(
        value = face,
        // a swipe, a tap beside it, «назад»: only hidden — nothing starts, nothing stops
        onHide = { onIntent(BlockIntent.SheetDismissed) },
        // «Не сейчас», «Начать», «Открыть репертуар» drop the sheet in the model: it slides away as from a swipe
        slideAway = !reduceMotion,
        scroll = { false },
        contentPadding = NoPadding,
        // the two constants of one enum are one class: the face is the value itself
        faceOf = { it },
    ) { shown ->
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val swap = if (reduceMotion) 0 else LiveMotion.BLOCK_SHEET_SWAP_MS
                fadeIn(tween(swap)) togetherWith fadeOut(tween(swap)) using
                    SizeTransform { _, _ -> tween(if (reduceMotion) 0 else LiveMotion.BLOCK_SHEET_GROW_MS, easing = EaseInOutCubic) }
            },
            label = "blockSheet",
        ) { current ->
            when (current) {
                BlockFace.OFFER -> OfferContent(onIntent, calm)
                BlockFace.PICKER -> ((sheet as? BlockSheet.Picker) ?: lastPicker)?.let { picker ->
                    if (landscape) PickerLandscape(picker, onIntent) else PickerPortrait(picker, onIntent)
                }
            }
        }
    }
}

/**
 * «Сначала — занятие» (spec 3.28, 3.36.6): the title, the text, the living «Начать занятие» of «Занятия» (3.16, 5.10) — its lights
 * stand still while [calm] — and «Не сейчас» as a quiet line. «Начать занятие» starts the practice here: Live stays, and the sheet
 * turns into the choice. In landscape the same one column, as wide as the sheet. Higher than the sheet — a large font in a low window
 * — it scrolls: its buttons keep their 56 and 48, never squeezed by what is above them.
 */
@Composable
fun OfferContent(onIntent: (BlockIntent) -> Unit, calm: () -> Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val button = currentDockMetrics().button
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = SheetSide, end = SheetSide, bottom = SheetBottom),
    ) {
        Text(
            text = stringResource(Res.string.block_offer_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = titleStyle(),
        )
        Text(
            text = stringResource(Res.string.block_offer_text),
            modifier = Modifier.padding(top = OfferTextTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
        StartPracticeButton(
            onClick = { onIntent(BlockIntent.StartPracticeClicked) },
            calm = calm,
            modifier = Modifier
                .padding(top = OfferButtonTop)
                .fillMaxWidth()
                .height(button),
        )
        AppButton(
            text = stringResource(Res.string.block_offer_later),
            onClick = { onIntent(BlockIntent.NotNowClicked) },
            modifier = Modifier
                .padding(top = OfferLaterTop)
                .fillMaxWidth(),
            style = AppButtonStyle.Quiet,
        )
    }
}

/**
 * «Что играем» upright (spec 3.36.6; live.html, 4): the list — the header and «Сейчас» at its top, going with it as it scrolls — and,
 * pinned under it, the goal that comes with a choice. The goal never takes the whole sheet: the list keeps [ListLeast] above it, and
 * a goal taller than what that leaves scrolls its title and chips above «Начать». The sheet stands 104 dp below the top of the base
 * screen (handoff `sizes`): Live's switcher and tag stay in sight.
 */
@Composable
fun PickerPortrait(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(PICKER_HEIGHT),
    ) {
        val goalMost = if (constraints.hasBoundedHeight) (maxHeight - ListLeast).coerceAtLeast(0.dp) else Dp.Infinity
        Column(Modifier.fillMaxSize()) {
            ChoiceBody(picker, onIntent, Modifier.weight(1f))
            // the goal comes with the choice: before it there is no panel at all, so no button is ever greyed out
            AnimatedVisibility(visible = picker.selectedId != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                GoalPanel(picker, onIntent, Modifier.heightIn(max = goalMost))
            }
        }
    }
}

/**
 * «Что играем» lying down (spec 3.36.6; live.html, 5): the list at the left, the header and «Сейчас» at its top; at the right a column
 * of 300 comes with a choice — «Выбрано» and its name, «Сколько играть · от 5 до 60 мин», the chips four in a row and «Начать · N мин»
 * at the bottom of the column. The sheet is not wider than 640: the list keeps about 300.
 */
@Composable
fun PickerLandscape(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        ChoiceBody(
            picker,
            onIntent,
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        AnimatedVisibility(visible = picker.selectedId != null, enter = fadeIn(), exit = fadeOut()) {
            Row {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GoalColumn(picker, onIntent)
            }
        }
    }
}

/** The list of the choice, or the words of an empty repertoire — each with the header and «Сейчас» over it, scrolling with it. */
@Composable
private fun ChoiceBody(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    if (picker.sections.isEmpty()) EmptyRepertoire(picker, onIntent, modifier) else ChoiceList(picker, onIntent, modifier)
}

/** The header, and under it «Сейчас» while a block runs; [side] — from the sides of what holds them to their words. */
@Composable
private fun ChoiceTop(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, side: Dp) {
    ChoiceHeader(picker.practiceMs, Modifier.padding(start = side, end = side, bottom = HeaderBottom))
    picker.now?.let { NowCard(it, picker.goalMaxMinutes, onIntent, Modifier.padding(start = side, end = side, bottom = NowBottom)) }
}

/**
 * «Что играем» — a heading, 22 sp / 800 — and at its right, quietly, on its baseline, «занятие 24:18»: the time of the practice tag,
 * running with it (spec 3.36.6). The title goes on two lines at a space before the time gives way.
 */
@Composable
private fun ChoiceHeader(practiceMs: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val time = stringResource(Res.string.practice_chip_label) + " " + Formats.timer(practiceMs)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HeaderGap)) {
        Text(
            text = stringResource(Res.string.block_sheet_title),
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .semantics { heading() },
            color = colors.onSurface,
            style = titleStyle(),
        )
        Text(
            text = time,
            modifier = Modifier.alignByBaseline(),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * «Сейчас» (spec 3.36.6, 5.29 R6): a card on the ground of the screen, the label over «D-dur · 2 октавы · ещё 7 мин» — the name with an
 * ellipsis, the minutes always whole — and at its right «Остановить», the outline of 48 (the low variant R1 gives the bottom zone of a
 * low window: 56 would be taller than the two lines of the card). The button leaves the lines their widest word — the label, or a word
 * of the minutes at their widest ([widestMinutes], the most a goal can be: its two digits); where at its size it does not, its words
 * step down to [STOP_LEAST_SP], and where not even then, it stands under the lines, as wide as the card ([rememberNowPlan]). TalkBack:
 * «Сейчас: D-dur · 2 октавы · ещё 7 мин», then the button.
 */
@Composable
private fun NowCard(now: NowLine, widestMinutes: Int, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val left = minutesLeft(now.minutesLeft)
    val widest = minutesLeft(maxOf(widestMinutes, now.minutesLeft))
    val whole = stringResource(Res.string.block_line, now.title, left)
    // the words of the line after the name, as the language joins them: «· ещё 7 мин»
    val tail = stringResource(Res.string.block_line, "", left).trimStart()
    val said = stringResource(Res.string.block_now, whole)
    val label = stringResource(Res.string.block_now_label)
    val stop = stringResource(Res.string.block_stop)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val lineStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AppShapes.M)
            .padding(horizontal = NowPaddingSide, vertical = NowPaddingVertical),
    ) {
        val plan = rememberNowPlan(maxWidth, label, labelStyle, widest, tail, lineStyle, stop)
        val onStop = { onIntent(BlockIntent.StopClicked) }
        if (plan.beside) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NowGap)) {
                NowWords(label, labelStyle, now.title, if (plan.oneLine) tail else left, plan.oneLine, lineStyle, said, Modifier.weight(1f))
                AppButton(text = stop, onClick = onStop, style = AppButtonStyle.Outline, compact = true, fontSize = plan.stopSize)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NowGap)) {
                NowWords(label, labelStyle, now.title, if (plan.oneLine) tail else left, plan.oneLine, lineStyle, said, Modifier.fillMaxWidth())
                AppButton(text = stop, onClick = onStop, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Outline, compact = true, fontSize = plan.stopSize)
            }
        }
    }
}

/** «ещё 7 мин» in the language's form for [minutes]. */
@Composable
private fun minutesLeft(minutes: Int): String =
    stringResource(Formats.plural(minutes, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), minutes)

/** Where «Остановить» stands in the card «Сейчас» and how large its words are; [oneLine] — the name and the minutes share a line. */
private data class NowPlan(val beside: Boolean, val stopSize: TextUnit, val oneLine: Boolean)

/**
 * The plan of the card «Сейчас» [width] wide inside its fields (spec 5.29 R6): «Остановить» beside the lines at the largest size, down
 * to [STOP_LEAST_SP], at which it leaves them their widest word — the [label] or a word of the minutes at their [widest] (a number and
 * its unit are one word); else under them. The name and the minutes share a line where the name keeps [NowTitleLeast] beside the
 * [tail]; else the name has the first line and the minutes the second — without the «·» that joins them on one line.
 */
@Composable
private fun rememberNowPlan(width: Dp, label: String, labelStyle: TextStyle, widest: String, tail: String, lineStyle: TextStyle, stop: String): NowPlan {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (least, tailWidth) = remember(label, labelStyle, widest, tail, lineStyle, measurer, density) {
        fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val words = widest.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { widthOf(it, lineStyle) } ?: 0
        with(density) { maxOf(widthOf(label, labelStyle), words).toDp() + WordSlack to widthOf(tail, lineStyle).toDp() + WordSlack }
    }
    val beside = appButtonBeside(stop, width - NowGap - least, AppButtonStyle.Outline, compact = true, minSp = STOP_LEAST_SP)
    val under = appButtonOneLineSize(stop, width, AppButtonStyle.Outline, compact = true, before = 0.dp, minSp = STOP_LEAST_SP)
    val lines = if (beside != null) width - NowGap - beside.width else width
    return NowPlan(beside = beside != null, stopSize = beside?.fontSize ?: under, oneLine = tailWidth + NowTailGap + NowTitleLeast <= lines)
}

/**
 * The label over the line — TalkBack hears them as one phrase, [said], and the button after it. The line: the name with an ellipsis
 * and, [oneLine], the [minutes] after it on the same line; otherwise the name alone and the minutes under it, wrapping at a space only
 * (the plan leaves each of their words its room).
 */
@Composable
private fun NowWords(
    label: String,
    labelStyle: TextStyle,
    title: String,
    minutes: String,
    oneLine: Boolean,
    lineStyle: TextStyle,
    said: String,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = said }) {
        Text(text = label, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle)
        if (oneLine) {
            Row {
                Text(title, Modifier.weight(1f, fill = false), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = lineStyle)
                Spacer(Modifier.width(NowTailGap))
                Text(minutes, color = colors.onSurface, maxLines = 1, softWrap = false, style = lineStyle)
            }
        } else {
            Text(title, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = lineStyle)
            Text(minutes, color = colors.onSurface, style = lineStyle)
        }
    }
}

/**
 * The list of the repertoire ([pickerSections]) under the header and «Сейчас», all of it scrolling together: its rows 8 from the sides
 * of the sheet, their words 20 in, under the labels — and the header and the card as far in.
 */
@Composable
private fun ChoiceList(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(start = ListSide, end = ListSide, bottom = ListBottom)) {
        item(key = HEADER_KEY, contentType = HEADER_KEY) {
            ChoiceHeader(picker.practiceMs, Modifier.padding(start = TopInList, end = TopInList, bottom = HeaderBottom))
        }
        picker.now?.let { now ->
            item(key = NOW_KEY, contentType = NOW_KEY) {
                NowCard(now, picker.goalMaxMinutes, onIntent, Modifier.padding(start = TopInList, end = TopInList, bottom = NowBottom))
            }
        }
        pickerSections(
            sections = picker.sections,
            isSelected = { it.id == picker.selectedId },
            onPick = { onIntent(BlockIntent.PieceClicked(it.id)) },
        )
    }
}

/**
 * The repertoire is empty (spec 3.28, 3.36.6): the header and «Сейчас», the words in the middle of what they and the button leave —
 * a large font in a low window scrolls them all — and «Открыть репертуар», the main button pinned at the bottom of the sheet, to the
 * tab «Репертуар» (R1); a take that runs is saved as Live is left (3.9).
 */
@Composable
private fun EmptyRepertoire(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            TopThenCentred(
                viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
                top = { ChoiceTop(picker, onIntent, side = SheetSide) },
                body = {
                    Text(
                        text = stringResource(Res.string.block_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EmptySide, vertical = EmptyVertical),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
        }
        AppSheetButtons(
            main = stringResource(Res.string.block_open_repertoire),
            onMain = { onIntent(BlockIntent.OpenRepertoireClicked) },
            modifier = Modifier.padding(start = SheetSide, end = SheetSide, bottom = SheetBottom),
            top = EmptyButtonTop,
        )
    }
}

/**
 * [top] and under it [body] in the middle of what [top] leaves of a [viewport] (pixels) — or right under it, all of it taller than
 * the viewport and scrolling ([modifier] scrolls it).
 */
@Composable
private fun TopThenCentred(viewport: Int, top: @Composable () -> Unit, body: @Composable () -> Unit, modifier: Modifier) {
    Layout(contents = listOf(top, body), modifier = modifier) { (tops, bodies), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val topPlaced = tops.map { it.measure(loose) }
        val bodyPlaced = bodies.map { it.measure(loose) }
        val topHeight = topPlaced.sumOf { it.height }
        val bodyHeight = bodyPlaced.sumOf { it.height }
        val room = maxOf(bodyHeight, viewport - topHeight)
        val width = (topPlaced + bodyPlaced).maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
        layout(width, topHeight + room) {
            var y = 0
            topPlaced.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
            y = topHeight + (room - bodyHeight) / 2
            bodyPlaced.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

/**
 * The goal, pinned at the bottom of the sheet upright (spec 3.36.6, 5.29 R6): a line of 1 over it; «Сколько играть» and at the right
 * «от 5 до 60 мин», the chips — in one row where each gets 48, else in two ([GoalRows]) — and «Начать · 15 мин», the main button with
 * the number in it. 12 / 20 of padding; the system inset under it is the sheet's own. Where the sheet leaves it less than it needs
 * (the caller's [modifier] bounds it), the title and the chips scroll above «Начать», which keeps its height.
 */
@Composable
private fun GoalPanel(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer),
    ) {
        HorizontalDivider(thickness = GoalLine, color = colors.outlineVariant)
        Column(Modifier.padding(horizontal = SheetSide, vertical = GoalPaddingVertical)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                GoalTitle(picker, inLine = false)
                GoalChips(picker, onIntent, Modifier.padding(top = GoalChipsTop))
            }
            StartButton(picker, onIntent)
        }
    }
}

/** The column of the goal lying down (spec 3.36.6): what is chosen and the goal scroll above «Начать · N мин» at its bottom. */
@Composable
private fun GoalColumn(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(LandscapeGoalWidth)
            .fillMaxHeight()
            .padding(start = SheetSide, end = SheetSide, top = GoalColumnTop, bottom = SheetBottom),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(Res.string.block_selected),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = picker.selectedTitle.orEmpty(),
                modifier = Modifier.padding(top = SelectedTitleTop),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            )
            GoalTitle(picker, inLine = true, modifier = Modifier.padding(top = GoalColumnGap))
            GoalChips(picker, onIntent, Modifier.padding(top = GoalChipsTop))
        }
        StartButton(picker, onIntent)
    }
}

/**
 * «Сколько играть» and «от 5 до 60 мин» (the bounds of 5.21, from the config), 13 sp / 600: at the two ends of the row upright,
 * [inLine] — as one line with «·» in the column lying down. For TalkBack one text.
 */
@Composable
private fun GoalTitle(picker: BlockSheet.Picker, inLine: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.block_goal_title)
    val range = stringResource(Res.string.block_goal_range, picker.goalMinMinutes, picker.goalMaxMinutes)
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    if (inLine) {
        Text(stringResource(Res.string.block_line, title, range), modifier, color = colors.onSurfaceVariant, style = style)
    } else {
        Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, Modifier.weight(1f, fill = false), color = colors.onSurfaceVariant, style = style)
            Spacer(Modifier.width(HeaderGap))
            Text(range, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
        }
    }
}

/**
 * The chips of the goal (spec 3.36.6, 5.29 R6): the chips of choice of R1 in the sheet — «−5» and «+5» are actions, the quick goals
 * are chosen — in one row where each gets its 48 (its gaps giving way from 6 to 4), else in two, 6 apart ([GoalRows]). The rows stand
 * 2 apart in the code: a chip of 44 is pressed over 48, and the eye sees 6 between them.
 */
@Composable
private fun GoalChips(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier = Modifier) {
    val cells = buildList {
        add(GoalCell.Step(-1))
        picker.quickGoals.forEach { add(GoalCell.Quick(it)) }
        add(GoalCell.Step(+1))
    }
    val minus = stringResource(Res.string.block_goal_minus, picker.goalStep)
    val plus = stringResource(Res.string.block_goal_plus, picker.goalStep)
    val words = cells.map { cell ->
        when (cell) {
            is GoalCell.Step -> if (cell.steps > 0) plus else minus
            is GoalCell.Quick -> cell.minutes.toString()
        }
    }
    // a chip never narrower than its words: at a large font «30» needs more than 48, and one row asks for more than 360
    val chip = AppChip.choiceWidthFor(words)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rows = GoalRows.of(maxWidth.value, cells.size, chip.value)
        // One row: the chips at its ends and what they leave between them — the gaps of GoalRows, without a pixel of each rounded up and
        // added up past the edge. Two rows: 6 apart from the start, the second under the first three.
        val across = if (rows.perRow >= cells.size) Arrangement.SpaceBetween else Arrangement.spacedBy(rows.gapDp.dp)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(GoalRowGap)) {
            cells.indices.chunked(rows.perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = across) {
                    row.forEach { index -> GoalChip(cells[index], words[index], picker, onIntent, Modifier.width(rows.cellDp.dp)) }
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
private fun GoalChip(cell: GoalCell, text: String, picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit, modifier: Modifier) {
    when (cell) {
        is GoalCell.Step -> {
            val up = cell.steps > 0
            val said = stringResource(if (up) Res.string.practice_step_up else Res.string.practice_step_down, picker.goalStep)
            AppChip.Choice(
                text = text,
                selected = null,
                onClick = { onIntent(BlockIntent.GoalStepped(cell.steps)) },
                modifier = modifier.semantics { contentDescription = said },
                inSheet = true,
                enabled = if (up) picker.canGoalUp else picker.canGoalDown,
            )
        }
        is GoalCell.Quick -> {
            val said = stringResource(Res.string.block_goal_option, cell.minutes)
            AppChip.Choice(
                text = text,
                selected = cell.minutes == picker.goalMinutes,
                onClick = { onIntent(BlockIntent.GoalPicked(cell.minutes)) },
                modifier = modifier.semantics { contentDescription = said },
                inSheet = true,
            )
        }
    }
}

/** «Начать · 15 мин» — the main button with the number in it, 12 over it; 48 in a window no higher than 360, as a bottom zone's. */
@Composable
private fun StartButton(picker: BlockSheet.Picker, onIntent: (BlockIntent) -> Unit) {
    AppSheetButtons(
        main = stringResource(Res.string.block_start, picker.goalMinutes),
        onMain = { onIntent(BlockIntent.StartClicked) },
        top = StartTop,
    )
}

@Composable
private fun titleStyle() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)

// The sheet «Что играем» (spec 5.29 R6; live.html, 4): its sides, its parts and the room between them.
private val NoPadding = PaddingValues(0.dp)
private val SheetSide = 20.dp
private val SheetBottom = 16.dp
private val HeaderGap = 12.dp
private val HeaderBottom = 6.dp
private val NowBottom = 4.dp
private val NowPaddingSide = 14.dp
private val NowPaddingVertical = 12.dp

/** Between the lines of «Сейчас» and «Остановить», beside them or under them. */
private val NowGap = 10.dp
private val NowTailGap = 4.dp

/** The name keeps at least this beside the minutes — a few letters and «…» — or it takes a line of its own. */
private val NowTitleLeast = 64.dp

/** Words are laid out in whole pixels: a word that fits only by a hair is not trusted. */
private val WordSlack = 1.dp

/** The rows reach 12 past their words: their words stand 20 in, under the labels of the sections. */
private val ListSide = 8.dp

/** The header and «Сейчас» in the list: 20 in from the sides of the sheet, as their words were over it. */
private val TopInList = SheetSide - ListSide
private val ListBottom = 12.dp

/** The least the list keeps over the goal upright: one row with its composer. */
private val ListLeast = 64.dp
private val EmptySide = 40.dp
private val EmptyVertical = 16.dp
private val EmptyButtonTop = 12.dp
private val OfferTextTop = 8.dp
private val OfferButtonTop = 20.dp
private val OfferLaterTop = 6.dp
private val GoalLine = 1.dp
private val GoalPaddingVertical = 12.dp
private val GoalChipsTop = 8.dp
private val GoalRowGap = 2.dp
private val StartTop = 12.dp
private val LandscapeGoalWidth = 300.dp
private val GoalColumnTop = 4.dp
private val GoalColumnGap = 14.dp
private val SelectedTitleTop = 3.dp

/** 788 of 892: the top of the sheet 104 dp below the top of the base screen (handoff `sizes`). */
private const val PICKER_HEIGHT = 788f / 892f

/** «Остановить» steps its words down no smaller than the label «Сейчас» beside it (13 sp): smaller, it would read as a caption. */
private const val STOP_LEAST_SP = 13f
private const val TABULAR_FIGURES = "tnum"
private const val HEADER_KEY = "header"
private const val NOW_KEY = "now"
