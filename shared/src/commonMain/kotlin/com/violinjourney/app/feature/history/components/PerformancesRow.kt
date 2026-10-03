package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.PerformancesLine
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.KindSignPlate
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.history.HistoryPerformances
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.performances_row_in_days_few
import com.violinjourney.app.shared.resources.performances_row_in_days_many
import com.violinjourney.app.shared.resources.performances_row_in_days_one
import com.violinjourney.app.shared.resources.performances_row_last
import com.violinjourney.app.shared.resources.performances_row_next_said
import com.violinjourney.app.shared.resources.performances_row_none
import com.violinjourney.app.shared.resources.performances_row_past_few
import com.violinjourney.app.shared.resources.performances_row_past_many
import com.violinjourney.app.shared.resources.performances_row_past_one
import com.violinjourney.app.shared.resources.performances_row_said
import com.violinjourney.app.shared.resources.performances_title
import com.violinjourney.app.shared.resources.practice_day_today
import com.violinjourney.app.shared.resources.practice_day_tomorrow
import com.violinjourney.app.shared.resources.practice_pair_description
import org.jetbrains.compose.resources.stringResource

/**
 * The row «Выступления» of «Записи» (spec 3.36.9, 5.29 R9; events-views.html 8, practice-sheets.html 11): the sign of «Выступление» on
 * a plate of the colour of the kind (not the house of records.html), «Выступления» and the caption of how things stand — ahead: the name
 * of the nearest in one line with an ellipsis and, under it, whole, wrapping where it must, «через 27 дней · 3 прошло» («сегодня · …»,
 * «завтра · …»; without any over — the term alone); only those over: «3 прошло · последнее 24 октября»; none: «Концерты, экзамены — с
 * записями». The chevron at its end. One button for TalkBack: «Выступления: Осенний концерт через 27 дней, 3 прошло». No motion of its
 * own: the tab lays it, takes it away while picking, and gives it back.
 */
@Composable
fun PerformancesRow(performances: HistoryPerformances, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ground = colors.surfaceContainer
    val dot = stringResource(Res.string.dot_separator)
    val caption = captionOf(performances, dot)
    val said = stringResource(Res.string.performances_row_said, caption.said)
    val shape = RoundedCornerShape(EventsDimens.RecordsRowCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.RecordsRowMin)
            .clip(shape)
            .background(ground, shape)
            .clickable(role = Role.Button, onClick = onClick)
            // the whole row, its fields too: one button, one sentence
            .clearAndSetSemantics { contentDescription = said }
            .padding(horizontal = EventsDimens.RecordsRowPaddingH, vertical = EventsDimens.RecordsRowPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindSignPlate(
            look = performances.look, plate = EventsDimens.RecordsRowPlate, corner = EventsDimens.RecordsRowPlateCorner,
            sign = EventsDimens.RecordsRowSign, ground = ground,
        )
        Spacer(Modifier.width(EventsDimens.RecordsRowGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.performances_title),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = EventsDimens.RecordsRowTitle,
                    lineHeight = EventsDimens.RecordsRowTitleHeight,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            val style = captionStyle()
            // the name of the nearest: one line, its end an ellipsis — the term under it is never cut
            caption.name?.let { name -> Text(text = name, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style) }
            Text(text = caption.words, color = colors.onSurfaceVariant, style = style)
        }
        Spacer(Modifier.width(EventsDimens.RecordsRowGap))
        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = EventsDimens.RecordsRowChevron, tint = ViolinTheme.textTertiary)
    }
}

/** What the row says: [name] — the nearest ahead, on a line of its own; [words] — the line that is never cut; [said] — for TalkBack. */
private class Caption(val name: String?, val words: String, val said: String)

@Composable
private fun captionOf(performances: HistoryPerformances, dot: String): Caption = when (val line = performances.line) {
    is PerformancesLine.Ahead -> {
        val nearest = line.nearest
        // the kind is the built-in «Выступление»: it is there whether the kinds are read or not
        val name = eventNameOf(EventName.of(nearest.title, nearest.kind, emptyList()))
        val term = termOf(line.days)
        val past = line.pastCount.takeIf { it > 0 }?.let { pastOf(it) }
        val next = stringResource(Res.string.performances_row_next_said, name, term)
        Caption(
            name = name,
            words = listOfNotNull(term, past).joinToString(dot),
            said = past?.let { stringResource(Res.string.practice_pair_description, next, it) } ?: next,
        )
    }
    is PerformancesLine.OnlyPast -> {
        val past = pastOf(line.count)
        // the date whole: the words wrap before it, never between its day and its month (5.29 R9, review of stage 99)
        val last = stringResource(Res.string.performances_row_last, Formats.recordDateWhole(line.lastDate, withYear = line.lastDate.year != performances.today.year))
        Caption(name = null, words = past + dot + last, said = stringResource(Res.string.practice_pair_description, past, last))
    }
    PerformancesLine.None -> stringResource(Res.string.performances_row_none).let { Caption(name = null, words = it, said = it) }
}

/** «через 27 дней», «сегодня», «завтра» (plan D38). */
@Composable
private fun termOf(days: Int): String = when (days) {
    0 -> stringResource(Res.string.practice_day_today)
    1 -> stringResource(Res.string.practice_day_tomorrow)
    else -> stringResource(
        Formats.plural(days, Res.string.performances_row_in_days_one, Res.string.performances_row_in_days_few, Res.string.performances_row_in_days_many),
        days,
    )
}

/** «3 прошло». */
@Composable
private fun pastOf(count: Int): String =
    stringResource(Formats.plural(count, Res.string.performances_row_past_one, Res.string.performances_row_past_few, Res.string.performances_row_past_many), count)

@Composable
private fun captionStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontSize = EventsDimens.RecordsRowCaption,
    lineHeight = EventsDimens.RecordsRowCaptionHeight,
    fontFeatureSettings = TABULAR_FIGURES,
)

private const val TABULAR_FIGURES = "tnum"
