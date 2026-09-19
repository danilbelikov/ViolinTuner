package com.example.violintuner.feature.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.icons.IconSizes
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.DayLabel
import com.example.violintuner.feature.repertoire.components.SheetThumb
import com.example.violintuner.feature.repertoire.components.StatusChip
import com.example.violintuner.feature.repertoire.components.THUMB_DIM
import com.example.violintuner.feature.repertoire.components.statusLabel

private val SectionSpacing = 16.dp
private val CardSpacing = 8.dp
private val CardCorner = 16.dp
private val CardMinHeight = 96.dp
private val ThumbWidth = 54.dp
private val ThumbHeight = 72.dp
private val ThumbCorner = 6.dp
private val AddHeight = 48.dp
private val AddCorner = 24.dp
private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private const val TABULAR_FIGURES = "tnum"
private const val EMPTY_HEIGHT_FRACTION = 0.7f

/** The «Репертуар» section of «Записи» (spec 3.15, handoff 13a2, 13b): items of the screen's one lazy list. */
fun LazyListScope.repertoireItems(state: RepertoireState, onIntent: (RepertoireIntent) -> Unit) {
    when {
        state.loading -> Unit
        state.totalCount == 0 -> item(key = "repertoireEmpty") {
            EmptyRepertoire(onAdd = { onIntent(RepertoireIntent.AddClicked) }, modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION))
        }
        else -> {
            item(key = "repertoireAdd") {
                AddButton(onClick = { onIntent(RepertoireIntent.AddClicked) }, modifier = Modifier.padding(top = SectionSpacing))
            }
            item(key = "repertoireFilters") {
                StatusFilters(
                    selected = state.filter,
                    onSelect = { onIntent(RepertoireIntent.FilterSelected(it)) },
                    modifier = Modifier.padding(top = SectionSpacing, bottom = SectionSpacing - CardSpacing),
                )
            }
            items(state.cards, key = { "piece-${it.id}" }) { card ->
                PieceCardRow(card, onClick = { onIntent(RepertoireIntent.PieceClicked(card.id)) }, modifier = Modifier.padding(top = CardSpacing))
            }
            if (state.cards.isEmpty() && state.filter != null) {
                item(key = "repertoireEmptyFilter") {
                    EmptyFilter(state.filter, onShowAll = { onIntent(RepertoireIntent.FilterSelected(null)) })
                }
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(AddHeight),
        shape = RoundedCornerShape(AddCorner),
        border = androidx.compose.foundation.BorderStroke(1.dp, SolidColor(colors.outlineVariant)),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.primary) {
            IconLabel(
                icon = AppIcons.Plus,
                text = stringResource(R.string.repertoire_add),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun StatusFilters(selected: PieceStatus?, onSelect: (PieceStatus?) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Four chips with long words: on a narrow screen the row scrolls rather than wraps.
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<PieceStatus?>(null) + PieceStatus.entries).forEach { status ->
            val isSelected = status == selected
            val shape = RoundedCornerShape(ChipCorner)
            Box(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(shape)
                    .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                    .border(1.dp, if (isSelected) colors.primaryContainer else colors.outlineVariant, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(status) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = status?.let { statusLabel(it) } ?: stringResource(R.string.history_filter_all),
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun PieceCardRow(card: PieceCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetThumb(card.thumbPath, ThumbWidth, ThumbHeight, ThumbCorner, dim = THUMB_DIM)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = card.title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            if (card.composer.isNotEmpty()) {
                Text(
                    text = card.composer,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(card.status)
                KeyAndTempo(
                    keyName = card.keyName,
                    tempoBpm = card.tempoBpm,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
        LastTake(card)
    }
}

/** Score of the latest take in its zone color with the day and the number of takes; a plain dash without takes. */
@Composable
private fun LastTake(card: PieceCard) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = card.lastScore?.let { stringResource(R.string.repertoire_score, it) } ?: stringResource(R.string.practice_no_value),
            color = card.lastScoreZone?.let { ViolinTheme.zoneColors.colorFor(it) } ?: colors.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        val caption = listOfNotNull(card.lastDay?.let { dayLabel(it) }, takesLabel(card.takes).takeIf { card.takes > 0 })
        if (caption.isNotEmpty()) {
            Text(
                text = caption.joinToString(stringResource(R.string.dot_separator)),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

@Composable
private fun EmptyRepertoire(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.repertoire_empty),
            modifier = Modifier.widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        AddButton(onAdd, Modifier.widthIn(max = 280.dp))
    }
}

@Composable
private fun EmptyFilter(filter: PieceStatus, onShowAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.repertoire_empty_filter, statusLabel(filter)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onShowAll) { Text(stringResource(R.string.repertoire_show_all)) }
    }
}

/** "G-dur · ♩ = 96", either half alone, or null when the piece has neither. */
/**
 * «G-dur · [metronome] 96» — only what the piece has; nothing at all when it has neither. The
 * tempo is the metronome of the icon set rather than the glyph ♩: that one comes from a fallback
 * font in another weight (spec 3.16).
 */
@Composable
fun KeyAndTempo(keyName: String?, tempoBpm: Int?, style: TextStyle, modifier: Modifier = Modifier) {
    if (keyName == null && tempoBpm == null) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (keyName != null) {
            Text(
                text = if (tempoBpm != null) keyName + stringResource(R.string.dot_separator) else keyName,
                color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (tempoBpm != null) {
            val description = stringResource(R.string.piece_tempo_description, tempoBpm)
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TempoIconGap),
            ) {
                AppIcon(AppIcons.Metronome, contentDescription = null, tint = color, size = IconSizes.InText)
                Text(text = tempoBpm.toString(), color = color, maxLines = 1, style = style)
            }
        }
    }
}

private val TempoIconGap = 5.dp

@Composable
fun dayLabel(day: DayLabel): String = when (day) {
    DayLabel.Today -> stringResource(R.string.history_day_today)
    DayLabel.Yesterday -> stringResource(R.string.history_day_yesterday)
    is DayLabel.On -> Formats.dayAndShortMonth(day.date)
}

/** «1 дубль», «2 дубля», «6 дублей». */
@Composable
fun takesLabel(count: Int): String = stringResource(
    Formats.pluralRu(count, R.string.takes_one, R.string.takes_few, R.string.takes_many), count,
)
