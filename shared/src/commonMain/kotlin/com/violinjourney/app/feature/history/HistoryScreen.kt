package com.violinjourney.app.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.TabTitle
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.DailyChart
import com.violinjourney.app.feature.history.components.DayHeader
import com.violinjourney.app.feature.history.components.PerformancesRow
import com.violinjourney.app.feature.history.components.RecordTile
import com.violinjourney.app.feature.history.components.RecordTileSize
import com.violinjourney.app.feature.history.components.SelectionBar
import com.violinjourney.app.feature.history.components.SelectionBarHeight
import com.violinjourney.app.feature.history.components.SelectionDeleteDialog
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.history.components.recordsCountOf
import com.violinjourney.app.feature.history.components.stripDescription
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_empty_live_text
import com.violinjourney.app.shared.resources.history_empty_live_title
import com.violinjourney.app.shared.resources.history_empty_takes_text
import com.violinjourney.app.shared.resources.history_empty_text
import com.violinjourney.app.shared.resources.history_empty_title
import com.violinjourney.app.shared.resources.history_empty_video_text
import com.violinjourney.app.shared.resources.history_empty_video_title
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.history_filter_live
import com.violinjourney.app.shared.resources.history_filter_takes
import com.violinjourney.app.shared.resources.history_filter_video
import com.violinjourney.app.shared.resources.history_open_live
import com.violinjourney.app.shared.resources.history_show_all
import com.violinjourney.app.shared.resources.history_strip_title
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.takes_empty_title
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The tab «Записи» of R5 (spec 3.36.5, 5.29 R5; records.html 1).
private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp

/**
 * The tag of the strip — outside its dimming: while picking, the dimmed strip says nothing to TalkBack, and a test that follows its place
 * frame by frame has nothing else to find it by (`HistoryTabTest`).
 */
internal const val HISTORY_STRIP_TAG = "history strip"

/** The line of the title: as high as the selection bar that lies over it while picking (56; lying 52). */
private val TitleRowHeight = SelectionBarHeight.Portrait
private val TitleRowHeightLandscape = SelectionBarHeight.Landscape

/** A text button stands out into the field by its own padding: its word ends where the cards do. */
private val TextButtonEdge = 12.dp
private val StripTop = 8.dp
private val StripPaddingVertical = 14.dp
private val StripPaddingSide = 16.dp
private val StripBarsTop = 10.dp

/** A filter chip is 40 in a place of 48: 4 of air above and under it count into the gaps that are seen — 14 above, 4 under. */
private val ChipAir = 4.dp
private val ChipsTop = 14.dp - ChipAir
private val ChipsBottom = 4.dp - ChipAir
private val ChipGap = 8.dp
private val CardGap = 6.dp

/** The empty states: their words stand 32 from the edges of the screen — 16 inside its field. */
private val EmptySide = 32.dp - ScreenPadding
private val EmptyTitleTop = 16.dp
private val EmptyTextTop = 8.dp
private val EmptyUnderFilterTop = 40.dp
private val EmptyButtonTop = 20.dp

/** An empty tab lying: its words and «Открыть Live» in the middle of the right column, no wider than this. */
private val EmptyLandscapeWidth = 400.dp
private const val BAR_FADE_MS = 200

/** A deleted card fades out and the list closes over it (spec 5.12). */
private const val CARD_FADE_OUT_MS = 150
private const val CARD_COLLAPSE_MS = 250

/** Kinds of rows of the list of records: a composition is reused only for a row of its own kind. */
private const val DAY_HEADER = "dayHeader"
private const val RECORD_CARD = "recordCard"

/** The key of the row «Выступления» in the list upright — of the row alone, and of the row over the block of an empty tab. */
private const val PERFORMANCES = "performances"

/**
 * The columns of the tab lying (spec 3.36.5): the title, the strip and the chips on the left, 360 as before; the list on the right,
 * down to the bar. The left column is never wider than half of what the two leave: on a phone of 640 × 360 with a cutout at its side
 * (≈ 600) a column of 360 would leave the list 195, and the line of a card could not show its time and its length.
 */
internal object HistoryColumns {
    val Left = 360.dp
    val Gap = 16.dp

    fun left(width: Dp): Dp = minOf(Left, ((width - Gap * 3) / 2).coerceAtLeast(0.dp))
}

