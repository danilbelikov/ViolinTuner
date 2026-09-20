package com.example.violintuner.feature.repertoire

import com.example.violintuner.feature.repertoire.scale.scaleSubtitle
import com.example.violintuner.feature.repertoire.scale.KeySignatureTile
import androidx.compose.foundation.layout.size
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.domain.repertoire.PieceSection
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
import androidx.compose.foundation.layout.width
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

/** The elements of one section of the repertoire (spec 3.15, 3.22; handoff 13b, 24c): items of a lazy list. */
fun LazyListScope.repertoireItems(state: RepertoireState, onIntent: (RepertoireIntent) -> Unit) {
    when {
        state.loading -> Unit
        state.totalCount == 0 -> item(key = "repertoireEmpty") {
            EmptyRepertoire(state.section, onAdd = { onIntent(RepertoireIntent.AddClicked) }, modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION))
        }
        else -> {
            item(key = "repertoireAdd") {
                AddButton(state.section, onClick = { onIntent(RepertoireIntent.AddClicked) }, modifier = Modifier.padding(top = SectionSpacing))
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
private fun AddButton(section: SectionRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                text = stringResource(addLabelOf(section)),
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
        when {
            // a drawn scale has notes of its own: the clef with its key signature is known from afar, a first bar in miniature is not
            card.scale != null && card.thumbPath == null -> KeySignatureTile(card.scale, Modifier.size(ThumbWidth, ThumbHeight), ThumbCorner)
            // a row of strokes looks even, not "without a photo"
            card.stroke && card.thumbPath == null -> StrokeTile(Modifier.size(ThumbWidth, ThumbHeight), ThumbCorner)
            else -> SheetThumb(card.thumbPath, ThumbWidth, ThumbHeight, ThumbCorner, dim = THUMB_DIM)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = card.title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            val second = card.scale?.let { scaleSubtitle(it) } ?: card.composer
            if (second.isNotEmpty()) {
                Text(
                    text = second,
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
                    // the key of a scale is its very name
                    keyName = card.keyName.takeIf { card.scale == null },
                    tempoBpm = card.tempoBpm,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
        LastTake(card)
    }
}

/**
 * When the piece was last played and how often — words only (spec 3.21, handoff 22g2): two lines by
 * the right edge, «нет дублей» without takes. The card is about the piece, not about how it goes;
 * a star by the date says one of its takes is marked as the best.
 */
@Composable
private fun LastTake(card: PieceCard) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val date = card.lastDate
        if (date == null) {
            Text(stringResource(R.string.repertoire_no_takes), color = colors.onSurfaceVariant, maxLines = 1, style = style)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (card.hasBest) AppIcon(AppIcons.Star, contentDescription = stringResource(R.string.take_best), tint = colors.primary, size = BestStar)
                Text(Formats.recordDate(date, card.lastDateOtherYear), color = colors.onSurfaceVariant, maxLines = 1, style = style)
            }
            Text(takesLabel(card.takes), color = colors.onSurfaceVariant, maxLines = 1, style = style)
        }
    }
}

private val BestStar = 12.dp

@Composable
private fun EmptyRepertoire(section: SectionRef, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(emptyTextOf(section)),
            modifier = Modifier.widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        AddButton(section, onAdd, Modifier.widthIn(max = 280.dp))
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

/** «1 дубль», «2 дубля», «6 дублей». */
@Composable
fun takesLabel(count: Int): String = stringResource(
    Formats.pluralRu(count, R.string.takes_one, R.string.takes_few, R.string.takes_many), count,
)

private fun addLabelOf(section: SectionRef): Int = when (section) {
    is SectionRef.Custom -> R.string.section_add_own
    is SectionRef.BuiltIn -> when (section.section) {
        PieceSection.PIECES -> R.string.repertoire_add
        PieceSection.SCALES -> R.string.section_add_scale
        PieceSection.ETUDES -> R.string.section_add_etude
        PieceSection.STROKES -> R.string.section_add_stroke
    }
}

private fun emptyTextOf(section: SectionRef): Int = when (section) {
    is SectionRef.Custom -> R.string.section_empty_own
    is SectionRef.BuiltIn -> when (section.section) {
        PieceSection.PIECES -> R.string.repertoire_empty
        PieceSection.SCALES -> R.string.section_empty_scales
        PieceSection.ETUDES -> R.string.section_empty_etudes
        PieceSection.STROKES -> R.string.section_empty_strokes
    }
}

/** A bow stroke has no cover as a rule: a quiet tile with a bow stands where the photo would (handoff 24c). */
@Composable
private fun StrokeTile(modifier: Modifier, corner: androidx.compose.ui.unit.Dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(colors.surfaceContainerHigh, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) { AppIcon(AppIcons.Bow, contentDescription = null, tint = colors.outline, size = 30.dp) }
}
