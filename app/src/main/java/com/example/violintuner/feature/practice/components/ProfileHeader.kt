package com.example.violintuner.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.practice.ProfileHeader
import com.example.violintuner.feature.practice.TrophyBadge

private val AvatarGap = 12.dp
private val NameGap = 2.dp
private val BarHeight = 6.dp
private val BarCorner = 3.dp
private val TouchHeight = 44.dp
private const val TABULAR_FIGURES = "tnum"
private const val TOTAL_LETTER_SPACING_EM = -0.01

/** Sizes of the header that differ between the layouts (handoff `sizes`, frames 11a and 11g). */
@Immutable
data class ProfileHeaderMetrics(
    val avatar: Dp,
    val nameSize: Int,
    val totalSize: Int,
    val captionSize: Int,
    val rowGap: Dp,
    val captionGap: Dp,
    val trophyIcon: Dp,
    val trophyGap: Dp,
    /** Portrait keeps the trophies beside the name; landscape gives them a line with words. */
    val trophiesBeside: Boolean,
) {
    companion object {
        val Portrait = ProfileHeaderMetrics(
            avatar = 56.dp, nameSize = 16, totalSize = 24, captionSize = 12, rowGap = 10.dp, captionGap = 6.dp,
            trophyIcon = 32.dp, trophyGap = 4.dp, trophiesBeside = true,
        )
        val Landscape = ProfileHeaderMetrics(
            avatar = 44.dp, nameSize = 14, totalSize = 20, captionSize = 11, rowGap = 8.dp, captionGap = 4.dp,
            trophyIcon = 24.dp, trophyGap = 2.dp, trophiesBeside = false,
        )
    }
}

/**
 * Who practises and how far they have come: stands where the title of the screen used to
 * (spec 3.13, handoff 11a–11c, 11g). The total time is the main figure and the only accent
 * text of the screen; the level is a caption, not a score.
 */
@Composable
fun ProfileHeader(
    header: ProfileHeader,
    metrics: ProfileHeaderMetrics,
    onProfileClick: () -> Unit,
    onTrophiesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val name = header.name.ifEmpty { stringResource(R.string.progress_no_name) }
    val total = Formats.totalTime(header.totalMs)
    val nameStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = metrics.nameSize.sp, lineHeight = 1.2.em, fontWeight = FontWeight.SemiBold,
    )
    val totalStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = metrics.totalSize.sp, lineHeight = 1.1.em, fontWeight = FontWeight.ExtraBold,
        letterSpacing = TOTAL_LETTER_SPACING_EM.em, fontFeatureSettings = TABULAR_FIGURES,
    )
    val captionStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = metrics.captionSize.sp, fontFeatureSettings = TABULAR_FIGURES,
    )
    val levelText = stringResource(R.string.progress_level, header.level, levelName(header.level))
    val toNextText = header.nextLevel?.let { next ->
        stringResource(R.string.progress_to_next_level, next, Formats.remainingTime(header.toNextLevelMs ?: 0L))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        fun widthOf(text: String, style: TextStyle): Dp = with(density) { measurer.measure(text, style, maxLines = 1).size.width.toDp() }

        // The row of trophies gives way when the name and the total do not fit beside it
        // (a long name, a large system font) — by measure, not by a count of characters.
        val fullRowWidth = metrics.trophyIcon * header.trophyRow.size + metrics.trophyGap * (header.trophyRow.size - 1).coerceAtLeast(0)
        val textWidth = maxOf(widthOf(name, nameStyle), widthOf(total, totalStyle))
        val compactRow = metrics.trophiesBeside &&
            maxWidth - metrics.avatar - AvatarGap * 2 - fullRowWidth < textWidth
        val captionsInOneLine = toNextText == null ||
            widthOf(levelText, captionStyle) + widthOf(toNextText, captionStyle) + AvatarGap <= maxWidth

        Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvatarGap)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = maxOf(metrics.avatar, TouchHeight))
                        .clip(RoundedCornerShape(metrics.avatar / 2))
                        .clickable(onClickLabel = stringResource(R.string.progress_open_profile), role = Role.Button, onClick = onProfileClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AvatarGap),
                ) {
                    Avatar(
                        path = header.avatarPath,
                        fallback = if (header.name.isEmpty()) AvatarFallback.Level(header.level) else AvatarFallback.Letter(initialOf(header.name)),
                        size = metrics.avatar,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NameGap)) {
                        Text(text = name, color = colors.onSurface, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val totalDescription = stringResource(R.string.progress_total_description, total)
                        Text(
                            text = total,
                            color = colors.primary,
                            style = totalStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { contentDescription = totalDescription },
                        )
                    }
                }
                if (metrics.trophiesBeside) {
                    TrophyRow(header, metrics, compact = compactRow, withWords = false, captionStyle = captionStyle, onClick = onTrophiesClick)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(metrics.captionGap)) {
                LevelBar(header.levelFraction, description = toNextText ?: levelText)
                if (captionsInOneLine) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(levelText, color = colors.onSurfaceVariant, style = captionStyle, maxLines = 1)
                        if (toNextText != null) Text(toNextText, color = colors.onSurfaceVariant, style = captionStyle, maxLines = 1)
                    }
                } else {
                    Text(levelText, color = colors.onSurfaceVariant, style = captionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (toNextText != null) {
                        Text(toNextText, color = colors.onSurfaceVariant, style = captionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (!metrics.trophiesBeside) {
                TrophyRow(header, metrics, compact = false, withWords = true, captionStyle = captionStyle.copy(fontSize = 12.sp), onClick = onTrophiesClick)
            }
        }
    }
}

/** No percent and no points: the bar is a quiet line, and the caption under it speaks in time. */
@Composable
private fun LevelBar(fraction: Float, description: String) {
    val colors = ViolinTheme.progressColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarCorner))
            .background(colors.levelTrack)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(BarCorner))
                .background(Brush.horizontalGradient(listOf(colors.levelFillStart, colors.levelFillEnd))),
        )
    }
}

