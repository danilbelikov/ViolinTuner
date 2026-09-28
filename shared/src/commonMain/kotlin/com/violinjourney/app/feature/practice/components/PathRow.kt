package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.LevelRing
import com.violinjourney.app.core.ui.components.LevelRingSize
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.ProfileHeader
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.path_caption
import com.violinjourney.app.shared.resources.path_description
import com.violinjourney.app.shared.resources.path_description_first
import com.violinjourney.app.shared.resources.path_description_last
import com.violinjourney.app.shared.resources.progress_level
import com.violinjourney.app.shared.resources.progress_to_next_level
import org.jetbrains.compose.resources.stringResource

private val RowMinHeight = 56.dp
private val RowPaddingVertical = 4.dp
private val RingGap = 12.dp
private const val TABULAR_FIGURES = "tnum"
private const val TOTAL_LETTER_SPACING_EM = -0.01

/** The words of the level: «Уровень 5 · Гаммы» and «до 6 уровня — 2 ч 43 мин» — null on the last level. */
@Immutable
internal data class LevelWords(val level: String, val toNext: String?)

/** The words of the level of [header], in the language of the screen. */
@Composable
internal fun levelWords(header: ProfileHeader): LevelWords = LevelWords(
    level = stringResource(Res.string.progress_level, header.level, levelName(header.level)),
    toNext = header.nextLevel?.let { next -> stringResource(Res.string.progress_to_next_level, next, Formats.remainingTime(header.toNextLevelMs ?: 0L)) },
)

/** No practice was ever saved: «Уровень 1 · Первый звук» stands where the sum would (spec 3.36.2). */
internal val ProfileHeader.firstRun: Boolean get() = totalMs <= 0L

/**
 * The path row (spec 3.36.2, 5.29): where the player is on the path — the ring of the level (its arc — the way to the next level,
 * the number or the photo inside it, [LevelRing]), the whole time at the violin in the accent, and under it «Уровень 5 · Гаммы» and
 * «до 6 уровня — 2 ч 43 мин» in one line if they fit, else in two — the level, then what is left, never cut. A chevron at the end:
 * the whole row opens «Мой путь». On the last level the ring is full and the words have no remainder; before the first practice
 * «Уровень 1 · Первый звук» stands in the place of the sum, in the colour of text, and under it only the remainder.
 *
 * The ring moves as the bar of the header did (3.13, [rememberLevelProgress]): at once when the progress changes — under the
 * recap too — without motion on loading and on a return to the tab ([animate] false while the row only holds its place).
 * TalkBack reads one description — «Уровень 5, Гаммы, 47 ч 17 мин, до 6 уровня 2 ч 43 мин», the progress in words — and «Кнопка»:
 * the row opens «Мой путь». It carries no [androidx.compose.ui.semantics.ProgressBarRangeInfo]: Android would announce a node
 * with one as a progress bar, not as a button (the bar of «Мой путь», not pressed, has it). The photo says nothing.
 */
@Composable
fun PathRow(
    header: ProfileHeader,
    photo: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val words = levelWords(header)
    val progress = rememberLevelProgress(header.level, header.levelFraction, words, animate)
    val shown = progress.shownCaptions
    val firstRun = header.firstRun
    val total = Formats.totalTime(header.totalMs)
    val description = pathDescription(header, total)
    val totalStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = TOTAL_LETTER_SPACING_EM.em, fontFeatureSettings = TABULAR_FIGURES,
    )
    val captionStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.5.sp, fontFeatureSettings = TABULAR_FIGURES)
    val caption = shown.toNext?.let { stringResource(Res.string.path_caption, shown.level, it) } ?: shown.level
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // the column of words is what is left beside the ring and the chevron
        val wordsWidth = with(LocalDensity.current) { (maxWidth - LevelRingSize.Row.ring - RingGap * 2 - IconSizes.Standalone).roundToPx() }
        val oneLine = firstRun || shown.toNext == null || measurer.measure(caption, captionStyle, maxLines = 1).size.width <= wordsWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.M)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                // no range info here: on Android a node with one is announced as a progress bar whatever its role, and this row
                // is a button — the progress is in its words, «до 6 уровня 2 ч 43 мин»
                .clearAndSetSemantics { contentDescription = description }
                .heightIn(min = RowMinHeight)
                .padding(vertical = RowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RingGap),
        ) {
            LevelRing(level = progress.shownLevel, progress = { progress.filled.value }, size = LevelRingSize.Row, photo = photo, ground = colors.surface)
            Column(Modifier.weight(1f)) {
                if (firstRun) {
                    Text(shown.level, color = colors.onSurface, style = totalStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    shown.toNext?.let { Text(it, color = colors.onSurfaceVariant, style = captionStyle) }
                } else {
                    Text(total, color = colors.primary, style = totalStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PathCaption(shown, caption, oneLine, captionStyle)
                }
            }
            AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary)
        }
    }
}

/** One line when it fits; else the level, then the remainder under it — nothing is cut. */
@Composable
private fun PathCaption(words: LevelWords, caption: String, oneLine: Boolean, style: TextStyle) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (oneLine) {
        Text(caption, color = color, style = style)
    } else {
        Text(words.level, color = color, style = style)
        words.toNext?.let { Text(it, color = color, style = style) }
    }
}

/** «Уровень 5, Гаммы, 47 ч 17 мин, до 6 уровня 2 ч 43 мин» — without the sum before the first practice, without the rest on the last level. */
@Composable
private fun pathDescription(header: ProfileHeader, total: String): String {
    val name = levelName(header.level)
    val next = header.nextLevel
    val left = Formats.remainingTime(header.toNextLevelMs ?: 0L)
    return when {
        next == null -> stringResource(Res.string.path_description_last, header.level, name, total)
        header.firstRun -> stringResource(Res.string.path_description_first, header.level, name, next, left)
        else -> stringResource(Res.string.path_description, header.level, name, total, next, left)
    }
}
