package com.violinjourney.app.feature.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.repertoire.components.KindTile
import com.violinjourney.app.feature.repertoire.components.MetaLine
import com.violinjourney.app.feature.repertoire.components.SheetThumb
import com.violinjourney.app.feature.repertoire.components.StatusMark
import com.violinjourney.app.feature.repertoire.components.metaWords
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.feature.repertoire.scale.KeySignatureTile
import com.violinjourney.app.feature.repertoire.sections.SectionBar
import com.violinjourney.app.feature.repertoire.sections.countDescription
import com.violinjourney.app.feature.repertoire.sections.sectionCountLabel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.repertoire_add
import com.violinjourney.app.shared.resources.repertoire_empty
import com.violinjourney.app.shared.resources.repertoire_empty_filter
import com.violinjourney.app.shared.resources.repertoire_empty_filter_hint
import com.violinjourney.app.shared.resources.repertoire_has_best_description
import com.violinjourney.app.shared.resources.repertoire_last_take_description
import com.violinjourney.app.shared.resources.repertoire_no_takes
import com.violinjourney.app.shared.resources.repertoire_show_all
import com.violinjourney.app.shared.resources.section_add_etude
import com.violinjourney.app.shared.resources.section_add_own
import com.violinjourney.app.shared.resources.section_add_scale
import com.violinjourney.app.shared.resources.section_add_stroke
import com.violinjourney.app.shared.resources.section_empty_etudes
import com.violinjourney.app.shared.resources.section_empty_own
import com.violinjourney.app.shared.resources.section_empty_scales
import com.violinjourney.app.shared.resources.section_empty_strokes
import com.violinjourney.app.shared.resources.takes_few
import com.violinjourney.app.shared.resources.takes_many
import com.violinjourney.app.shared.resources.takes_one
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The list of a section (spec 3.36.4, 5.29 R4; repertoire.html 2).
private val ScreenPadding = 16.dp
private val CountTop = 2.dp
private val CountGap = 12.dp
private val ChipGap = 8.dp
private val CardSpacing = 10.dp
private val CardMinHeight = 84.dp
private val CardPadding = 12.dp
private val CardGap = 12.dp
private val ThumbWidth = 52.dp
private val ThumbHeight = 64.dp
private val StatusTop = 6.dp
private val BestStar = 12.dp
private val EmptyFilterVertical = 40.dp
private val EmptyFilterButtonTop = 16.dp

/**
 * The visible chip is 40, its touch 48 (5.29 R1): the row of chips stands 10 under the count and 0 over the first card, so the chips
 * are seen 14 under the count and 4 + 10 over the card (spec: «сверху 14, снизу 4»).
 */
private val ChipTouchSlack = 4.dp
private val ChipsTop = 14.dp - ChipTouchSlack
private const val TABULAR_FIGURES = "tnum"
private const val EMPTY_HEIGHT_FRACTION = 0.7f

/**
 * The elements of one section (spec 3.15, 3.22, 3.36.4): items of the lazy list under its header. First the count — the shares as a
 * bar across the width and «выучено 2 из 6» at its end; then the filter chips with their numbers and the cards, freshest first. An
 * empty section has no chips: its old words in the middle, «Добавить …» only in the bottom zone. Nothing while the data is read.
 */
fun LazyListScope.repertoireItems(state: RepertoireState, onIntent: (RepertoireIntent) -> Unit) {
    if (state.loading) return
    item(key = "sectionCount") { SectionCountRow(state.section, state.count, Modifier.padding(top = CountTop)) }
    if (state.totalCount == 0) {
        item(key = "repertoireEmpty") { EmptySection(state.section, Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION)) }
        return
    }
    item(key = "repertoireFilters") {
        StatusFilters(
            selected = state.filter,
            all = state.totalCount,
            count = state.count,
            onSelect = { onIntent(RepertoireIntent.FilterSelected(it)) },
            modifier = Modifier.padding(top = ChipsTop),
        )
    }
    items(state.cards, key = { "piece-${it.id}" }) { card ->
        PieceCardRow(card, onClick = { onIntent(RepertoireIntent.PieceClicked(card.id)) }, modifier = Modifier.padding(top = CardSpacing))
    }
    val filter = state.filter
    if (state.cards.isEmpty() && filter != null) {
        item(key = "repertoireEmptyFilter") { EmptyFilter(filter, onShowAll = { onIntent(RepertoireIntent.FilterSelected(null)) }) }
    }
}

