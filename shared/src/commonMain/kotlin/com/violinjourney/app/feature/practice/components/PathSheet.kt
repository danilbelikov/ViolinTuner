package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.LevelRing
import com.violinjourney.app.core.ui.components.LevelRingSize
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.ProfileHeader
import com.violinjourney.app.feature.practice.TrophyBadge
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_settings
import com.violinjourney.app.shared.resources.path_all_trophies
import com.violinjourney.app.shared.resources.path_name_photo
import com.violinjourney.app.shared.resources.path_title
import com.violinjourney.app.shared.resources.progress_next_trophy
import com.violinjourney.app.shared.resources.progress_no_name
import com.violinjourney.app.shared.resources.progress_open_trophies
import com.violinjourney.app.shared.resources.progress_total_description
import com.violinjourney.app.shared.resources.progress_trophies_and_next
import com.violinjourney.app.shared.resources.progress_trophies_few
import com.violinjourney.app.shared.resources.progress_trophies_many
import com.violinjourney.app.shared.resources.progress_trophies_one
import com.violinjourney.app.shared.resources.progress_trophy_names
import com.violinjourney.app.shared.resources.trophies_title
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private val HeadGap = 14.dp
private val BarTop = 14.dp
private val BarHeight = 10.dp
private val BarCorner = 5.dp
private val CaptionTop = 6.dp
private val CaptionGap = 12.dp
private val TrophiesTop = 20.dp
private val TilesTop = 10.dp
private val TilesGap = 10.dp
private val TileIcon = 36.dp
private val TileGap = 6.dp
private val TileDash = 1.5.dp
private val DashOn = 4.dp
private val DashOff = 3.dp
private val RowsTop = 10.dp
private val RowPhoto = 32.dp
private const val TILES = 3
private const val TABULAR_FIGURES = "tnum"
private const val LEVEL_SPLIT = " · "
private const val MIN_TOTAL_SIZE = 20
private const val TOTAL_SIZE = 30

/**
 * What «Мой путь» holds (spec 3.36.2, 5.29): the ring of 72 — the photo in it and the number as a badge — beside the name or «За
 * скрипкой» and the whole time large in the accent; the bar of the level with its shine (3.16) and the words under it — the level
 * on the left, the remainder on the right or under it; the latest trophies and the next one; and the rows «Все трофеи», «Имя и
 * фото», «Настройки». No title in words: the ring begins it, and TalkBack names the sheet «Мой путь». A face of the frame of «Занятия»
 * ([PracticeSheetHost]): a swipe only hides it (PathHidden).
 */
@Composable
fun PathSheetContent(header: ProfileHeader, photo: ImageBitmap?, onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.path_title)
    val words = levelWords(header)
    val progress = rememberLevelProgress(header.level, header.levelFraction, words, animate = true)
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Head(header, photo, progress)
        LevelBar(progress, header.levelFraction, Modifier.padding(top = BarTop))
        LevelCaption(progress.shownCaptions, header.firstRun, Modifier.padding(top = CaptionTop))
        SectionLabel(stringResource(Res.string.trophies_title), Modifier.padding(top = TrophiesTop))
        TrophyTiles(header, onClick = { onIntent(PracticeIntent.TrophiesClicked) }, modifier = Modifier.padding(top = TilesTop))
        Spacer(Modifier.height(RowsTop))
        ListRow(
            text = stringResource(Res.string.path_all_trophies),
            onClick = { onIntent(PracticeIntent.TrophiesClicked) },
            end = ListRowEnd.Arrow,
            accent = true,
        )
        ListRow(
            text = stringResource(Res.string.path_name_photo),
            onClick = { onIntent(PracticeIntent.ProfileClicked) },
            icon = AppIcons.Person,
            // the photo itself when there is one: it is seen where it is changed
            leading = photo?.let { bitmap ->
                {
                    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(RowPhoto).clip(CircleShape))
                }
            },
        )
        ListRow(text = stringResource(Res.string.nav_settings), onClick = { onIntent(PracticeIntent.PathSettingsClicked) }, icon = AppIcons.Gear)
    }
}

