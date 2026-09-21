package com.example.violintuner.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.components.DeleteDialog
import com.example.violintuner.core.ui.components.SegmentedSwitch
import com.example.violintuner.core.ui.components.dimmedWhen
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.feature.history.components.CardActions
import com.example.violintuner.feature.history.components.DailyChart
import com.example.violintuner.feature.history.components.RecordTile
import com.example.violintuner.feature.history.components.RecordTileSize
import com.example.violintuner.feature.history.components.SelectAction
import com.example.violintuner.feature.history.components.SelectionBar
import com.example.violintuner.feature.history.components.SelectionBarHeight
import com.example.violintuner.feature.history.components.SessionCard
import com.example.violintuner.feature.history.components.deleteTextOf
import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.feature.repertoire.sections.SectionNameDialog
import com.example.violintuner.feature.repertoire.sections.SectionsIntent
import com.example.violintuner.feature.repertoire.sections.SectionsState
import com.example.violintuner.feature.repertoire.sections.sectionItems
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val SectionSpacing = 16.dp
private val SwitchTop = 12.dp
private val SwitchHeight = 40.dp
private val LandscapeSwitchTop = 8.dp
private val LandscapeSwitchHeight = 36.dp
private val LandscapeSwitchWidth = 320.dp
private val LandscapeChartWidth = 360.dp
private val CardSpacing = 8.dp
private val ChartCorner = 20.dp
private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private const val TABULAR_FIGURES = "tnum"

/** The «Записи» tab (spec 3.11, 3.21; handoff 22a2). Stateless. */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    sections: SectionsState = SectionsState(loading = true, cards = emptyList(), total = SectionCount.EMPTY, maxNameLength = 0),
    onSectionsIntent: (SectionsIntent) -> Unit = {},
    cardActions: CardActions? = null,
) {
    val colors = MaterialTheme.colorScheme
    val selection = state.selection
    val selecting = selection.active
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        val landscape = maxWidth > maxHeight
        val barHeight = if (landscape) SelectionBarHeight.Landscape else SelectionBarHeight.Portrait
        val records = state.section == HistorySection.SESSIONS && !state.loading && state.totalCount > 0
        val switch: @Composable (Modifier) -> Unit = { switchModifier ->
            val sections = HistorySection.entries
            // No big title above it (handoff 22a2): the switch is the title — «Записи | Репертуар» under «Записи» said it twice.
            SegmentedSwitch(
                labels = listOf(stringResource(R.string.history_section_sessions), stringResource(R.string.history_section_repertoire)),
                selectedIndex = sections.indexOf(state.section),
                onSelect = { onIntent(HistoryIntent.SectionSelected(sections[it])) },
                modifier = switchModifier.dimmedWhen(selecting),
                height = if (landscape) LandscapeSwitchHeight else SwitchHeight,
            )
        }
        val list: LazyListScope.() -> Unit = {
            when {
                state.section == HistorySection.REPERTOIRE -> sectionItems(sections, onSectionsIntent)
                state.loading -> Unit
                state.totalCount == 0 -> item(key = "empty") { EmptyHistory(Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION)) }
                else -> {
                    if (!landscape) item(key = "chart") { ChartCard(state, Modifier.padding(top = SectionSpacing).dimmedWhen(selecting)) }
                    item(key = "filters") {
                        Filters(
                            selected = state.filter,
                            onSelect = { onIntent(HistoryIntent.FilterSelected(it)) },
                            modifier = Modifier
                                .padding(top = if (landscape) 0.dp else SectionSpacing)
                                .dimmedWhen(selecting),
                        )
                    }
                    item(key = "count") {
                        // under an empty filter there is nothing to pick: the count says «0 записей», the action is gone (handoff 22i2)
                        CountRow(
                            total = if (state.cards.isEmpty()) 0 else state.totalCount,
                            selecting = selecting || state.cards.isEmpty(),
                            onSelect = { onIntent(HistoryIntent.Select(SelectionIntent.SelectClicked)) },
                        )
                    }
                    state.groups.forEach { group ->
                        // Part of the list, not of its controls: stays as it is while picking, and is not picked itself (handoff 22d).
                        item(key = "day-" + group.date) { DayHeader(group, Modifier.animateItem()) }
                        items(group.cards, key = { it.id }) { card ->
                            SessionCard(
                                card = card,
                                zone = zone,
                                onClick = { onIntent(HistoryIntent.SessionClicked(card.id)) },
                                modifier = Modifier
                                    .padding(top = CardSpacing)
                                    .animateItem(),
                                actions = cardActions,
                                selected = if (selecting) card.id in selection.ids else null,
                                onLongClick = { onIntent(HistoryIntent.Select(SelectionIntent.CardLongPressed(card.id))) },
                            )
                        }
                    }
                    if (state.cards.isEmpty()) {
                        item(key = "emptyFilter") {
                            Text(
                                text = stringResource(R.string.history_empty_filter),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 40.dp),
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            )
                        }
                    }
                }
            }
        }
        if (landscape) {
            // The chart stops being a card above the list and becomes the left column: the list gets the whole height (handoff 22h1).
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
                switch(Modifier.padding(top = LandscapeSwitchTop).width(LandscapeSwitchWidth).align(Alignment.CenterHorizontally))
                Row(modifier = Modifier.padding(top = LandscapeSwitchTop), horizontalArrangement = Arrangement.spacedBy(ScreenPadding)) {
                    if (records) ChartCard(state, Modifier.width(LandscapeChartWidth).dimmedWhen(selecting))
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = ScreenPadding), content = list)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = SwitchTop, bottom = ScreenPadding),
            ) {
                item(key = "sections") { switch(Modifier) }
                list()
            }
        }
        // Over the list, not in it: the title scrolls away with the list, the bin must not (handoff 19e2).
        AnimatedVisibility(visible = selecting, enter = fadeIn(tween(BAR_FADE_MS)), exit = fadeOut(tween(BAR_FADE_MS))) {
            SelectionBar(selection, state.allSelected, onIntent = { onIntent(HistoryIntent.Select(it)) }, height = barHeight)
        }
    }
    sections.newName?.let { name ->
        SectionNameDialog(
            title = stringResource(R.string.section_new_title),
            confirm = stringResource(R.string.section_create),
            name = name,
            maxLength = sections.maxNameLength,
            canConfirm = sections.canCreate,
            onNameChange = { onSectionsIntent(SectionsIntent.NameChanged(it)) },
            onConfirm = { onSectionsIntent(SectionsIntent.CreateConfirmed) },
            onDismiss = { onSectionsIntent(SectionsIntent.DialogDismissed) },
        )
    }
    if (selection.confirming) {
        val words = Formats.plural(selection.count, R.string.selection_delete_records_one, R.string.selection_delete_records_few, R.string.selection_delete_records_many)
        DeleteDialog(
            title = stringResource(words, selection.count),
            text = deleteTextOf(state.cards.filter { it.id in selection.ids }.sumOf { it.videoBytes }),
            onConfirm = { onIntent(HistoryIntent.Select(SelectionIntent.DeleteConfirmed)) },
            onDismiss = { onIntent(HistoryIntent.Select(SelectionIntent.DeleteDismissed)) },
        )
    }
}

