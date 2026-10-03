package com.violinjourney.app.feature.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.ui.components.AppDialog
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.DialogTone
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.ExactLines
import com.violinjourney.app.core.ui.components.FaceArrival
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.SettleAfterDoubleTap
import com.violinjourney.app.core.ui.components.WholeWords
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_chip_no_audio
import com.violinjourney.app.shared.resources.backup_chip_no_video
import com.violinjourney.app.shared.resources.backup_count_days_few
import com.violinjourney.app.shared.resources.backup_count_days_many
import com.violinjourney.app.shared.resources.backup_count_days_one
import com.violinjourney.app.shared.resources.backup_count_kinds_few
import com.violinjourney.app.shared.resources.backup_count_kinds_many
import com.violinjourney.app.shared.resources.backup_count_kinds_one
import com.violinjourney.app.shared.resources.backup_count_level
import com.violinjourney.app.shared.resources.backup_count_pages_few
import com.violinjourney.app.shared.resources.backup_count_pages_many
import com.violinjourney.app.shared.resources.backup_count_pages_one
import com.violinjourney.app.shared.resources.backup_count_pieces_few
import com.violinjourney.app.shared.resources.backup_count_pieces_many
import com.violinjourney.app.shared.resources.backup_count_pieces_one
import com.violinjourney.app.shared.resources.backup_count_sessions_few
import com.violinjourney.app.shared.resources.backup_count_sessions_many
import com.violinjourney.app.shared.resources.backup_count_sessions_one
import com.violinjourney.app.shared.resources.backup_count_video_few
import com.violinjourney.app.shared.resources.backup_count_video_many
import com.violinjourney.app.shared.resources.backup_count_video_one
import com.violinjourney.app.shared.resources.backup_done_of
import com.violinjourney.app.shared.resources.backup_part_audio_short
import com.violinjourney.app.shared.resources.backup_part_data_short
import com.violinjourney.app.shared.resources.backup_part_sheets
import com.violinjourney.app.shared.resources.backup_part_video
import com.violinjourney.app.shared.resources.backup_percent
import com.violinjourney.app.shared.resources.backup_phase_now
import com.violinjourney.app.shared.resources.backup_remaining
import com.violinjourney.app.shared.resources.backup_remaining_minutes
import com.violinjourney.app.shared.resources.backup_remaining_seconds
import com.violinjourney.app.shared.resources.backup_total_value
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.event_count_few
import com.violinjourney.app.shared.resources.event_count_many
import com.violinjourney.app.shared.resources.event_count_one
import com.violinjourney.app.shared.resources.restore_without_sheets
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TABULAR_FIGURES = "tnum"
private const val SECONDS_PER_MINUTE = 60

/**
 * The look of the screens of a copy and of a restore (spec 3.36.8, 5.29 R8): one column, 560 at most, in the middle of the window, its
 * fields 16 — the sides of the bottom zone too; under what scrolls, 16 above the zone.
 */
internal object BackupDimens {
    val ColumnMax = 560.dp
    val Side = 16.dp
    val EndGap = 16.dp

    /** The tile of an outcome: 88 at a corner of 28 with an icon of 44; in a window no higher than 360 dp 56, its corner 18, icon 28. */
    val Tile = 88.dp
    val TileCompact = 56.dp
    val TileCorner = 28.dp
    val TileCornerCompact = 18.dp
    val TileIcon = 44.dp
    val TileIconCompact = 28.dp
    val TileTop = 24.dp
    val TileBottom = 16.dp

    /** Between the title of an outcome and its text. */
    val TitleToText = 10.dp

    /** A card of «инфо», of a file, of a warning: fields 14, an icon of 20 and 12 to the words; 12 above it. */
    val CardPadding = 14.dp
    val CardIcon = 20.dp
    val CardGap = 12.dp
    val CardTop = 12.dp

