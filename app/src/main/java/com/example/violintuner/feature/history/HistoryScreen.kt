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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.components.DeleteDialog
import com.example.violintuner.core.ui.components.SegmentedSwitch
import com.example.violintuner.core.ui.components.dimmedWhen
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.CardActions
import com.example.violintuner.feature.history.components.SelectAction
import com.example.violintuner.feature.history.components.SelectionBar
import com.example.violintuner.feature.history.components.SelectionBarHeight
import com.example.violintuner.feature.history.components.SessionCard
import com.example.violintuner.feature.history.components.WeeklyChart
import com.example.violintuner.feature.repertoire.RepertoireIntent
import com.example.violintuner.feature.repertoire.RepertoireReducer
import com.example.violintuner.feature.repertoire.RepertoireState
import com.example.violintuner.feature.repertoire.repertoireItems
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val SectionSpacing = 16.dp
private val SectionSwitchTop = 14.dp
private val CardSpacing = 8.dp
private val ChartCorner = 20.dp
private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private const val TABULAR_FIGURES = "tnum"

/** History of sessions (spec 3.11, handoff 4c). Stateless. */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    repertoire: RepertoireState = RepertoireReducer.loading(filter = null),
    onRepertoireIntent: (RepertoireIntent) -> Unit = {},
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
        val barHeight = if (maxWidth > maxHeight) SelectionBarHeight.Landscape else SelectionBarHeight.Portrait
        LazyColumn(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
        ) {
            item(key = "header") { Header(Modifier.dimmedWhen(selecting)) }
            item(key = "sections") {
                val sections = HistorySection.entries
                SegmentedSwitch(
                    labels = listOf(stringResource(R.string.history_section_sessions), stringResource(R.string.history_section_repertoire)),
                    selectedIndex = sections.indexOf(state.section),
                    onSelect = { onIntent(HistoryIntent.SectionSelected(sections[it])) },
                    modifier = Modifier
                        .padding(top = SectionSwitchTop)
                        .dimmedWhen(selecting),
                )
            }
            when {
                state.section == HistorySection.REPERTOIRE -> repertoireItems(repertoire, onRepertoireIntent)
                state.loading -> Unit
                state.totalCount == 0 -> item(key = "empty") { EmptyHistory(Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION)) }
                else -> {
                    item(key = "chart") { ChartCard(state, Modifier.padding(top = SectionSpacing).dimmedWhen(selecting)) }
                    item(key = "filters") {
                        Filters(
                            selected = state.filter,
                            onSelect = { onIntent(HistoryIntent.FilterSelected(it)) },
                            modifier = Modifier
                                .padding(top = SectionSpacing)
                                .dimmedWhen(selecting),
                        )
                    }
                    if (state.cards.isNotEmpty()) {
                        item(key = "count") {
                            CountRow(state.totalCount, selecting, onSelect = { onIntent(HistoryIntent.Select(SelectionIntent.SelectClicked)) })
                        }
                    }
                    items(state.cards, key = { it.id }) { card ->
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
                    if (state.cards.isEmpty()) {
                        item(key = "emptyFilter") {
                            Text(
                                text = stringResource(R.string.history_empty_filter),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        // Over the list, not in it: the title scrolls away with the list, the bin must not (handoff 19e2).
        AnimatedVisibility(visible = selecting, enter = fadeIn(tween(BAR_FADE_MS)), exit = fadeOut(tween(BAR_FADE_MS))) {
            SelectionBar(selection, state.allSelected, onIntent = { onIntent(HistoryIntent.Select(it)) }, height = barHeight)
        }
    }
    if (selection.confirming) {
        val words = Formats.pluralRu(selection.count, R.string.selection_delete_records_one, R.string.selection_delete_records_few, R.string.selection_delete_records_many)
        DeleteDialog(
            title = stringResource(words, selection.count),
            text = stringResource(R.string.selection_delete_text),
            onConfirm = { onIntent(HistoryIntent.Select(SelectionIntent.DeleteConfirmed)) },
            onDismiss = { onIntent(HistoryIntent.Select(SelectionIntent.DeleteDismissed)) },
        )
    }
}

private const val EMPTY_HEIGHT_FRACTION = 0.8f
private const val BAR_FADE_MS = 200
private val CountRowHeight = 32.dp

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.nav_history),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    )
}

/**
 * The line above the list (spec 3.18, handoff 19a2): «23 сессии» — all of them, whatever the
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
        val countRes = Formats.pluralRu(total, R.string.history_count_one, R.string.history_count_few, R.string.history_count_many)
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

@Composable
private fun ChartCard(state: HistoryState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(ChartCorner))
            .padding(16.dp),
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
            state.weekDelta?.let { delta ->
                Text(
                    text = stringResource(R.string.history_week_delta, Formats.signedCents(delta.toDouble())),
                    modifier = Modifier.alignByBaseline(),
                    color = if (delta >= 0) zoneColors.inTune else zoneColors.near,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
            }
        }
        WeeklyChart(weeks = state.weeks, modifier = Modifier.padding(top = 8.dp))
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

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.history_empty),
            modifier = Modifier.widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
