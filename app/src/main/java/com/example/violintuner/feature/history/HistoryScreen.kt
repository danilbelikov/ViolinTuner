package com.example.violintuner.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.violintuner.core.ui.components.SegmentedSwitch
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
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
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
        ) {
            item(key = "header") { Header(state) }
            item(key = "sections") {
                val sections = HistorySection.entries
                SegmentedSwitch(
                    labels = listOf(stringResource(R.string.history_section_sessions), stringResource(R.string.history_section_repertoire)),
                    selectedIndex = sections.indexOf(state.section),
                    onSelect = { onIntent(HistoryIntent.SectionSelected(sections[it])) },
                    modifier = Modifier.padding(top = SectionSwitchTop),
                )
            }
            when {
                state.section == HistorySection.REPERTOIRE -> repertoireItems(repertoire, onRepertoireIntent)
                state.loading -> Unit
                state.totalCount == 0 -> item(key = "empty") { EmptyHistory(Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION)) }
                else -> {
                    item(key = "chart") { ChartCard(state, Modifier.padding(top = SectionSpacing)) }
                    item(key = "filters") {
                        Filters(
                            selected = state.filter,
                            onSelect = { onIntent(HistoryIntent.FilterSelected(it)) },
                            modifier = Modifier.padding(top = SectionSpacing, bottom = SectionSpacing - CardSpacing),
                        )
                    }
                    items(state.cards, key = { it.id }) { card ->
                        SessionCard(
                            card = card,
                            zone = zone,
                            onClick = { onIntent(HistoryIntent.SessionClicked(card.id)) },
                            modifier = Modifier.padding(top = CardSpacing),
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
    }
}

private const val EMPTY_HEIGHT_FRACTION = 0.8f

@Composable
private fun Header(state: HistoryState) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = stringResource(R.string.nav_history),
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        if (!state.loading && state.totalCount > 0 && state.section == HistorySection.SESSIONS) {
            val countRes = Formats.pluralRu(
                state.totalCount, R.string.history_count_one, R.string.history_count_few, R.string.history_count_many,
            )
            Text(
                text = stringResource(countRes, state.totalCount),
                modifier = Modifier.alignByBaseline(),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