/**
 * The tab «Записи» (spec 3.11, 3.21, 3.36.5): «what was recorded and when». On top the line of the title — «Записи» and «Выбрать»
 * (only while the chip shows cards) — then the strip «За две недели», the chips «Все · Дубли · Видео · С Live» (they wrap, never
 * slide away), and the list by days, the newest on top, the headers of the days not sticky. Picking lays the selection bar over the
 * title and dims the strip and the chips. Nothing at all — no strip, no chips: a word of what will be here and «Открыть Live» in the
 * bottom zone; nothing under a chip — its own words and «Показать все записи». While the list is read — the title alone. Lying, the
 * title, the strip and the chips are a column on the left ([HistoryColumns]) and the list scrolls on the right; «Открыть Live» of an
 * empty tab stands in the middle of the right column. Stateless.
 */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    cardActions: CardActions? = null,
) {
    val selection = state.selection
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            LandscapeLayout(state, onIntent, zone, cardActions, leftWidth = HistoryColumns.left(maxWidth))
        } else {
            PortraitLayout(state, onIntent, zone, cardActions)
        }
        // Over the list, not in it: the title scrolls away with the list, the bin must not (handoff 19e2).
        AnimatedVisibility(visible = selection.active, enter = fadeIn(tween(BAR_FADE_MS)), exit = fadeOut(tween(BAR_FADE_MS))) {
            SelectionBar(
                selection, state.allSelected, onIntent = { onIntent(HistoryIntent.Select(it)) },
                height = if (landscape) SelectionBarHeight.Landscape else SelectionBarHeight.Portrait,
            )
        }
    }
    SelectionDeleteDialog(selection, state.cards, takes = false, onIntent = { onIntent(HistoryIntent.Select(it)) })
}

/**
 * Upright: one column up to 560. One list whatever there is (spec 3.36.5, 5.12): the last card deleted fades out and closes as every
 * deleted card does, and the strip and the chips fade with it, instead of an empty tab taking the place of the list at once. An empty
 * tab has the bottom zone with «Открыть Live» over the tabs — the zone is there only then ([AppDock] `pinned`). The row «Выступления»
 * (spec 3.36.9) is the first under the title, 4 under it, the strip 8 under the row; in an empty tab the row and the block of the tab
 * are one element of the list — the block stands in the middle of what the row leaves ([RowOverEmptyTab]). While picking the row goes
 * as the strip and the chips go when the last card is deleted — it fades out where it stands, without shrinking; no motion of its own
 * (plan D44): what was under it comes up with the shift the cards take after a deletion.
 */
@Composable
private fun PortraitLayout(state: HistoryState, onIntent: (HistoryIntent) -> Unit, zone: TimeZone, cardActions: CardActions?) {
    val title = stringResource(Res.string.nav_history)
    val selecting = state.selection.active
    val empty = state.nothingAtAll
    val performances = state.performances
    // «убрать анимации» — at once
    val reduceMotion = LocalReduceMotion.current
    val rowFadeOut = if (reduceMotion) null else tween<Float>(CARD_FADE_OUT_MS)
    // The strip and the chips follow the row as the cards do (their shift after a deletion, 250): a list draws what leaves it under the
    // rest (LazyLayoutItemAnimator), so a strip that jumped into the place of the row at once would hide its fade. Nothing above them
    // moved before the row (R5): their frames there are the same.
    val belowTheRow = if (reduceMotion) null else tween<IntOffset>(CARD_COLLAPSE_MS)
    val openPerformances = { onIntent(HistoryIntent.PerformancesClicked) }
    AppDock(
        dock = { OpenLiveButton(onIntent, compact = compact) },
        modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
        metrics = currentDockMetrics().copy(side = ScreenPadding),
        pinned = empty,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val inset = LocalDockInset.current
            val room = (maxHeight - TitleRowHeight - inset).coerceAtLeast(0.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = maxOf(ScreenPadding, inset)),
            ) {
                // scrolls away with the list, as the switch «Записи | Репертуар» did
                item(key = "title") {
                    TitleRow(title, canSelect = state.canSelect, onSelect = { onIntent(HistoryIntent.Select(SelectionIntent.SelectClicked)) }, landscape = false)
                }
                when {
                    state.loading -> Unit
                    empty -> item(key = PERFORMANCES) {
                        // in the middle of what is left between the row and the zone; scrolls when it is more (a large font on 360 × 640)
                        RowOverEmptyTab(performances?.takeUnless { selecting }, onClick = openPerformances, room = room)
                    }
                    else -> {
                        if (performances != null && !selecting) {
                            item(key = PERFORMANCES) {
                                PerformancesRow(
                                    performances = performances,
                                    onClick = openPerformances,
                                    modifier = Modifier
                                        .animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = rowFadeOut)
                                        .padding(top = EventsDimens.RecordsRowTop),
                                )
                            }
                        }
                        item(key = "strip") {
                            StripCard(state, Modifier.animateItem(fadeInSpec = null, placementSpec = belowTheRow, fadeOutSpec = tween(CARD_FADE_OUT_MS)).padding(top = StripTop).testTag(HISTORY_STRIP_TAG).dimmedWhen(selecting))
                        }
                        item(key = "chips") {
                            KindChips(
                                state.filter, onSelect = { onIntent(HistoryIntent.FilterSelected(it)) },
                                Modifier.animateItem(fadeInSpec = null, placementSpec = belowTheRow, fadeOutSpec = tween(CARD_FADE_OUT_MS)).padding(top = ChipsTop, bottom = ChipsBottom).dimmedWhen(selecting),
                            )
                        }
                        records(state, onIntent, zone, cardActions)
                    }
                }
            }
        }
    }
}