@Composable
private fun Head(header: ProfileHeader, photo: ImageBitmap?, progress: LevelProgress<LevelWords>) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HeadGap)) {
        LevelRing(
            level = progress.shownLevel,
            progress = { progress.filled.value },
            size = LevelRingSize.Sheet,
            photo = photo,
            ground = colors.surfaceContainer,
        )
        Column(Modifier.weight(1f)) {
            if (header.name.isNotEmpty()) {
                Text(
                    text = header.name,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
                )
            } else {
                Text(
                    text = stringResource(Res.string.progress_no_name),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            val big = MaterialTheme.typography.headlineMedium.copy(
                fontSize = TOTAL_SIZE.sp, lineHeight = 1.15.em, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR_FIGURES,
            )
            val fit = TextAutoSize.StepBased(minFontSize = MIN_TOTAL_SIZE.sp, maxFontSize = TOTAL_SIZE.sp, stepSize = 1.sp)
            if (header.firstRun) {
                // «Уровень 1 · Первый звук» where the sum will be, in the colour of text, as in the path row
                Text(progress.shownCaptions.level, color = colors.onSurface, maxLines = 1, softWrap = false, autoSize = fit, style = big)
            } else {
                val total = Formats.totalTime(header.totalMs)
                val description = stringResource(Res.string.progress_total_description, total)
                Text(
                    text = total,
                    modifier = Modifier.semantics { contentDescription = description },
                    color = colors.primary,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = fit,
                    style = big,
                )
            }
        }
    }
}

/**
 * The bar of the level (5.29): 10 high at a corner of 5, the old gradient of the level over the track, filled as far as [progress]
 * goes — read while drawing, so it grows without recomposing — and the shine that runs along it now and then (3.16, 5.10): only
 * here, and it gives way while the bar moves and while the sheet is out of sight.
 */
@Composable
private fun LevelBar(progress: LevelProgress<LevelWords>, fraction: Float, modifier: Modifier) {
    val colors = ViolinTheme.progressColors
    val shine = rememberLevelShine(progress.filled, moving = { progress.moving }, enabled = !LocalReduceMotion.current)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarCorner))
            .background(colors.levelTrack)
            .onLayoutRectChanged { shine.seen = it.fractionVisibleInWindow() > 0f }
            .drawWithCache {
                // The fill's brush is made for its width — anew while the bar grows; a pass of the shine only redraws.
                val width = size.width * progress.filled.value
                val fill = if (width > 0f) Brush.horizontalGradient(listOf(colors.levelFillStart, colors.levelFillEnd), endX = width) else null
                val corner = BarCorner.toPx()
                onDrawBehind {
                    if (fill != null) {
                        drawRoundRect(brush = fill, size = Size(width, size.height), cornerRadius = CornerRadius(corner))
                        shine.draw(this, fillWidth = width, corner = corner, color = colors.levelShine)
                    }
                }
            }
            .onSizeChanged { shine.barWidthPx = it.width }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) },
    )
}

/**
 * Under the bar (5.29): «Уровень 5» in the colour of text and bold, «· Гаммы» quieter, on the left; «до 6 уровня — 2 ч 43 мин» on
 * the right — or under the level when the two do not fit one line, never cut. The last level has nothing on the right; before the
 * first practice only the remainder stands here — the level is where the sum will be.
 */
@Composable
private fun LevelCaption(words: LevelWords, firstRun: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
    val toNext = words.toNext
    if (firstRun) {
        if (toNext != null) Text(toNext, modifier, color = colors.onSurfaceVariant, style = style)
        return
    }
    val level = twoToned(words.level, colors.onSurface)
    if (toNext == null) {
        Text(level, modifier, color = colors.onSurfaceVariant, style = style)
        return
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val needed = measurer.measure(level, style, maxLines = 1).size.width + measurer.measure(toNext, style, maxLines = 1).size.width +
            with(LocalDensity.current) { CaptionGap.roundToPx() }
        if (needed <= constraints.maxWidth) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(level, color = colors.onSurfaceVariant, maxLines = 1, style = style)
                Text(toNext, color = colors.onSurfaceVariant, maxLines = 1, style = style, textAlign = TextAlign.End)
            }
        } else {
            Column {
                Text(level, color = colors.onSurfaceVariant, style = style)
                Text(toNext, color = colors.onSurfaceVariant, style = style)
            }
        }
    }
}

/**
 * «Уровень 5 · Гаммы» with its first part — up to the first « · » of the template of every language — in [strong] and bold; the
 * rest takes the colour of the text around it.
 */
private fun twoToned(level: String, strong: Color): AnnotatedString = buildAnnotatedString {
    val cut = level.indexOf(LEVEL_SPLIT)
    if (cut < 0) {
        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(level) }
    } else {
        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(level.substring(0, cut)) }
        append(level.substring(cut))
    }
}

/**
 * The two latest trophies and the next one (spec 3.36.2): tiles a third of the width each from the left, on the ground of the
 * screen; the next one — its drawing as an outline in a dashed frame, its words quieter. The whole row opens «Все трофеи», and
 * TalkBack reads it as «2 трофея · следующий 50 ч».
 */
@Composable
private fun TrophyTiles(header: ProfileHeader, onClick: () -> Unit, modifier: Modifier) {
    val words = trophyWords(header)
    val names = stringArrayResource(Res.array.progress_trophy_names)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(AppShapes.M)
            .clickable(onClickLabel = stringResource(Res.string.progress_open_trophies), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = words },
        horizontalArrangement = Arrangement.spacedBy(TilesGap),
    ) {
        header.trophyRow.take(TILES).forEach { badge ->
            TrophyTile(badge, names.getOrElse(badge.index) { "" }, Modifier.weight(1f).fillMaxHeight())
        }
        repeat(TILES - header.trophyRow.size.coerceAtMost(TILES)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TrophyTile(badge: TrophyBadge, name: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val ground = colors.surface
    val dash = colors.outlineVariant
    val look = if (badge.given) {
        Modifier.background(ground, AppShapes.M)
    } else {
        Modifier.drawBehind {
            val stroke = TileDash.toPx()
            drawRoundRect(
                color = dash,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(TileCorner.toPx() - stroke / 2),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
            )
        }
    }
    val words = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    Column(
        modifier = modifier.then(look).padding(horizontal = TilePaddingSide, vertical = TilePaddingVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        TrophyIcon(badge.hours, locked = !badge.given, size = TileIcon)
        Text(
            text = name,
            color = if (badge.given) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = words.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = Formats.hoursMark(badge.hours),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            style = words.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

private val TileCorner = 18.dp
private val TilePaddingSide = 6.dp
private val TilePaddingVertical = 12.dp

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