@Composable
private fun TrophyRow(
    header: ProfileHeader,
    metrics: ProfileHeaderMetrics,
    compact: Boolean,
    withWords: Boolean,
    captionStyle: TextStyle,
    onClick: () -> Unit,
) {
    val words = trophyWords(header)
    // Compact: the last one given, the next one, and how many more there are.
    val badges: List<TrophyBadge> = if (compact) {
        listOfNotNull(header.trophyRow.lastOrNull { it.given }, header.trophyRow.lastOrNull { !it.given })
    } else {
        header.trophyRow
    }
    val more = header.givenTrophies - badges.count { it.given }
    Row(
        modifier = Modifier
            .height(TouchHeight)
            .clip(RoundedCornerShape(TouchHeight / 2))
            .clickable(onClickLabel = stringResource(R.string.progress_open_trophies), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = words },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (withWords) 8.dp else metrics.trophyGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.trophyGap)) {
            badges.forEach { TrophyIcon(it.hours, locked = !it.given, size = metrics.trophyIcon) }
        }
        val caption = when {
            withWords -> words
            compact && more > 0 -> stringResource(R.string.progress_more_trophies, more)
            else -> null
        }
        if (caption != null) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = captionStyle, maxLines = 1)
    }
}

/** «2 трофея · следующий 50 ч», «следующий 1 ч», «10 трофеев». */
@Composable
private fun trophyWords(header: ProfileHeader): String {
    val count = header.givenTrophies.takeIf { it > 0 }?.let {
        stringResource(
            Formats.pluralRu(it, R.string.progress_trophies_one, R.string.progress_trophies_few, R.string.progress_trophies_many),
            it,
        )
    }
    val next = header.nextTrophyHours?.let { stringResource(R.string.progress_next_trophy, Formats.hoursMark(it)) }
    return when {
        count != null && next != null -> stringResource(R.string.progress_trophies_and_next, count, next)
        else -> count ?: next.orEmpty()
    }
}

@Composable
internal fun levelName(level: Int): String = stringArrayResource(R.array.progress_level_names).getOrElse(level - 1) { "" }

/** First character of the name as the avatar shows it; a surrogate pair (an emoji) stays whole. */
internal fun initialOf(name: String): String =
    if (name.isEmpty()) "" else name.substring(0, name.offsetByCodePoints(0, 1)).uppercase()