    /** The progress: 12 under the header to the line of phases, 10 to the percent, the bar 10 high 14 under it and 8 above its line. */
    val PhasesTop = 12.dp
    val PhasesToPercent = 10.dp
    val BarHeight = 10.dp
    val BarCorner = 5.dp
    val BarTop = 14.dp
    val BarBottom = 8.dp

    /** The first card under the progress. */
    val FirstCardTop = 22.dp

    /** The chips of a card: 30 high, a capsule, fields 10, 6 apart, 10 under what they follow. */
    val ChipHeight = 30.dp
    val ChipSide = 10.dp
    val ChipVertical = 4.dp
    val ChipGap = 6.dp
    val ChipsTop = 10.dp
}

/** The percent of the progress: 56 sp, 40 in a window no higher than 360 dp (spec 5.29 R8). */
private const val PERCENT_SP = 56f
private const val PERCENT_COMPACT_SP = 40f

/** The title of an outcome: 28 sp / 800; a word that does not stand whole takes it a step smaller, down to the 20 of a header. */
private const val RESULT_TITLE_SP = 28f
private const val RESULT_TITLE_LEAST_SP = 20f

/** The dangerous coral of the ground of a failed tile (spec 5.29 R8: «DangerSoft на 16 %»). */
private const val PROBLEM_TILE_ALPHA = 0.16f

private val Capsule = RoundedCornerShape(percent = 50)

@Composable
internal fun partColor(part: BackupPart): Color = with(ViolinTheme.backupColors) {
    when (part) {
        BackupPart.DATA -> data
        BackupPart.SHEETS -> sheets
        BackupPart.AUDIO -> audio
        BackupPart.VIDEO -> video
    }
}

@Composable
internal fun plural(count: Int, one: StringResource, few: StringResource, many: StringResource): String = stringResource(Formats.plural(count, one, few, many), count)

@Composable
internal fun sessionsWord(count: Int) = plural(count, Res.string.backup_count_sessions_one, Res.string.backup_count_sessions_few, Res.string.backup_count_sessions_many)

@Composable
internal fun piecesWord(count: Int) = plural(count, Res.string.backup_count_pieces_one, Res.string.backup_count_pieces_few, Res.string.backup_count_pieces_many)

@Composable
internal fun daysWord(count: Int) = plural(count, Res.string.backup_count_days_one, Res.string.backup_count_days_few, Res.string.backup_count_days_many)

@Composable
internal fun pagesWord(count: Int) = plural(count, Res.string.backup_count_pages_one, Res.string.backup_count_pages_few, Res.string.backup_count_pages_many)

/** The words of a chip: «41 день занятий», «уровень 9», «64 записи», «6 видео», «12 произведений», «48 страниц», «12 событий», «2 своих вида». */
@Composable
internal fun chipWords(chip: BackupFacts.Chip): String = when (chip.fact) {
    BackupFacts.Fact.DAYS -> daysWord(chip.count)
    BackupFacts.Fact.LEVEL -> stringResource(Res.string.backup_count_level, chip.count)
    BackupFacts.Fact.SESSIONS -> sessionsWord(chip.count)
    BackupFacts.Fact.VIDEOS -> plural(chip.count, Res.string.backup_count_video_one, Res.string.backup_count_video_few, Res.string.backup_count_video_many)
    BackupFacts.Fact.PIECES -> piecesWord(chip.count)
    BackupFacts.Fact.PAGES -> pagesWord(chip.count)
    BackupFacts.Fact.EVENTS -> plural(chip.count, Res.string.event_count_one, Res.string.event_count_few, Res.string.event_count_many)
    BackupFacts.Fact.KINDS -> plural(chip.count, Res.string.backup_count_kinds_one, Res.string.backup_count_kinds_few, Res.string.backup_count_kinds_many)
}

/** The words of a dashed chip: «без видео», «без звука», «без фото нот». */
@Composable
internal fun missingWords(missing: BackupFacts.Missing): String = stringResource(
    when (missing) {
        BackupFacts.Missing.VIDEO -> Res.string.backup_chip_no_video
        BackupFacts.Missing.AUDIO -> Res.string.backup_chip_no_audio
        BackupFacts.Missing.SHEETS -> Res.string.restore_without_sheets
    },
)