private const val EMPTY_HEIGHT_FRACTION = 0.8f
private const val BAR_FADE_MS = 200
private val CountRowHeight = 32.dp

/** The date above a day's recordings (spec 3.21, handoff 22c1): quiet, not a card, not pressed, not sticky; today carries the chip of «Занятия». */
@Composable
private fun DayHeader(group: DayGroup, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = DayHeaderTop)
            .height(DayHeaderHeight)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formats.recordDayHeader(group.date, withYear = group.cards.first().otherYear),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
        if (group.today) {
            Text(
                text = stringResource(R.string.history_day_today),
                modifier = Modifier
                    .border(1.dp, colors.primary, RoundedCornerShape(TodayChipCorner))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                color = colors.primary,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private val DayHeaderTop = 8.dp
private val DayHeaderHeight = 22.dp
private val TodayChipCorner = 6.dp

/**
 * The line above the list (spec 3.18, handoff 19a2): «23 записи» — all of them, whatever the
 * filter — and «Выбрать». While picking, the count stays and the action steps aside.
 */
@Composable
private fun CountRow(total: Int, selecting: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SectionSpacing - CardSpacing)
            .height(CountRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val countRes = Formats.plural(total, R.string.history_count_one, R.string.history_count_few, R.string.history_count_many)
        Text(
            text = stringResource(countRes, total),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
        // sticks out into the margin by its own padding, so the word lines up with the cards
        if (!selecting) SelectAction(onSelect, Modifier.offset(x = 10.dp))
    }
}

/** «Записи за две недели» (spec 3.21, handoff 22c): how many, by day; the filter below does not touch it. */
@Composable
private fun ChartCard(state: HistoryState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val total = state.days.sumOf { it.count }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(ChartCorner))
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row {
            Text(
                text = stringResource(R.string.history_chart_title),
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
            Text(
                text = stringResource(Formats.plural(total, R.string.history_count_one, R.string.history_count_few, R.string.history_count_many), total),
                modifier = Modifier.alignByBaseline(),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        DailyChart(days = state.days, top = state.chartTop, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Filters(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val shape = RoundedCornerShape(ChipCorner)
            Box(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(shape)
                    .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                    .border(1.dp, if (isSelected) colors.primaryContainer else colors.outlineVariant, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(filter) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        when (filter) {
                            HistoryFilter.ALL -> R.string.history_filter_all
                            HistoryFilter.THIS_WEEK -> R.string.history_filter_this_week
                            HistoryFilter.MONTH -> R.string.history_filter_month
                        },
                    ),
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

/** No recordings at all (handoff 22i1): no chart, the switch stays — the repertoire may be filled before the first recording. */
@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecordTile(hasAudio = false, hasVideo = false, size = RecordTileSize.EMPTY)
        Text(
            text = stringResource(R.string.history_empty),
            modifier = Modifier.widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        )
    }
}
