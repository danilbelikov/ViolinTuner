package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.ProfileHeader
import com.violinjourney.app.feature.practice.TrophyBadge
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.progress_level
import com.violinjourney.app.shared.resources.progress_level_names
import com.violinjourney.app.shared.resources.progress_more_trophies
import com.violinjourney.app.shared.resources.progress_next_trophy
import com.violinjourney.app.shared.resources.progress_no_name
import com.violinjourney.app.shared.resources.progress_open_profile
import com.violinjourney.app.shared.resources.progress_open_trophies
import com.violinjourney.app.shared.resources.progress_to_next_level
import com.violinjourney.app.shared.resources.progress_total_description
import com.violinjourney.app.shared.resources.progress_trophies_and_next
import com.violinjourney.app.shared.resources.progress_trophies_few
import com.violinjourney.app.shared.resources.progress_trophies_many
import com.violinjourney.app.shared.resources.progress_trophies_one
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

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
    /** False while the header only holds its place (loading): nothing it shows then is a change. */
    animate: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    // Motion is for what changes before the user's eyes. What is already true when the header
    // appears — the first data after loading included — is shown as it is: someone with four
    // hundred hours must not watch the bar climb from level one on every visit.
    var armed by remember { mutableStateOf(false) }
    val motion = armed
    LaunchedEffect(animate) { armed = animate }

    val name = header.name.ifEmpty { stringResource(Res.string.progress_no_name) }
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
    val levelText = stringResource(Res.string.progress_level, header.level, levelName(header.level))
    val toNextText = header.nextLevel?.let { next ->
        stringResource(Res.string.progress_to_next_level, next, Formats.remainingTime(header.toNextLevelMs ?: 0L))
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
                        .clickable(onClickLabel = stringResource(Res.string.progress_open_profile), role = Role.Button, onClick = onProfileClick),
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
                        val totalDescription = stringResource(Res.string.progress_total_description, total)
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
                    TrophyRow(header, metrics, compact = compactRow, withWords = false, captionStyle = captionStyle, motion = motion, onClick = onTrophiesClick)
                }
            }
            LevelBlock(
                level = header.level,
                fraction = header.levelFraction,
                captions = LevelCaptions(levelText, toNextText),
                oneLine = captionsInOneLine,
                captionStyle = captionStyle,
                gap = metrics.captionGap,
                motion = motion,
            )
            if (!metrics.trophiesBeside) {
                TrophyRow(
                    header, metrics, compact = false, withWords = true, captionStyle = captionStyle.copy(fontSize = 12.sp),
                    motion = motion, onClick = onTrophiesClick,
                )
            }
        }
    }
}

private data class LevelCaptions(val level: String, val toNext: String?)

/**
 * The bar and the two captions under it. No percent and no points: the bar is a quiet line and
 * the captions speak in time. Bar and captions move together: when a level is passed the bar
 * runs to its end, rests, and only then the new level is named and the bar starts it from nothing.
 */