/**
 * «5 записей · 5 произведений · 38 дней занятий» — what a restore takes away, in «Заменить данные?» and «Удалить всё и восстановить?»:
 * only what there is ([BackupFacts.lost]).
 */
@Composable
internal fun countsLine(counts: BackupCounts): String = BackupFacts.lost(counts).map { chipWords(it) }.joinToString(stringResource(Res.string.dot_separator))

/** A weight as the screens say it: «≈ 252 МБ» from a megabyte up, «меньше 1 МБ» below ([BackupFacts.approximate]). */
@Composable
internal fun weightWords(bytes: Long): String {
    val size = Formats.fileSize(bytes)
    return if (BackupFacts.approximate(bytes)) stringResource(Res.string.backup_total_value, size) else size
}

/** «около 3 мин» past a minute, «несколько секунд» below: an estimate is not a stopwatch. */
@Composable
internal fun remainingWords(seconds: Int): String = stringResource(
    Res.string.backup_remaining,
    if (seconds < SECONDS_PER_MINUTE) stringResource(Res.string.backup_remaining_seconds) else stringResource(Res.string.backup_remaining_minutes, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE),
)

@Composable
internal fun partShortName(part: BackupPart): String = stringResource(
    when (part) {
        BackupPart.DATA -> Res.string.backup_part_data_short
        BackupPart.SHEETS -> Res.string.backup_part_sheets
        BackupPart.AUDIO -> Res.string.backup_part_audio_short
        BackupPart.VIDEO -> Res.string.backup_part_video
    },
)

/** The words of a step of a line of phases: a phase in its words, a part not being moved by its short name. */
@Composable
internal fun stepWords(name: BackupFacts.StepName): String = when (name) {
    is BackupFacts.StepName.Phase -> phaseWords(name.phase)
    is BackupFacts.StepName.Part -> partShortName(name.part)
}

/**
 * What a face of a screen showed while it was its own: [own] — the screen still stands on this face. A face that leaves — the job has
 * moved on to the next one — keeps its [value] while it fades (the lesson of stage 119: what goes away keeps the face it had); a face
 * that stays follows it.
 */
@Composable
internal fun <T> heldWhile(own: Boolean, value: T): T {
    // not a snapshot state: the face recomposes with the screen anyway, and a write here must not ask for another composition
    val held = remember { Held(value) }
    if (own) held.value = value
    return held.value
}

/** What a face holds: written in composition, read right after — never observed. */
private class Held<T>(var value: T)

/**
 * The presses of the bottom zone of a face (spec 5.29 R8, review of stage 122): for the time of a double tap of the system from its first
 * frame ([SettleAfterDoubleTap]) a face does not take them, as a face of a sheet (R3) and a moment of the journey (R7) do not. A face that
 * came in the place of another — the choice after «Ещё раз», «Остановить» after «Восстановить» into an empty app — has its button where
 * the one pressed stood, and the second tap of that finger would ask for a second «Сохранить как…» or stop what has just begun. The face a
 * screen opens on holds too (the lead's fix of stage 122, verified): the screen comes in under the finger that opened it — «Сохранить в…»
 * of the copy stands where «Сначала сохранить текущие данные» of the passport stood, and the second tap of that finger would put up a
 * «Сохранить как…» over a screen not seen yet.
 */
@Composable
internal fun <T> zonePresses(onIntent: (T) -> Unit): (T) -> Unit {
    val arrival = remember { FaceArrival(inPlace = true) }
    SettleAfterDoubleTap(arrival)
    val latest = rememberUpdatedState(onIntent)
    return remember(arrival) { { intent -> if (!arrival.holds) latest.value(intent) } }
}