/**
 * The count of a section under its header (spec 3.36.4): the bar of its shares across what is left of the width and «выучено 2 из
 * 6» at its end, 12 apart; an empty section — the dashed outline and «пока пусто». One phrase for TalkBack — the count and the
 * shares.
 */
@Composable
private fun SectionCountRow(section: SectionRef, count: SectionCount, modifier: Modifier = Modifier) {
    val description = countDescription(section, count)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CountGap),
    ) {
        SectionBar(count, Modifier.weight(1f))
        Text(
            text = sectionCountLabel(count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * The filter (spec 3.36.4): the filter chips of R1 with their numbers — «Все 6 · Разбираю 1 · Учу 3 · В репертуаре 2»; a chip of
 * nought stands too, so an empty filter is not opened in vain. The ribbon scrolls sideways and runs to the edges of the screen.
 */
@Composable
private fun StatusFilters(selected: PieceStatus?, all: Int, count: SectionCount, onSelect: (PieceStatus?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .bleed(ScreenPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        AppChip(text = stringResource(Res.string.history_filter_all), selected = selected == null, onClick = { onSelect(null) }, count = all)
        PieceStatus.entries.forEach { status ->
            val number = when (status) {
                PieceStatus.READING -> count.reading
                PieceStatus.LEARNING -> count.learning
                PieceStatus.IN_REPERTOIRE -> count.learned
            }
            AppChip(text = statusLabel(status), selected = status == selected, onClick = { onSelect(status) }, count = number)
        }
    }
}

/** Wider than the column by [side] on each end: a ribbon that scrolls under the fields of the screen to its edges. */
private fun Modifier.bleed(side: Dp): Modifier = layout { measurable, constraints ->
    val extra = side.roundToPx() * 2
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wide)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-side.roundToPx(), 0) }
}

/**
 * An element (spec 3.36.4, 5.29 R4): the first page of its music without the film, or the tile of its kind; the name on up to two
 * lines; one line of what is set — «И. С. Бах · G-dur · [metronome] 100», a scale its kind and tempo, a stroke its tempo; the status as
 * a dot and a word; at the right the day of the last take with a star when one is marked as the best, and how many takes (3.21).
 * The whole card is one target and one phrase: «Менуэт соль мажор, И. С. Бах, G-dur, темп 100, Учу, последний дубль 27 сентября,
 * 2 дубля, есть лучший дубль».
 */
@Composable
private fun PieceCardRow(card: PieceCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val parts = PieceMeta.parts(card.composer, card.keyName, card.tempoBpm, card.scale?.kind, card.stroke)
    val description = cardDescription(card, metaWords(parts))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(card)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
            )
            MetaLine(parts, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), modifier = Modifier.padding(top = 2.dp))
            StatusMark(card.status, Modifier.padding(top = StatusTop))
        }
        LastTake(card, Modifier.align(Alignment.Top))
    }
}

/**
 * The cover of an element: its first page without the film — the list is where one looks for the music (5.29 R4); without a photo
 * the tile of its kind: the clef with the key signature of a scale, the page of an étude, the bow of a stroke, a note otherwise.
 */
@Composable
private fun Cover(card: PieceCard) {
    val scale = card.scale
    when {
        card.thumbPath != null -> SheetThumb(card.thumbPath, ThumbWidth, ThumbHeight, CoverCorner, dim = 0f)
        scale != null -> KeySignatureTile(scale, Modifier.size(ThumbWidth, ThumbHeight), CoverCorner)
        card.stroke -> KindTile(AppIcons.Bow, ThumbWidth, ThumbHeight, AppShapes.S)
        card.etude -> KindTile(AppIcons.Etude, ThumbWidth, ThumbHeight, AppShapes.S)
        else -> KindTile(AppIcons.NoteOne, ThumbWidth, ThumbHeight, AppShapes.S)
    }
}