@Composable
private fun LevelBlock(
    level: Int,
    fraction: Float,
    captions: LevelCaptions,
    oneLine: Boolean,
    captionStyle: TextStyle,
    gap: Dp,
    motion: Boolean,
) {
    val colors = ViolinTheme.progressColors
    val target = fraction.coerceIn(0f, 1f)
    val filled = remember { Animatable(target) }
    var shownLevel by remember { mutableIntStateOf(level) }
    var shownCaptions by remember { mutableStateOf(captions) }

    LaunchedEffect(level, target, captions) {
        when {
            !motion -> filled.snapTo(target)
            level > shownLevel -> {
                filled.animateTo(1f, tween(ProgressMotion.BAR_LEVEL_UP_FILL_MS, easing = FastOutSlowInEasing))
                delay(ProgressMotion.BAR_LEVEL_UP_PAUSE_MS)
                filled.snapTo(0f)
                shownLevel = level
                shownCaptions = captions
                filled.animateTo(target, tween(ProgressMotion.BAR_LEVEL_UP_GROW_MS, easing = FastOutSlowInEasing))
            }
            else -> {
                shownCaptions = captions
                filled.animateTo(target, tween(ProgressMotion.BAR_GROW_MS, easing = FastOutSlowInEasing))
            }
        }
        shownLevel = level
        shownCaptions = captions
    }

    val shine = rememberLevelShine(filled, enabled = !LocalReduceMotion.current)

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        val description = captions.toNext ?: captions.level
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(RoundedCornerShape(BarCorner))
                .background(colors.levelTrack)
                .drawBehind {
                    // Read in the draw phase: a growing bar redraws, it does not recompose.
                    val width = size.width * filled.value
                    if (width > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(colors.levelFillStart, colors.levelFillEnd), endX = width),
                            size = Size(width, size.height),
                            cornerRadius = CornerRadius(BarCorner.toPx()),
                        )
                        shine.draw(this, fillWidth = width, corner = BarCorner.toPx(), color = colors.levelShine)
                    }
                }
                .onSizeChanged { shine.barWidthPx = it.width }
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
        )
        Crossfade(
            targetState = shownCaptions,
            // Without motion the captions change in place, like the bar: no fade from a level
            // that was never the user's.
            animationSpec = if (motion) tween(ProgressMotion.CAPTION_CROSSFADE_MS) else snap(),
            label = "level captions",
        ) { shown ->
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            if (oneLine) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(shown.level, color = color, style = captionStyle, maxLines = 1)
                    if (shown.toNext != null) Text(shown.toNext, color = color, style = captionStyle, maxLines = 1)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Text(shown.level, color = color, style = captionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shown.toNext != null) Text(shown.toNext, color = color, style = captionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TrophyRow(
    header: ProfileHeader,
    metrics: ProfileHeaderMetrics,
    compact: Boolean,
    withWords: Boolean,
    captionStyle: TextStyle,
    motion: Boolean,
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
            .clickable(onClickLabel = stringResource(Res.string.progress_open_trophies), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = words },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (withWords) 8.dp else metrics.trophyGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.trophyGap)) {
            badges.forEach { badge -> key(badge.hours) { TrophyBadgeIcon(badge, metrics.trophyIcon, motion) } }
        }
        val caption = when {
            withWords -> words
            compact && more > 0 -> stringResource(Res.string.progress_more_trophies, more)
            else -> null
        }
        if (caption != null) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = captionStyle, maxLines = 1)
    }
}

/**
 * One place of the row. When its gift sheet is answered the outline fills in and the trophy
 * pops into place; the outline of the mark after it fades in a moment later (handoff `anims`).
 */
@Composable
private fun TrophyBadgeIcon(badge: TrophyBadge, size: Dp, motion: Boolean) {
    val fill = remember { Animatable(if (badge.given) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    // A badge that is there when the header appears just is; one that joins later fades in.
    val entrance = remember { Animatable(if (motion) 0f else 1f) }

    LaunchedEffect(badge.given) {
        when {
            !badge.given -> fill.snapTo(0f)
            fill.value == 1f -> Unit
            !motion -> fill.snapTo(1f)
            else -> {
                launch {
                    pop.snapTo(ProgressMotion.TROPHY_POP_FROM)
                    pop.animateTo(1f, tween(ProgressMotion.TROPHY_POP_MS, easing = ProgressMotion.TrophyPop))
                }
                fill.animateTo(1f, tween(ProgressMotion.TROPHY_FILL_MS))
            }
        }
    }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(ProgressMotion.NEXT_TROPHY_FADE_MS, delayMillis = ProgressMotion.NEXT_TROPHY_DELAY_MS))
    }

    Box(modifier = Modifier.graphicsLayer { alpha = entrance.value }) {
        if (fill.value < 1f) TrophyIcon(badge.hours, locked = true, size = size, modifier = Modifier.graphicsLayer { alpha = 1f - fill.value })
        if (fill.value > 0f) {
            TrophyIcon(
                hours = badge.hours,
                locked = false,
                size = size,
                modifier = Modifier.graphicsLayer {
                    alpha = fill.value
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}

/** «2 трофея · следующий 50 ч», «следующий 1 ч», «10 трофеев». */
@Composable
private fun trophyWords(header: ProfileHeader): String {
    val count = header.givenTrophies.takeIf { it > 0 }?.let {
        stringResource(
            Formats.plural(it, Res.string.progress_trophies_one, Res.string.progress_trophies_few, Res.string.progress_trophies_many),
            it,
        )
    }
    val next = header.nextTrophyHours?.let { stringResource(Res.string.progress_next_trophy, Formats.hoursMark(it)) }
    return when {
        count != null && next != null -> stringResource(Res.string.progress_trophies_and_next, count, next)
        else -> count ?: next.orEmpty()
    }
}

@Composable
internal fun levelName(level: Int): String = stringArrayResource(Res.array.progress_level_names).getOrElse(level - 1) { "" }

/** First character of the name as the avatar shows it; a surrogate pair (an emoji) stays whole. */
internal fun initialOf(name: String): String =
    if (name.isEmpty()) "" else name.substring(0, if (name[0].isHighSurrogate() && name.length > 1) 2 else 1).uppercase()