/**
 * [text] whose numbers keep to the word after them ([BackupFacts.keptNumbers]; spec 5.29 R6: «число и единица не разрываются») — where
 * that keeps every word whole in the width it is given, at the least size of its [autoSize] (at the size of its [style] without one);
 * where it does not, the text as it is: a number may end a line, a word never breaks (fr «50 enregistrements» beside a switch at 320 ×
 * 544 at 1.5 — 175 dp in 166; 5.29 R8, review of stage 122). The part captions of a copy and the passport of a restore.
 */
@Composable
internal fun KeptNumbersText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, autoSize: WholeWordsFit? = null) {
    val kept = remember(text) { BackupFacts.keptNumbers(text) }
    if (kept == text) {
        Text(text = text, modifier = modifier, color = color, autoSize = autoSize, style = style)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val shown = remember(text, kept, style, autoSize, room, density, direction) {
            val least = style.copy(fontSize = (autoSize?.minSp ?: style.fontSize.value).sp)
            if (room == Constraints.Infinity || WholeWords.at(measurer, kept, least, room, density, direction)) kept else text
        }
        Text(text = shown, color = color, autoSize = autoSize, style = style)
    }
}

/**
 * A face of the copy or the restore (spec 3.36.8, 5.29 R8; `landscape.html`, rule 3): what scrolls, in a column of 560 at most in the
 * middle of the window — scrolled from the margins too — and the bottom zone of R1 under it ([AppDock], its sides 16 as the fields of the
 * screen, its rows as wide as the column: [zoneRow]). [dock] null — no zone: what scrolls goes to the bottom. The zone stays put and what
 * scrolls ends 16 above it.
 */
@Composable
internal fun BackupFace(dock: (@Composable DockScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    AppDock(
        dock = { dock?.invoke(this) },
        metrics = currentDockMetrics().copy(side = BackupDimens.Side),
        pinned = dock != null,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = BackupDimens.ColumnMax).fillMaxWidth().padding(horizontal = BackupDimens.Side)) {
                content()
                Spacer(Modifier.height(LocalDockInset.current + BackupDimens.EndGap))
            }
        }
    }
}

/** A row of the bottom zone of a copy or a restore: as wide as the words of the column above it, in the middle of the window. */
internal fun DockScope.zoneRow(): Modifier =
    Modifier.align(Alignment.CenterHorizontally).widthIn(max = BackupDimens.ColumnMax - BackupDimens.Side * 2).fillMaxWidth()

/**
 * The line of phases of a copy or a restore (spec 3.36.8, 5.29 R8): one line of 13 sp / 700, the steps through « · » — behind the job in
 * the second level of text, the one of now in the accent, those ahead in the third; it wraps, 4 between its lines. For a reader one
 * phrase, «сейчас: Видео 7 из 12».
 */
@Composable
internal fun PhaseRow(steps: List<BackupFacts.Step>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tertiary = ViolinTheme.textTertiary
    val dot = stringResource(Res.string.dot_separator)
    val words = steps.map { stepWords(it.name) }
    val now = steps.indexOfFirst { it.state == BackupFacts.StepState.CURRENT }.takeIf { it >= 0 }?.let { stringResource(Res.string.backup_phase_now, words[it]) }
    val line = buildAnnotatedString {
        steps.forEachIndexed { i, step ->
            if (i > 0) withStyle(SpanStyle(color = tertiary)) { append(dot) }
            val color = when (step.state) {
                BackupFacts.StepState.DONE -> colors.onSurfaceVariant
                BackupFacts.StepState.CURRENT -> colors.primary
                BackupFacts.StepState.NEXT -> tertiary
            }
            withStyle(SpanStyle(color = color)) { append(words[i]) }
        }
    }
    Text(
        text = line,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { now?.let { contentDescription = it } },
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            // 17 a line and 4 between wrapped ones: the half leading of the first and the last line is trimmed
            lineHeight = 21.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        ),
    )
}

/**
 * How far a long job is (spec 3.36.8, 5.29 R8): the percent large — 56 sp, 40 in a low window ([compact]) — a bar of 10 and «1,9 из 3,4
 * ГБ · осталось около 3 мин». A reader hears the percent from the bar, which is the progress: the number itself is only drawn. Without a
 * progress yet the bar goes on its own and the line holds its height.
 */