private val CoverCorner = 12.dp

@Composable
private fun cardDescription(card: PieceCard, meta: List<String>): String {
    val date = card.lastDate
    val takes = if (date == null) {
        listOf(stringResource(Res.string.repertoire_no_takes))
    } else {
        listOf(stringResource(Res.string.repertoire_last_take_description, Formats.recordDate(date, card.lastDateOtherYear)), takesLabel(card.takes))
    }
    val best = if (card.hasBest) stringResource(Res.string.repertoire_has_best_description) else null
    return (listOf(card.title) + meta + statusLabel(card.status) + takes + listOfNotNull(best)).joinToString(", ")
}

/**
 * When the element was last played and how often — words only (spec 3.21): two lines by the right edge, 13 sp, «нет дублей» without
 * takes. The card is about the element, not about how it goes; a star by the date says one of its takes is marked as the best.
 */
@Composable
private fun LastTake(card: PieceCard, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier = modifier.padding(top = 2.dp), horizontalAlignment = Alignment.End) {
        val date = card.lastDate
        if (date == null) {
            Text(stringResource(Res.string.repertoire_no_takes), color = colors.onSurfaceVariant, maxLines = 1, style = style)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (card.hasBest) AppIcon(AppIcons.Star, contentDescription = null, tint = colors.primary, size = BestStar)
                Text(Formats.recordDate(date, card.lastDateOtherYear), color = colors.onSurfaceVariant, maxLines = 1, style = style)
            }
            Text(takesLabel(card.takes), color = colors.onSurfaceVariant, maxLines = 1, style = style)
        }
    }
}

/** An empty section (spec 3.36.4): its own words in the middle, no chips and no button — «Добавить …» is in the bottom zone. */
@Composable
private fun EmptySection(section: SectionRef, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(emptyTextOf(section)),
            modifier = Modifier.widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
    }
}

/**
 * Nothing under the filter (spec 3.36.4): «Ничего со статусом «Выучено».», one line for every section and status — the status is
 * changed on the screen of the element — and «Показать все» as a soft button.
 */
@Composable
private fun EmptyFilter(filter: PieceStatus, onShowAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = EmptyFilterVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.repertoire_empty_filter, statusLabel(filter)),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            text = stringResource(Res.string.repertoire_empty_filter_hint),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
        AppButton(
            text = stringResource(Res.string.repertoire_show_all),
            onClick = onShowAll,
            modifier = Modifier.padding(top = EmptyFilterButtonTop - 6.dp),
            style = AppButtonStyle.Soft,
        )
    }
}

/** «1 дубль», «2 дубля», «6 дублей». */
@Composable
fun takesLabel(count: Int): String = stringResource(
    Formats.plural(count, Res.string.takes_one, Res.string.takes_few, Res.string.takes_many), count,
)

/** «Добавить произведение», «Добавить гамму», «Добавить этюд», «Добавить штрих», «Добавить» — the main button of a section's list. */
internal fun addLabelOf(section: SectionRef): StringResource = when (section) {
    is SectionRef.Custom -> Res.string.section_add_own
    is SectionRef.BuiltIn -> when (section.section) {
        PieceSection.PIECES -> Res.string.repertoire_add
        PieceSection.SCALES -> Res.string.section_add_scale
        PieceSection.ETUDES -> Res.string.section_add_etude
        PieceSection.STROKES -> Res.string.section_add_stroke
    }
}

private fun emptyTextOf(section: SectionRef): StringResource = when (section) {
    is SectionRef.Custom -> Res.string.section_empty_own
    is SectionRef.BuiltIn -> when (section.section) {
        PieceSection.PIECES -> Res.string.repertoire_empty
        PieceSection.SCALES -> Res.string.section_empty_scales
        PieceSection.ETUDES -> Res.string.section_empty_etudes
        PieceSection.STROKES -> Res.string.section_empty_strokes
    }
}
