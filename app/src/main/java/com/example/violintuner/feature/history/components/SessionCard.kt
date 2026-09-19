package com.example.violintuner.feature.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconSizes
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.DayLabel
import com.example.violintuner.feature.history.HistoryCard
import java.time.ZoneId

private val CardCorner = 16.dp
private val ScoreColumnWidth = 52.dp
private val PreviewBarWidth = 4.dp
private val PreviewHeight = 28.dp
private const val TABULAR_FIGURES = "tnum"

/** Card of one session: the list of «Записи» (spec 3.11, handoff 4c) and the day's records on the practice screen. */
@Composable
fun SessionCard(card: HistoryCard, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier, actions: CardActions? = null) {
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
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (actions != null && card.hasAudio) 4.dp else 16.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sessionTitle(card.title, card.pieceTitle, card.startedAtEpochMs, zone),
                    modifier = Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                if (card.pieceTitle != null) TakeChip()
            }
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
        if (actions != null && card.hasAudio) CardMenuButton(card.id, actions)
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

/**
 * What a session is called (spec 3.9, 3.15): its own name when it was given one; otherwise a
 * take is named after its piece — «Менуэт · 18 сентября» — and a free session is «Сессия · …».
 */
@Composable
fun sessionTitle(title: String?, pieceTitle: String?, startedAtEpochMs: Long, zone: ZoneId): String {
    val date = Formats.dayAndMonth(startedAtEpochMs, zone)
    return title
        ?: pieceTitle?.let { stringResource(R.string.session_take_title, it, date) }
        ?: stringResource(R.string.session_default_title, date)
}

/** The quiet «дубль» mark of a session that belongs to a piece (handoff 13a1). */
@Composable
private fun TakeChip() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(colors.surfaceContainerHigh, RoundedCornerShape(TakeChipCorner))
            .padding(start = 4.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppIcon(AppIcons.Note, contentDescription = null, tint = colors.onSurfaceVariant, size = IconSizes.InText)
        Text(
            text = stringResource(R.string.session_take_chip),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        )
    }
}

private val TakeChipCorner = 6.dp