@Composable
internal fun JobProgress(progress: BackupProgress?, remainingSec: Int?, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val percentSp = if (compact) PERCENT_COMPACT_SP else PERCENT_SP
    Text(
        text = stringResource(Res.string.backup_percent, BackupPhases.percentOf(progress)),
        modifier = Modifier.clearAndSetSemantics {},
        color = colors.onSurface,
        maxLines = 1,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = percentSp.sp,
            lineHeight = (percentSp * PERCENT_LINE).sp,
            // the line asked, on Android too: material3's own style pads it back to Manrope's 1.37 em there (review of stage 122)
            lineHeightStyle = ExactLines,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.03).em,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
    val bar = Modifier.padding(top = BackupDimens.BarTop, bottom = BackupDimens.BarBottom).fillMaxWidth().height(BackupDimens.BarHeight).clip(RoundedCornerShape(BackupDimens.BarCorner))
    if (progress == null) {
        LinearProgressIndicator(modifier = bar, color = colors.primary, trackColor = colors.surfaceContainerHigh, gapSize = 0.dp)
    } else {
        LinearProgressIndicator(progress = { progress.fraction }, modifier = bar, color = colors.primary, trackColor = colors.surfaceContainerHigh, gapSize = 0.dp, drawStopIndicator = {})
    }
    Text(
        // the line keeps its height while the speed is still being measured
        text = BackupFacts.keptNumbers(
            listOfNotNull(
                progress?.let { stringResource(Res.string.backup_done_of, Formats.fileSize(it.doneBytes), Formats.fileSize(it.totalBytes)) },
                remainingSec?.let { remainingWords(it) },
            ).joinToString(stringResource(Res.string.dot_separator)),
        ),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
    )
}

/**
 * The line height of the percent: as the mockup's `line-height: 1`, a little more for the figures' own height — on both platforms
 * ([ExactLines]: Android pads a line lower than the font's own 1.37 em back to it, and the figures stood 7 dp lower than on iOS).
 */
private const val PERCENT_LINE = 1.1f

/**
 * A card of «инфо» (spec 5.29 R8, as «Инфо» of R5): surfaceContainer at a corner of 18, fields 14, an icon of 20 in the second level of
 * text and its words, 14 sp at a line of 1.45, beside it — what the person should know: what is left out of a copy, where a copy goes on,
 * what to do with a copy saved.
 */
@Composable
internal fun InfoCard(text: String, modifier: Modifier = Modifier, icon: ImageVector = AppIcons.Info) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().background(colors.surfaceContainer, AppShapes.M).padding(BackupDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(BackupDimens.CardGap),
    ) {
        AppIcon(icon, contentDescription = null, size = BackupDimens.CardIcon, tint = colors.onSurfaceVariant)
        Text(BackupFacts.keptNumbers(text), modifier = Modifier.weight(1f), color = colors.onSurfaceVariant, style = cardWords())
    }
}

/** The words of a card: 14 sp at a line of 1.45. */
@Composable
internal fun cardWords() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.3.sp)

/** What a tile of an outcome says by its colour and sign: a done job, a failure, or nothing to save. */
internal enum class TileKind { Done, Problem, Empty }

/**
 * The tile of an outcome (spec 3.36.8, 5.29 R8): 88 at a corner of 28 — 56 in a window no higher than 360 dp ([compact]) — 24 above, 16
 * below. Done — a tick in the accent on the soft accent (the outcome of an action of the person, not the brass of «сделано»); a failure —
 * the sign of a failure in the coral on the coral at 16 %; nothing to save — an archive in the accent on the soft accent. Drawn only: its
 * title says it.
 */
