package com.example.violintuner.feature.history.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
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
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.DayLabel
import com.example.violintuner.feature.history.HistoryCard
import java.time.ZoneId

private val CardCorner = 16.dp
private val CardRing = 1.5.dp
private val BestChipHeight = 22.dp
private val BestChipCorner = 6.dp
private val BestStar = 12.dp
private const val HIGHLIGHT_FADE_MS = 1_500
private val PreviewBarWidth = 4.dp
private val PreviewHeight = 28.dp
private const val TABULAR_FIGURES = "tnum"

/** Card of one session: the list of «Записи» (spec 3.11, 3.18) and the day's records on the practice screen. */
@Composable
fun SessionCard(card: HistoryCard, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier, actions: CardActions? = null) {
    val day = when (val label = card.day) {
        DayLabel.Today -> stringResource(R.string.history_day_today)
        DayLabel.Yesterday -> stringResource(R.string.history_day_yesterday)
        is DayLabel.On -> Formats.dayAndShortMonth(label.date)
    }
    RecordCard(
        card = card,
        title = sessionTitle(card.title, card.pieceTitle, card.startedAtEpochMs, zone),
        meta = stringResource(R.string.history_card_meta, day, Formats.duration(card.durationMs), Formats.signedCents(card.biasCents)),
        take = card.pieceTitle != null,
        onClick = onClick,
        modifier = modifier,
        actions = actions,
    )
}

/**
 * The one card of a recording — a session in «Записи» and on the practice screen, a take on the
 * screen of its piece (spec 3.18, handoff 19a2, 19c1). No score on it: the note tile carries the
 * zone, the bars on the right give the colour a shape. [best] and [highlighted] belong to takes.
 */
@Composable
fun RecordCard(
    card: HistoryCard,
    title: String,
    meta: String,
    take: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    best: Boolean = false,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CardCorner)
    val menu = actions != null && card.hasAudio
    val background by animateColorAsState(
        if (highlighted) ViolinTheme.repertoireColors.takeNew else colors.surfaceContainer, tween(HIGHLIGHT_FADE_MS), label = "recordBackground",
    )
    val ring by animateColorAsState(
        if (highlighted) colors.primary else colors.primary.copy(alpha = 0f), tween(HIGHLIGHT_FADE_MS), label = "recordRing",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(CardRing, ring, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (menu) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordTile(card.scoreZone, take = take, hasAudio = card.hasAudio)
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                )
                if (best) BestChip()
            }
            Text(
                text = meta,
                modifier = Modifier.padding(top = 2.dp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        PreviewBars(card.previewZones)
        if (actions != null && card.hasAudio) CardMenuButton(card.id, actions)
    }
}

/** «★ лучший»: without scores on the cards it is the only pointer to the best take, so it is loud enough (handoff 19c1). */
@Composable
private fun BestChip() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(BestChipHeight)
            .border(1.dp, colors.primary, RoundedCornerShape(BestChipCorner))
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(AppIcons.Star, contentDescription = null, tint = colors.primary, size = BestStar)
        Text(
            text = stringResource(R.string.take_best),
            color = colors.primary,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
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
