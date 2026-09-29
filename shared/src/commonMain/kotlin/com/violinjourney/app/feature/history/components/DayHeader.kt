package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.history.DayGroup
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_day_today
import org.jetbrains.compose.resources.stringResource

private val DayHeaderTop = 8.dp
private val DayHeaderHeight = 22.dp
private val DayHeaderStart = 4.dp
private val TodayChipGap = 8.dp
private val TodayChipCorner = 6.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * The date above a day's recordings (spec 3.21, 5.15, 5.29 R5): «27 сентября, воскресенье» — with the year when it is not this one —
 * 13 sp / 700 in the second level, 22 high (higher only for a large font); today carries the chip «сегодня». Quiet, not a card, not
 * pressed, not sticky; a heading for TalkBack.
 */
@Composable
internal fun DayHeader(group: DayGroup, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = DayHeaderStart, top = DayHeaderTop)
            .heightIn(min = DayHeaderHeight)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(TodayChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formats.recordDayHeader(group.date, withYear = group.cards.first().otherYear),
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
        if (group.today) {
            Text(
                text = stringResource(Res.string.history_day_today),
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