/**
 * Lying (spec 3.36.5, landscape.html «Остальные экраны»): on the left the title of 24 sp with «Выбрать», the strip and the chips —
 * the strip and the chips dimmed while picking, when the bar lies over the whole width; on the right the list, from under the bar.
 * No bottom zone: the main action is to open a recording. An empty tab — the title alone on the left, and on the right in the middle
 * the tile, the words and «Открыть Live» as the main button of the column. The list on the right is one whatever there is, as upright:
 * the last card deleted fades out, and the strip and the chips on the left fade with it. The row «Выступления» (spec 3.36.9) stands in
 * the left column under the title — in an empty tab too — and while picking goes as upright: it fades out where it stands and the strip
 * and the chips come up into its place over 250 (plan D44, 5.29 R9).
 */
@Composable
private fun LandscapeLayout(state: HistoryState, onIntent: (HistoryIntent) -> Unit, zone: TimeZone, cardActions: CardActions?, leftWidth: Dp) {
    val selecting = state.selection.active
    val empty = state.nothingAtAll
    val reduceMotion = LocalReduceMotion.current
    Row(Modifier.fillMaxSize().padding(start = ScreenPadding)) {
        Column(
            Modifier
                .width(leftWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ScreenPadding),
        ) {
            TitleRow(
                stringResource(Res.string.nav_history), canSelect = state.canSelect,
                onSelect = { onIntent(HistoryIntent.Select(SelectionIntent.SelectClicked)) }, landscape = true,
            )
            // the row comes with the list read, at once — no animation then: it is composed shown. While picking it goes as upright: it
            // fades out (150) where it stands, unclipped, while its place closes over 250 and the strip and the chips under it come up into
            // it; after picking it stands at once and they go down the same way (5.29 R9, review of stage 99); «убрать анимации» — at once
            if (!state.loading) {
                state.performances?.let { performances ->
                    AnimatedVisibility(
                        visible = !selecting,
                        enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(CARD_COLLAPSE_MS), expandFrom = Alignment.Top, clip = false),
                        exit = if (reduceMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(tween(CARD_FADE_OUT_MS)) + shrinkVertically(tween(CARD_COLLAPSE_MS), shrinkTowards = Alignment.Top, clip = false)
                        },
                    ) {
                        PerformancesRow(
                            performances = performances,
                            onClick = { onIntent(HistoryIntent.PerformancesClicked) },
                            modifier = Modifier.padding(top = EventsDimens.RecordsRowTop),
                        )
                    }
                }
            }
            // they come with the list read at once, and go with the last card — fading as it does
            AnimatedVisibility(visible = !state.loading && !empty, enter = EnterTransition.None, exit = fadeOut(tween(CARD_FADE_OUT_MS))) {
                Column {
                    StripCard(state, Modifier.padding(top = StripTop).testTag(HISTORY_STRIP_TAG).dimmedWhen(selecting))
                    KindChips(state.filter, onSelect = { onIntent(HistoryIntent.FilterSelected(it)) }, Modifier.padding(top = ChipsTop, bottom = ChipsBottom).dimmedWhen(selecting))
                }
            }
        }
        Spacer(Modifier.width(HistoryColumns.Gap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            // an empty tab's block stands in the middle of the column: the list starts [TitleRowHeightLandscape] under its top, so the
            // block is given as much under it
            val room = (maxHeight - TitleRowHeightLandscape * 2).coerceAtLeast(0.dp)
            // from under the bar that lies over the whole width while picking: the first day starts level with the strip
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = TitleRowHeightLandscape, end = ScreenPadding, bottom = ScreenPadding),
            ) {
                when {
                    state.loading -> Unit
                    empty -> item(key = "emptyAll") {
                        Box(Modifier.fillMaxWidth().heightIn(min = room), contentAlignment = Alignment.Center) {
                            Column(Modifier.widthIn(max = EmptyLandscapeWidth)) {
                                EmptyAll()
                                OpenLiveButton(onIntent, compact = currentDockMetrics().compact, modifier = Modifier.padding(top = EmptyButtonTop))
                            }
                        }
                    }
                    else -> records(state, onIntent, zone, cardActions)
                }
            }
        }
    }
}