@Composable
internal fun ResultTile(kind: TileKind, compact: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ground: Color
    val icon: ImageVector
    val tint: Color
    when (kind) {
        TileKind.Done -> {
            ground = ViolinTheme.accentSoft
            icon = AppIcons.Check
            tint = colors.primary
        }
        TileKind.Problem -> {
            ground = ViolinTheme.dangerSoft.copy(alpha = PROBLEM_TILE_ALPHA)
            icon = AppIcons.Alert
            tint = ViolinTheme.dangerSoft
        }
        TileKind.Empty -> {
            ground = ViolinTheme.accentSoft
            icon = AppIcons.Archive
            tint = colors.primary
        }
    }
    val size: Dp = if (compact) BackupDimens.TileCompact else BackupDimens.Tile
    Box(
        modifier = modifier
            .padding(top = BackupDimens.TileTop, bottom = BackupDimens.TileBottom)
            .size(size)
            .background(ground, RoundedCornerShape(if (compact) BackupDimens.TileCornerCompact else BackupDimens.TileCorner)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, size = if (compact) BackupDimens.TileIconCompact else BackupDimens.TileIcon, tint = tint)
    }
}

/**
 * The title of an outcome — «Копия сохранена», «Не хватило места там, куда сохраняли» — 28 sp / 800, letters −0.02 em, a heading for a
 * reader; its words wrap at their spaces, and a word that does not stand whole in a line (a narrow phone at a large font: de
 * «Wiederherstellung») takes all of them a step smaller, down to 20 sp.
 */
@Composable
internal fun ResultTitle(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text = BackupFacts.keptNumbers(text),
        modifier = modifier.fillMaxWidth().semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        autoSize = WholeWordsFit(RESULT_TITLE_SP, RESULT_TITLE_LEAST_SP),
        // the mockup's 1.15 on Android too: material3 pads its first and last line back to Manrope's 1.37 em there (review of stage 122)
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = RESULT_TITLE_SP.sp,
            lineHeight = 1.15.em,
            lineHeightStyle = ExactLines,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).em,
        ),
    )
}

/** The text under the title of an outcome: 16 sp at a line of 1.5, the second level of text; 10 under the title. */
@Composable
internal fun ResultText(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text = BackupFacts.keptNumbers(text),
        modifier = modifier.fillMaxWidth().padding(top = BackupDimens.TitleToText),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    )
}

/**
 * What a card holds, as chips (spec 3.36.8, 5.29 R8): «41 день занятий», «уровень 9», «64 записи» — 30 high, capsules on surface-2, 13 sp
 * / 700; what a copy left out — «без видео» — dashed, without a ground, in the second level of text. 6 apart, wrapping, 10 under what
 * they follow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FactChips(chips: BackupFacts.Chips, modifier: Modifier = Modifier) {
    if (chips.facts.isEmpty() && chips.missing.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = BackupDimens.ChipsTop),
        horizontalArrangement = Arrangement.spacedBy(BackupDimens.ChipGap),
        verticalArrangement = Arrangement.spacedBy(BackupDimens.ChipGap),
    ) {
        chips.facts.forEach { FactChip(chipWords(it), dashed = false) }
        chips.missing.forEach { FactChip(missingWords(it), dashed = true) }
    }
}

@Composable
private fun FactChip(text: String, dashed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = BackupDimens.ChipHeight)
            .then(if (dashed) Modifier.dashedFrame(colors.outlineVariant, Capsule) else Modifier.background(colors.surfaceContainerHigh, Capsule))
            .padding(horizontal = BackupDimens.ChipSide, vertical = BackupDimens.ChipVertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = BackupFacts.keptNumbers(text),
            color = if (dashed) colors.onSurfaceVariant else colors.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * A plain question with the safe answer on the left and the dangerous one on the right, a coral word (spec 3.20, 3.36.1): replacing
 * the data, stopping a copy. Dismissing it is the safe answer.
 */
@Composable
internal fun ConfirmDialog(title: String, text: String, safe: String, destructive: String, onSafe: () -> Unit, onDestructive: () -> Unit) {
    AppDialog(
        title = title,
        text = text,
        dismiss = safe,
        onDismiss = onSafe,
        confirm = destructive,
        onConfirm = onDestructive,
        confirmTone = DialogTone.Danger,
    )
}
