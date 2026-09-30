package com.violinjourney.app.feature.live.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_mark_done_description
import com.violinjourney.app.shared.resources.block_mark_played_description
import com.violinjourney.app.shared.resources.block_running
import com.violinjourney.app.shared.resources.block_section_today
import org.jetbrains.compose.resources.stringResource

// The list of a sheet of choice from the repertoire (spec 3.36.6, 5.29 R6; live.html, 4).
private val HeadTop = 16.dp
private val HeadBottom = 4.dp
private val HeadGap = 8.dp
private val RowMinHeight = 56.dp
private val RowPaddingVertical = 8.dp
private val RowPaddingSide = 12.dp
private val RowGap = 12.dp
private val RowCorner = 14.dp
private val RowShape = RoundedCornerShape(RowCorner)
private val SelectedEdge = 1.5.dp
private val ComposerTop = 2.dp
private val PillHeight = 28.dp
private val PillPadding = 10.dp
private val PillGap = 4.dp
private val PillEdge = 1.5.dp
private val PillTick = 15.dp
private val Capsule = RoundedCornerShape(percent = 50)
private const val TABULAR_FIGURES = "tnum"

/**
 * The rows of a list of choice from the repertoire (spec 3.36.6, 5.29 R6) — a common part, not only Live's: «Что играем» picks one
 * element on it (R6), the programme of an event several (R9, spec 3.35). The [sections] in the order they come, each under its label
 * with «сегодня N» at the right ([PickerSectionHead]); a section without elements is left out. A row ([PickerRow]) is chosen when
 * [isSelected] says so and calls [onPick] when pressed — unless not [pickable] (in «Что играем» the element whose block runs); at its
 * end stands [trailing], the pill of today by default ([TodayPill]). The order is theirs: marks never move anything. The caller gives
 * the list its sides — 8, so that the rows' ground reaches 12 past the words, and the words stand 20 in, under the labels.
 */
fun LazyListScope.pickerSections(
    sections: List<PickerSection>,
    isSelected: (PickerPiece) -> Boolean,
    onPick: (PickerPiece) -> Unit,
    pickable: (PickerPiece) -> Boolean = { it.today != TodayMark.Running },
    trailing: @Composable (PickerPiece) -> Unit = { TodayPill(it.today) },
) {
    sections.filter { it.pieces.isNotEmpty() }.forEach { section ->
        item(key = "head-" + section.ref, contentType = HEAD) { PickerSectionHead(section) }
        items(section.pieces, key = { it.id }, contentType = { PIECE }) { piece ->
            PickerRow(
                piece = piece,
                selected = isSelected(piece),
                enabled = pickable(piece),
                onClick = { onPick(piece) },
                trailing = { trailing(piece) },
            )
        }
    }
}

private const val HEAD = "head"
private const val PIECE = "piece"

/**
 * The label of a section (spec 5.29 R1, `SectionLabel`: capitals, 13 sp / 700) and at its right «сегодня 1» — 13 sp / 600, as
 * written — when not zero; 16 above, 4 below. For TalkBack one heading: «Гаммы, сегодня 1» (the label as written, not in capitals).
 */
@Composable
fun PickerSectionHead(section: PickerSection, modifier: Modifier = Modifier) {
    val name = sectionName(section.ref, section.name)
    val today = if (section.doneToday > 0) stringResource(Res.string.block_section_today, section.doneToday) else null
    val said = listOfNotNull(name, today).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = RowPaddingSide, end = RowPaddingSide, top = HeadTop, bottom = HeadBottom)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = said
            },
        horizontalArrangement = Arrangement.spacedBy(HeadGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(name, Modifier.weight(1f))
        if (today != null) {
            Text(
                text = today,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/**
 * A row of the list (spec 3.36.6, 5.29 R6): 56 at the least, 8 / 12 of padding, corner 14; the title in one line with an ellipsis
 * (16 sp / 700), the composer under it (13 sp), [trailing] at the end. The chosen one — the soft accent inside an outline of the
 * accent of 1.5. A radio button for TalkBack, «выбрано» when chosen, read as one phrase of its parts: the title, the composer, today.
 * Not [enabled] — it is not pressed (the element whose block runs, spec 3.28).
 */
@Composable
fun PickerRow(
    piece: PickerPiece,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { TodayPill(piece.today) },
) {
    val colors = MaterialTheme.colorScheme
    val said = listOfNotNull(piece.title, piece.composer, todaySaid(piece.today)).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(RowShape)
            .then(if (selected) Modifier.background(ViolinTheme.accentSoft).border(SelectedEdge, colors.primary, RowShape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = said }
            .padding(horizontal = RowPaddingSide, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = piece.title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
            )
            piece.composer?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = ComposerTop),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
        trailing()
    }
}

/** What TalkBack says of today: «сыгран сегодня, 15 минут», «сегодня 7 минут», «идёт»; nothing for an element not played. */
@Composable
private fun todaySaid(mark: TodayMark): String? = when (mark) {
    TodayMark.None -> null
    is TodayMark.Played -> stringResource(Res.string.block_mark_played_description, Formats.minutesInWords(mark.ms))
    is TodayMark.Done -> stringResource(Res.string.block_mark_done_description, Formats.minutesInWords(mark.ms))
    TodayMark.Running -> stringResource(Res.string.block_running)
}

/**
 * «Сегодня» of an element (spec 3.28, 3.36.6) — by shape, not only by colour (principle 5), a pill of 28 in 13 sp / 700 tabular
 * figures: played to its goal — the ground one step lighter than the sheet, the tick in the brass of «сделано» ([ViolinTheme.done])
 * and «15 мин»; played short of it — «7 мин» in the second level of text, no ground; its block runs — «идёт» in an outline of 1.5.
 * Nothing for an element not played today.
 */
@Composable
fun TodayPill(mark: TodayMark, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
    when (mark) {
        TodayMark.None -> Unit
        is TodayMark.Done -> Row(
            modifier = modifier
                .height(PillHeight)
                .background(colors.surfaceContainerHigh, Capsule)
                .padding(horizontal = PillPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillGap),
        ) {
            AppIcon(AppIcons.Check, contentDescription = null, size = PillTick, tint = ViolinTheme.done)
            Text(Formats.minutesInWords(mark.ms), color = colors.onSurface, maxLines = 1, softWrap = false, style = style)
        }
        is TodayMark.Played -> Box(modifier.height(PillHeight).padding(horizontal = PillPadding), contentAlignment = Alignment.Center) {
            Text(Formats.minutesInWords(mark.ms), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
        }
        TodayMark.Running -> Box(
            modifier = modifier
                .height(PillHeight)
                .border(PillEdge, colors.onSurfaceVariant, Capsule)
                .padding(horizontal = PillPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(Res.string.block_running), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
        }
    }
}