/** Nothing recorded at all: no strip and no chips — a word of what will be here, and the way to Live. */
private val HistoryState.nothingAtAll: Boolean get() = !loading && totalCount == 0

/**
 * An empty tab upright (spec 3.36.9, plan D44): the row «Выступления» first, 4 under the title, and the block of the tab in the middle of
 * what the row leaves of [room] — the room between the title and the zone — one element of the list, so the block does not slide down by
 * the height of the row. Where the block is taller than what is left (a large font on 360 × 640), it comes right under the row and
 * scrolls. Without the row ([performances] null) — the block alone in the middle of [room], as before.
 */
@Composable
private fun RowOverEmptyTab(performances: HistoryPerformances?, onClick: () -> Unit, room: Dp) {
    Layout(
        content = {
            if (performances != null) PerformancesRow(performances, onClick = onClick, modifier = Modifier.padding(top = EventsDimens.RecordsRowTop))
            EmptyAll()
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val row = if (measurables.size > 1) measurables.first().measure(loose) else null
        val block = measurables.last().measure(loose)
        val above = row?.height ?: 0
        val space = maxOf(room.roundToPx() - above, block.height)
        layout(constraints.maxWidth, above + space) {
            row?.placeRelative(0, 0)
            block.placeRelative(0, above + (space - block.height) / 2)
        }
    }
}

/** «Выбрать» only while the chip shows cards (spec 3.36.5); gone under the bar while picking. */
private val HistoryState.canSelect: Boolean get() = !loading && cards.isNotEmpty() && !selection.active

/** The days and their cards; nothing under the chip — its own words and «Показать все записи». */
private fun LazyListScope.records(state: HistoryState, onIntent: (HistoryIntent) -> Unit, zone: TimeZone, cardActions: CardActions?) {
    val selection = state.selection
    state.groups.forEach { group ->
        // Part of the list, not of its controls: stays as it is while picking, and is not picked itself (handoff 22d).
        // two kinds of rows, told apart: a scrolled-off card is reused only for a card, a header for a header
        item(key = "day-" + group.date, contentType = DAY_HEADER) {
            DayHeader(group, Modifier.animateItem(placementSpec = tween(CARD_COLLAPSE_MS), fadeOutSpec = tween(CARD_FADE_OUT_MS)))
        }
        items(group.cards, key = { it.id }, contentType = { RECORD_CARD }) { card ->
            SessionCard(
                card = card,
                zone = zone,
                onClick = { onIntent(HistoryIntent.SessionClicked(card.id)) },
                modifier = Modifier
                    .padding(top = CardGap)
                    .animateItem(placementSpec = tween(CARD_COLLAPSE_MS), fadeOutSpec = tween(CARD_FADE_OUT_MS)),
                actions = cardActions,
                selected = if (selection.active) card.id in selection.ids else null,
                onLongClick = { onIntent(HistoryIntent.Select(SelectionIntent.CardLongPressed(card.id))) },
            )
        }
    }
    if (state.cards.isEmpty()) {
        item(key = "emptyFilter") {
            EmptyUnderFilter(state.filter, onShowAll = { onIntent(HistoryIntent.FilterSelected(HistoryFilter.ALL)) })
        }
    }
}

/**
 * «Записи» and «Выбрать» on one line (spec 3.36.5): the title of the tab gives way with an ellipsis (fr «Enregistrements» beside
 * «Sélectionner» on 360 with a large font) and is read whole; the button is measured first, so it is never squeezed or wrapped.
 */
@Composable
private fun TitleRow(title: String, canSelect: Boolean, onSelect: () -> Unit, landscape: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (landscape) TitleRowHeightLandscape else TitleRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabTitle(title, Modifier.weight(1f), compact = landscape)
        if (canSelect) {
            AppButton(
                text = stringResource(Res.string.selection_select),
                onClick = onSelect,
                modifier = Modifier.offset(x = TextButtonEdge),
                style = AppButtonStyle.Text,
                icon = AppIcons.Select,
            )
        }
    }
}

