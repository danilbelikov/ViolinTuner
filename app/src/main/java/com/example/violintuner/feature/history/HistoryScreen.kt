package com.example.violintuner.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.WeeklyChart
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val SectionSpacing = 16.dp
private val CardSpacing = 8.dp
private val ChartCorner = 20.dp
private val CardCorner = 16.dp
private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private val ScoreColumnWidth = 52.dp
private val PreviewBarWidth = 4.dp
private val PreviewHeight = 28.dp
private const val TABULAR_FIGURES = "tnum"

/** History of sessions (spec 3.11, handoff 4c). Stateless. */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
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
            when {
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
        if (!state.loading && state.totalCount > 0) {
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
private fun SessionCard(card: HistoryCard, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val day = when (val label = card.day) {
        DayLabel.Today -> stringResource(R.string.history_day_today)
        DayLabel.Yesterday -> stringResource(R.string.history_day_yesterday)
        is DayLabel.On -> Formats.dayAndShortMonth(label.date)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(ScoreColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.scorePercent.toString(),
                color = zoneColors.colorFor(card.scoreZone),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            Text(
                text = stringResource(R.string.history_percent_sign),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title
                    ?: stringResource(R.string.session_default_title, Formats.dayAndMonth(card.startedAtEpochMs, zone)),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(
                    R.string.history_card_meta, day, Formats.duration(card.durationMs), Formats.signedCents(card.biasCents),
                ),
                modifier = Modifier.padding(top = 2.dp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
        PreviewBars(card.previewZones)
    }
}

/** Mini bars of the first notes: tall green, medium amber, short red (spec 3.11). */
@Composable
private fun PreviewBars(zones: List<Zone>) {
    val zoneColors = ViolinTheme.zoneColors
    Row(
        modifier = Modifier.height(PreviewHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        zones.forEach { zone ->
            val height = when (zone) {
                Zone.IN_TUNE -> PreviewHeight
                Zone.NEAR -> 16.dp
                Zone.OFF -> 8.dp
            }
            Box(
                Modifier
                    .width(PreviewBarWidth)
                    .height(height)
                    .background(zoneColors.colorFor(zone), RoundedCornerShape(2.dp)),
            )
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
