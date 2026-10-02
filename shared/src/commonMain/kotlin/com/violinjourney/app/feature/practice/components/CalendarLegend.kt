package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.MiniSign
import com.violinjourney.app.feature.events.eventKindName
import com.violinjourney.app.feature.events.eventKindWord
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_events_hint
import com.violinjourney.app.shared.resources.practice_kinds_legend_description
import com.violinjourney.app.shared.resources.practice_pair_description
import org.jetbrains.compose.resources.stringResource

/**
 * The legend of the kinds the events of the month shown are of (spec 3.36.9, 5.29 R9; events-kinds.html, 4–5): the mini sign of 12 in
 * the colour of the kind and its name, in the order of the form — the built-in kinds, then those of one's own by the alphabet. A line
 * that wraps, never cuts; [column] — one under another beside the grid, in landscape where there is room. One description to a reader,
 * «Виды месяца: урок, выступление, оркестр»: the signs say nothing apart.
 */
@Composable
fun KindLegend(kinds: List<EventKind>, column: Boolean, modifier: Modifier = Modifier) {
    var said: String? = null
    for (kind in kinds) {
        val word = eventKindWord(kind)
        said = said?.let { stringResource(Res.string.practice_pair_description, it, word) } ?: word
    }
    val description = stringResource(Res.string.practice_kinds_legend_description, said.orEmpty())
    val silent = Modifier.clearAndSetSemantics { contentDescription = description }
    if (column) {
        Column(modifier.then(silent), verticalArrangement = Arrangement.spacedBy(EventsDimens.LegendColumnGap)) {
            kinds.forEach { LegendKind(it) }
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth().then(silent),
            horizontalArrangement = Arrangement.spacedBy(EventsDimens.LegendGap),
            verticalArrangement = Arrangement.spacedBy(EventsDimens.LegendLineGap),
        ) {
            kinds.forEach { LegendKind(it) }
        }
    }
}

/** One kind of the legend: its mini sign, 6, its name 13 sp, 600, in the secondary text. */
@Composable
private fun LegendKind(kind: EventKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EventsDimens.LegendSignGap)) {
        MiniSign(kind.look, EventsDimens.LegendSign)
        Text(
            text = eventKindName(kind),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = EventsDimens.LegendText, lineHeight = EventsDimens.LegendLineHeight, fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/**
 * The hint under the calendar while the app has no event at all (spec 3.36.9; events-views.html, 4): a quiet line with the calendar —
 * no button, no cross, no touch; it explains the gesture that is there already. The first event takes its place with the legend; it
 * comes back when every event is gone. A reader hears its words.
 */
@Composable
fun EventsHint(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EventsDimens.HintIconGap)) {
        AppIcon(
            icon = AppIcons.Calendar,
            contentDescription = null,
            modifier = Modifier.padding(top = EventsDimens.HintIconTop),
            size = EventsDimens.HintIcon,
            tint = ViolinTheme.textTertiary,
        )
        Text(
            text = stringResource(Res.string.practice_events_hint),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = EventsDimens.HintText, lineHeight = EventsDimens.HintLineHeight, fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