/**
 * «За две недели» and the total «12 записей» over fourteen bars (spec 3.36.5, 5.29 R5): the fourteen days whatever the chip, the
 * total whatever the chip too. TalkBack hears it once — the old words of the chart with the total, then each day with recordings.
 */
@Composable
private fun StripCard(state: HistoryState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val total = state.stripTotal
    val said = stripDescription(state.days, total)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clearAndSetSemantics { contentDescription = said }
            .padding(horizontal = StripPaddingSide, vertical = StripPaddingVertical),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(StripTitleGap)) {
            Text(
                text = stringResource(Res.string.history_strip_title),
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
            Text(
                text = stringResource(recordsCountOf(total), total),
                modifier = Modifier.alignByBaseline(),
                color = colors.onSurface,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        DailyChart(days = state.days, top = state.chartTop, modifier = Modifier.padding(top = StripBarsTop))
    }
}

private val StripTitleGap = 12.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * «Все · Дубли · Видео · С Live» (spec 3.36.5): filter chips of R1 without icons; all four are always seen — what does not fit the
 * line wraps onto the next, 8 under it, instead of sliding away sideways (360, de, fr, a large font). One group for TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
        HistoryFilter.entries.forEach { filter ->
            AppChip(text = stringResource(chipWordOf(filter)), selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

private fun chipWordOf(filter: HistoryFilter): StringResource = when (filter) {
    HistoryFilter.ALL -> Res.string.history_filter_all
    HistoryFilter.TAKES -> Res.string.history_filter_takes
    HistoryFilter.VIDEO -> Res.string.history_filter_video
    HistoryFilter.LIVE -> Res.string.history_filter_live
}

/** Nothing recorded at all (spec 3.36.5): the outlined tile with a note, «Здесь появятся ваши записи» and how they come. */
@Composable
private fun EmptyAll(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = EmptySide), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordTile(hasAudio = false, hasVideo = false, size = RecordTileSize.EMPTY)
        Text(
            text = stringResource(Res.string.history_empty_title),
            modifier = Modifier.padding(top = EmptyTitleTop),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            text = stringResource(Res.string.history_empty_text),
            modifier = Modifier.padding(top = EmptyTextTop),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        )
    }
}

/** «Открыть Live» — the tab Live: the main button of the bottom zone upright, of the right column lying. */
@Composable
private fun OpenLiveButton(onIntent: (HistoryIntent) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    AppButton(
        text = stringResource(Res.string.history_open_live),
        onClick = { onIntent(HistoryIntent.OpenLiveClicked) },
        modifier = modifier.fillMaxWidth(),
        icon = AppIcons.TabLive.normal,
        compact = compact,
    )
}

/**
 * Nothing under a chip (spec 3.36.5): the strip and the chips stay, «Выбрать» is gone; the outlined tile with the sign of the kind —
 * a video under «Видео», a note under the others — what is missing, where it comes from, and «Показать все записи» (the chip «Все»).
 */
@Composable
private fun EmptyUnderFilter(filter: HistoryFilter, onShowAll: () -> Unit) {
    val (titleWords, textWords) = emptyWordsOf(filter) ?: return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EmptySide, end = EmptySide, top = EmptyUnderFilterTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecordTile(hasAudio = false, hasVideo = filter == HistoryFilter.VIDEO, size = RecordTileSize.EMPTY)
        Text(
            text = stringResource(titleWords),
            modifier = Modifier.padding(top = EmptyTitleTop),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            text = stringResource(textWords),
            modifier = Modifier.padding(top = EmptyTextTop),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
        AppButton(
            text = stringResource(Res.string.history_show_all),
            onClick = onShowAll,
            modifier = Modifier.padding(top = EmptyTextTop),
            style = AppButtonStyle.Text,
        )
    }
}

/** The title and the words of an empty chip; «Все» is never empty while there are recordings. */
private fun emptyWordsOf(filter: HistoryFilter): Pair<StringResource, StringResource>? = when (filter) {
    HistoryFilter.ALL -> null
    HistoryFilter.TAKES -> Res.string.takes_empty_title to Res.string.history_empty_takes_text
    HistoryFilter.VIDEO -> Res.string.history_empty_video_title to Res.string.history_empty_video_text
    HistoryFilter.LIVE -> Res.string.history_empty_live_title to Res.string.history_empty_live_text
}
