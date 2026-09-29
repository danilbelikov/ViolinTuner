package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.LevelProgress
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.PinnedBottom
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.TaktIcon
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.taktsInWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.journey_earned
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_streak
import com.violinjourney.app.shared.resources.practice_streak_days_description_few
import com.violinjourney.app.shared.resources.practice_streak_days_description_many
import com.violinjourney.app.shared.resources.practice_streak_days_description_one
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.progress_level
import com.violinjourney.app.shared.resources.progress_level_names
import com.violinjourney.app.shared.resources.progress_to_next_level
import com.violinjourney.app.shared.resources.recap_day_total
import com.violinjourney.app.shared.resources.recap_description_head
import com.violinjourney.app.shared.resources.recap_description_level
import com.violinjourney.app.shared.resources.recap_description_level_last
import com.violinjourney.app.shared.resources.recap_description_level_up
import com.violinjourney.app.shared.resources.recap_description_level_up_last
import com.violinjourney.app.shared.resources.recap_description_road
import com.violinjourney.app.shared.resources.recap_description_sentence
import com.violinjourney.app.shared.resources.recap_description_source
import com.violinjourney.app.shared.resources.recap_description_takts
import com.violinjourney.app.shared.resources.recap_level_name
import com.violinjourney.app.shared.resources.recap_level_up
import com.violinjourney.app.shared.resources.recap_road_done
import com.violinjourney.app.shared.resources.recap_road_enough
import com.violinjourney.app.shared.resources.recap_road_more
import com.violinjourney.app.shared.resources.recap_road_not_started
import com.violinjourney.app.shared.resources.recap_road_to
import com.violinjourney.app.shared.resources.recap_source_count
import com.violinjourney.app.shared.resources.recap_source_notes
import com.violinjourney.app.shared.resources.recap_source_repertoire
import com.violinjourney.app.shared.resources.recap_source_time
import com.violinjourney.app.shared.resources.recap_streak_up
import com.violinjourney.app.shared.resources.recap_title
import com.violinjourney.app.shared.resources.takt_few
import com.violinjourney.app.shared.resources.takt_many
import com.violinjourney.app.shared.resources.takt_one
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// «Занятие сохранено» (spec 3.36.3, 5.29 R3; practice-sheets.html 2).
private val HeaderTop = 4.dp
private val HeaderGap = 10.dp
private const val DURATION_SIZE = 22
private const val DAY_TOTAL_SIZE = 13
private val TaktsTop = 12.dp
private val TaktsIcon = 30.dp
private val TaktsIconGap = 10.dp
private const val TAKTS_SIZE = 40
private const val TAKTS_TRACKING = -0.02
private const val TAKTS_LINE_HEIGHT = 1.1

/** Every form of «такт» a language has: the place of the rolling number is kept by the widest of them. */
private val TaktForms = listOf(Res.string.takt_one, Res.string.takt_few, Res.string.takt_many)

/** Between the groups and the cards of the sheet: the sources, the road, the streak and the level. */
private val BlockGap = 10.dp

// A card or a group of rows on the ground of the screen (5.29 R3): 18, fields 12 / 14.
private val CardPaddingVertical = 12.dp
private val CardPaddingHorizontal = 14.dp
private val SourceRowMinHeight = 40.dp
private val SourceWordsGap = 4.dp
private const val SOURCE_SIZE = 14
private val DividerThickness = 1.dp

// The bars of the road and of the level: 6 (the road was 8).
private val BarHeight = 6.dp
private val BarTop = 8.dp
private val BarBottom = 6.dp
private const val ROAD_SIZE = 14
private const val ENOUGH_SIZE = 16

/** «Хватает до Праги»: the card in the accent at 12 % over the ground (5.29 R3). */
private const val ENOUGH_TINT = 0.12f
private val EnoughGap = 8.dp

// The streak and the level as a pair: 1 : 1.45, 10 apart.
private const val STREAK_WEIGHT = 1f
private const val LEVEL_WEIGHT = 1.45f
private val PairGap = 10.dp
private const val LABEL_SIZE = 12
private const val LABEL_TRACKING = 0.05
private const val STREAK_SIZE = 26
private val StreakTop = 4.dp
private val FlameGap = 6.dp
private const val STREAK_UP_SIZE = 13
private const val CAPTION_SIZE = 13

// A new level: the one highlight of the sheet — a frame of 1.5 in the accent, its name 22 / 800; the streak shrinks to a row of 48.
private val LevelFrame = 1.5.dp
private val LevelNameTop = 2.dp
private const val LEVEL_NAME_SIZE = 22
private val StreakRowMinHeight = 48.dp
private const val STREAK_ROW_SIZE = 14
private const val STREAK_ROW_NUMBER_SIZE = 16

/** Between the two columns of a low window. */
private val ColumnsGap = 12.dp
private const val TABULAR_FIGURES = "tnum"
private const val HALF = 0.5f

/**
 * A window too low for one column of the recap — a phone on its side (spec 3.31, 3.36.3): below it the recap goes in two columns and
 * the gift gives up its size and its card «Дальше», so that «Готово» and «Спасибо» are in sight without scrolling.
 */
internal val LowSheetWindowHeight = 520.dp

/** The window is lower than [LowSheetWindowHeight]. */
@Composable
internal fun lowSheetWindow(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < LowSheetWindowHeight

/**
 * «Занятие сохранено» (spec 3.31, 3.36.3; practice-sheets.html 2): the label and, small on one line, the length and «всего за день»;
 * the one big number — «+225 тактов», rolled from zero; the sources as rows; the road in two tones; the streak and the level as a
 * pair — a new level in a frame with the streak as a row under it, no streak at 0. Quiet: numbers roll and bars grow once (5.24),
 * nothing else moves. Its button «Готово» is [RecapButtons], at the bottom of the sheet — in a [low] window at the bottom of the
 * right column ([onDone]), where the columns scroll over it. TalkBack reads it as one paragraph on its title; «В дорогу» and
 * «Готово» stay buttons.
 */
@Composable
fun RecapSheetContent(
    recap: PracticeRecap,
    onTravel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    low: Boolean = lowSheetWindow(),
    /** False shows everything grown: previews, where the first frame is all there is. */
    animated: Boolean = true,
) {
    val still = LocalReduceMotion.current || !animated
    // One clock for everything that grows (spec 5.24): the takts roll and the bars fill together, once.
    val grow = remember(recap) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(recap) {
        if (still) return@LaunchedEffect
        delay(PracticeMotion.RECAP_DELAY_MS)
        grow.animateTo(1f, tween(PracticeMotion.RECAP_GROW_MS, easing = FastOutSlowInEasing))
    }
    // A new level runs the bar to its end and starts it again: two halves of a clock of its own.
    val level = remember(recap) { Animatable(if (still || !recap.levelUp) 1f else 0f) }
    LaunchedEffect(recap) {
        if (still || !recap.levelUp) return@LaunchedEffect
        delay(PracticeMotion.RECAP_DELAY_MS)
        level.animateTo(1f, tween(2 * PracticeMotion.RECAP_LEVEL_HALF_MS, easing = LinearEasing))
    }
    val title = stringResource(Res.string.recap_title)
    val look = RecapLook.of(recap)
    val earned: @Composable ColumnScope.() -> Unit = {
        Header(recap, title, recapDescription(recap, title))
        Takts(recap, { grow.value }, Modifier.padding(top = TaktsTop))
        Sources(recap, Modifier.padding(top = BlockGap))
    }
    val changed: @Composable ColumnScope.(first: Modifier) -> Unit = { first ->
        RoadCard(recap.road, { grow.value }, onTravel, first)
        StreakAndLevel(recap, look, { grow.value }, { level.value }, Modifier.padding(top = BlockGap))
    }
    if (low) {
        LowColumns(
            left = earned,
            right = { changed(Modifier) },
            buttons = { RecapButtons(onDone) },
            modifier = modifier.semantics { paneTitle = title },
        )
    } else {
        Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
            earned()
            changed(Modifier.padding(top = BlockGap))
        }
    }
}

/** «Готово» — the one button of the recap (spec 3.36.3); a swipe and «назад» are the same. */
@Composable
fun RecapButtons(onDone: () -> Unit, modifier: Modifier = Modifier) {
    AppSheetButtons(main = stringResource(Res.string.profile_done), onMain = onDone, modifier = modifier)
}

/**
 * The two columns of a low window (spec 3.36.3): on the left what was earned, on the right what it changed with «Готово» at the bottom
 * — each column scrolls by itself, and the right one is as tall as the left one at least, so «Готово» stands at the bottom of the sheet
 * and is always in sight, the fade of the sheet over it while the right column goes on below.
 */
@Composable
private fun LowColumns(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leftScroll = rememberScrollState()
    val rightScroll = rememberScrollState()
    val fade = MaterialTheme.colorScheme.surfaceContainer
    Layout(
        contents = listOf(
            { Column(Modifier.fillMaxWidth().verticalScroll(leftScroll), content = left) },
            {
                PinnedBottom(
                    fadeColor = fade,
                    fadeWhen = { rightScroll.canScrollForward },
                    content = { Column(Modifier.fillMaxWidth().verticalScroll(rightScroll), content = right) },
                    bottom = buttons,
                )
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (lefts, rights), constraints ->
        val gap = ColumnsGap.roundToPx()
        val width = constraints.maxWidth
        val columnWidth = ((width - gap) / 2).coerceAtLeast(0)
        val column = Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = constraints.maxHeight)
        val leftPlaced = lefts.map { it.measure(column) }
        val leftHeight = leftPlaced.maxOfOrNull { it.height } ?: 0
        // at least as tall as the left column: «Готово» stands at the bottom of the pair, not under the right column's last card
        val rightPlaced = rights.map { it.measure(column.copy(minHeight = leftHeight.coerceAtMost(constraints.maxHeight))) }
        val height = maxOf(leftHeight, rightPlaced.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            leftPlaced.forEach { it.placeRelative(0, 0) }
            rightPlaced.forEach { it.placeRelative(columnWidth + gap, 0) }
        }
    }
}

/** The label and, on one baseline, the length 22 / 800 and «всего за день — …» 13 sp — one paragraph for TalkBack, a heading. */
@Composable
private fun Header(recap: PracticeRecap, title: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            heading()
            contentDescription = description
        },
    ) {
        SectionLabel(title)
        Row(Modifier.padding(top = HeaderTop), horizontalArrangement = Arrangement.spacedBy(HeaderGap)) {
            Text(
                text = Formats.minutesInWords(recap.durationMs),
                modifier = Modifier.alignByBaseline(),
                color = colors.onSurface,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = DURATION_SIZE.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            recap.dayTotalMs?.let {
                Text(
                    text = stringResource(Res.string.recap_day_total, Formats.minutesInWords(it)),
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = DAY_TOTAL_SIZE.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

/**
 * «+225 тактов» — the one big number of the sheet, in the colour of text with the sign of a takt in the accent; it rolls from zero
 * ([grow], read here only). Its place is kept, unseen, by the number it rolls to in every form of the word — the roll passes through
 * the others: «+120 тактов» on the way to «+121 такт» is wider, and one that wrapped on the way would change the height of the sheet
 * while it grows. The number never has more figures than the last one, and the figures are tabular.
 */
@Composable
private fun Takts(recap: PracticeRecap, grow: () -> Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.displaySmall.copy(
        fontSize = TAKTS_SIZE.sp,
        lineHeight = TAKTS_LINE_HEIGHT.em,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = TAKTS_TRACKING.em,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TaktsIconGap),
    ) {
        TaktIcon(size = TaktsIcon, tint = colors.primary)
        Box(Modifier.weight(1f)) {
            val last = Formats.takts(recap.takts.toLong())
            TaktForms.forEach { form -> Text(stringResource(Res.string.journey_earned, stringResource(form, last)), Modifier.alpha(0f), style = style) }
            Text(stringResource(Res.string.journey_earned, taktsInWords((recap.takts * grow()).roundToLong())), color = colors.onSurface, style = style)
        }
    }
}

/**
 * The sources as rows (spec 3.31, 3.36.3): «Ноты в строе · 212» — «+71», «Время · 47 мин» — «+94», «Репертуар · 2» — «+60»; only
 * those that paid, their sum the number above. The words in the second level of text, the takts in the colour of text.
 */
@Composable
private fun Sources(recap: PracticeRecap, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val sources = recap.sources
    val rows = buildList {
        // Only what paid: a practice without Live has no line of notes (spec 3.31).
        if (sources.notesTakts > 0) add(SourceLine(stringResource(Res.string.recap_source_notes), sources.notesInTune.toString(), sources.notesTakts))
        // the time as the header says it (rounded), not the whole minutes the takts are counted in: «2 мин» above and «1 мин» here would read as a mistake
        if (sources.timeTakts > 0) add(SourceLine(stringResource(Res.string.recap_source_time), Formats.minutesInWords(recap.durationMs), sources.timeTakts))
        // its own word, not the bookmark of Live, which R6 renames (spec 3.36.3)
        if (sources.piecesTakts > 0) add(SourceLine(stringResource(Res.string.recap_source_repertoire), sources.pieces.toString(), sources.piecesTakts))
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, AppShapes.M)
            .padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical)
            .clearAndSetSemantics { },
    ) {
        rows.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider(thickness = DividerThickness, color = colors.outlineVariant)
            SourceRow(line)
        }
    }
}

private data class SourceLine(val label: String, val count: String, val takts: Int)

@Composable
private fun SourceRow(line: SourceLine) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = SOURCE_SIZE.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_FIGURES)
    Row(
        Modifier.fillMaxWidth().heightIn(min = SourceRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SourceWordsGap),
    ) {
        Text(line.label, color = colors.onSurfaceVariant, style = style)
        Text(
            text = stringResource(Res.string.recap_source_count, line.count),
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style,
        )
        Text(stringResource(Res.string.journey_earned, Formats.takts(line.takts.toLong())), color = colors.onSurface, style = style.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * The road (spec 3.36.3): «До Праги» and «ещё 1 128» over a bar in two tones — the dark what there was, the light what the practice
 * added, growing with [grow]; no leg, no count. Enough — the card in the accent at 12 %, «Хватает до Праги» and the text button
 * «В дорогу →» (home, 3.25), the bar grown to its end. The route done, or the journey not begun — one line, no bar, no button.
 */
@Composable
private fun RoadCard(road: RecapRoad, grow: () -> Float, onTravel: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val enough = road is RecapRoad.Leg && road.enough
    val ground = if (enough) colors.primary.copy(alpha = ENOUGH_TINT).compositeOver(colors.surface) else colors.surface
    val words = MaterialTheme.typography.bodyMedium.copy(fontSize = ROAD_SIZE.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_FIGURES)
    Column(
        modifier
            .fillMaxWidth()
            .background(ground, AppShapes.M)
            .padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
    ) {
        when (road) {
            RecapRoad.NotStarted -> Text(
                stringResource(Res.string.recap_road_not_started),
                Modifier.clearAndSetSemantics { },
                color = colors.onSurfaceVariant,
                style = words.copy(fontWeight = FontWeight.SemiBold),
            )
            is RecapRoad.RouteDone -> Text(
                stringResource(Res.string.recap_road_done, Formats.takts(road.balance)),
                Modifier.clearAndSetSemantics { },
                color = colors.onSurfaceVariant,
                style = words.copy(fontWeight = FontWeight.SemiBold),
            )
            is RecapRoad.Leg -> {
                if (road.enough) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EnoughGap)) {
                        Text(
                            text = stringResource(Res.string.recap_road_enough, cityToOf(road.nextIndex)),
                            modifier = Modifier.weight(1f).clearAndSetSemantics { },
                            color = colors.primary,
                            style = words.copy(fontSize = ENOUGH_SIZE.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
                        )
                        // the second action of the sheet, a word: it does not argue with «Готово»
                        AppButton(
                            text = stringResource(Res.string.home_travel),
                            onClick = onTravel,
                            style = AppButtonStyle.Text,
                            trailingIcon = AppIcons.ArrowRight,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(EnoughGap)) {
                        Text(stringResource(Res.string.recap_road_to, cityToOf(road.nextIndex)), Modifier.weight(1f), color = colors.onSurfaceVariant, style = words)
                        Text(stringResource(Res.string.recap_road_more, Formats.takts(road.missing)), color = colors.onSurface, style = words.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
                // the bar grows from the purse before this practice to the purse after it
                Bar(
                    before = (road.balanceBefore.toFloat() / road.price).coerceIn(0f, 1f),
                    after = { ((road.balanceBefore + (road.balanceAfter - road.balanceBefore) * grow()) / road.price).coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = BarTop),
                )
            }
        }
    }
}

/** The streak and the level by [look] (spec 3.36.3): a pair, a new level framed with the streak as a row, or the level alone. */
@Composable
private fun StreakAndLevel(recap: PracticeRecap, look: RecapLook, grow: () -> Float, level: () -> Float, modifier: Modifier) {
    when {
        look.levelUp -> Column(modifier.fillMaxWidth()) {
            LevelUpCard(recap, level)
            if (look.streak == RecapStreak.Row) StreakRow(recap, Modifier.padding(top = BlockGap))
        }
        look.streak == RecapStreak.None -> LevelCard(recap, grow, modifier.fillMaxWidth())
        else -> Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(PairGap)) {
            StreakCard(recap, Modifier.weight(STREAK_WEIGHT).fillMaxHeight())
            LevelCard(recap, grow, Modifier.weight(LEVEL_WEIGHT).fillMaxHeight())
        }
    }
}

/** «ДНЕЙ ПОДРЯД», the number with its flame (3.18: none under three days) and «+1 день» — only when the practice grew the streak. */
@Composable
private fun StreakCard(recap: PracticeRecap, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.card(colors.surface).clearAndSetSemantics { }) {
        CardLabel(stringResource(Res.string.practice_streak), colors.onSurfaceVariant)
        Row(Modifier.padding(top = StreakTop), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlameGap)) {
            Text(
                text = recap.streakDays.toString(),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = STREAK_SIZE.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
            StreakFlame(streakDays = recap.streakDays, running = false, scope = recap)
        }
        if (recap.streakExtended) StreakUp()
    }
}

/** A new level: the streak shrinks to a row of 48 under its card — «Дней подряд», the number with its flame, «+1 день». */
@Composable
private fun StreakRow(recap: PracticeRecap, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = StreakRowMinHeight)
            .background(colors.surface, AppShapes.M)
            .padding(horizontal = CardPaddingHorizontal)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlameGap),
    ) {
        Text(
            stringResource(Res.string.practice_streak),
            Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = STREAK_ROW_SIZE.sp, lineHeight = 20.sp),
        )
        Text(
            recap.streakDays.toString(),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = STREAK_ROW_NUMBER_SIZE.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
        StreakFlame(streakDays = recap.streakDays, running = false, scope = recap)
        if (recap.streakExtended) StreakUp()
    }
}

@Composable
private fun StreakUp() {
    Text(
        text = stringResource(Res.string.recap_streak_up),
        color = ViolinTheme.practiceColors.flameOuter,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = STREAK_UP_SIZE.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
    )
}

/** «УРОВЕНЬ 5 · ГАММЫ», the bar growing from before to after, «до 6 уровня — 1 ч 56 мин»; the last level has no remainder. */
@Composable
private fun LevelCard(recap: PracticeRecap, grow: () -> Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(Res.array.progress_level_names)
    val after = recap.levelAfter
    Column(modifier.card(colors.surface).clearAndSetSemantics { }) {
        CardLabel(stringResource(Res.string.progress_level, after.level, names.getOrElse(after.level - 1) { "" }), colors.onSurfaceVariant)
        Bar(
            before = recap.levelBefore.fraction,
            after = { levelBar(recap.levelBefore, after, levelUp = false, grow()).second },
            modifier = Modifier.padding(top = BarTop, bottom = BarBottom),
        )
        Remainder(after)
    }
}

/**
 * A new level (spec 3.36.3): the whole width in a frame of 1.5 in the accent, inside its edge — «НОВЫЙ УРОВЕНЬ», «6 · Этюды», the bar
 * that runs to its end and starts again ([level]), the remainder to the next.
 */
@Composable
private fun LevelUpCard(recap: PracticeRecap, level: () -> Float) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(Res.array.progress_level_names)
    val after = recap.levelAfter
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surface)
            .border(LevelFrame, colors.primary, AppShapes.M)
            .padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical)
            .clearAndSetSemantics { },
    ) {
        CardLabel(stringResource(Res.string.recap_level_up), colors.primary)
        Text(
            text = stringResource(Res.string.recap_level_name, after.level, names.getOrElse(after.level - 1) { "" }),
            modifier = Modifier.padding(top = LevelNameTop),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = LEVEL_NAME_SIZE.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
        )
        Bar(
            before = { levelBar(recap.levelBefore, after, levelUp = true, level()).first },
            after = { levelBar(recap.levelBefore, after, levelUp = true, level()).second },
            modifier = Modifier.padding(top = BarTop, bottom = BarBottom),
        )
        Remainder(after)
    }
}

@Composable
private fun Remainder(after: LevelProgress) {
    val next = after.nextLevel
    val left = after.toNextMs
    if (next == null || left == null) return
    Text(
        text = stringResource(Res.string.progress_to_next_level, next, Formats.remainingTime(left)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = CAPTION_SIZE.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
    )
}

/** The label of a card (5.29 R3): 12 sp, 700, capitals, 0.05 em apart. */
@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = LABEL_SIZE.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = LABEL_TRACKING.em),
    )
}

/** A card of the sheet (5.29 R3): on the ground of the screen, 18, fields 12 / 14. */
private fun Modifier.card(ground: Color): Modifier =
    background(ground, AppShapes.M).padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical)

/**
 * The level bar at [t] of its growth: the old share stays dark, the new one grows light. A new level runs the old bar to its end in
 * the first half and the new one from zero in the second (spec 5.24).
 */
private fun levelBar(before: LevelProgress, after: LevelProgress, levelUp: Boolean, t: Float): Pair<Float, Float> =
    if (!levelUp) {
        before.fraction to before.fraction + (after.fraction - before.fraction) * t
    } else if (t < HALF) {
        before.fraction to before.fraction + (1f - before.fraction) * (t / HALF)
    } else {
        0f to after.fraction * ((t - HALF) / HALF)
    }

/** A bar of 6 in two tones on the track of surface-2: primaryContainer to [before], the accent on to [after] (read while drawing). */
@Composable
private fun Bar(before: Float, after: () -> Float, modifier: Modifier = Modifier) = Bar({ before }, after, modifier)

@Composable
private fun Bar(before: () -> Float, after: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BarHeight / 2)
    Box(Modifier.then(modifier).fillMaxWidth().height(BarHeight).clip(shape).background(colors.surfaceContainerHigh)) {
        BarPart(after, colors.primary, shape)
        BarPart(before, colors.primaryContainer, shape)
    }
}

@Composable
private fun BarPart(share: () -> Float, color: Color, shape: RoundedCornerShape) {
    Layout(Modifier.clip(shape).background(color)) { _, constraints ->
        val width = (constraints.maxWidth * share().coerceIn(0f, 1f)).toInt()
        layout(width, constraints.maxHeight) {}
    }
}

/** The text of the paragraph TalkBack reads for the whole sheet (spec 3.31, 3.36.3). */
@Composable
private fun recapDescription(recap: PracticeRecap, title: String): String {
    val parts = mutableListOf<String>()
    parts += stringResource(Res.string.recap_description_head, title, Formats.minutesInWords(recap.durationMs))
    recap.dayTotalMs?.let {
        parts += stringResource(Res.string.recap_description_sentence, stringResource(Res.string.recap_day_total, Formats.minutesInWords(it)))
    }
    val sources = recap.sources
    val lines = mutableListOf<String>()
    if (sources.notesTakts > 0) {
        lines += stringResource(Res.string.recap_description_source, stringResource(Res.string.recap_source_notes), Formats.takts(sources.notesTakts.toLong()))
    }
    if (sources.timeTakts > 0) {
        lines += stringResource(Res.string.recap_description_source, stringResource(Res.string.recap_source_time), Formats.takts(sources.timeTakts.toLong()))
    }
    if (sources.piecesTakts > 0) {
        lines += stringResource(Res.string.recap_description_source, stringResource(Res.string.recap_source_repertoire), Formats.takts(sources.piecesTakts.toLong()))
    }
    // «ноты в строе — 71, время — 94»: the pairs joined as each language joins two things
    var listed = lines.firstOrNull().orEmpty()
    for (line in lines.drop(1)) listed = stringResource(Res.string.practice_pair_description, listed, line)
    parts += stringResource(Res.string.recap_description_takts, taktsInWords(recap.takts.toLong()), listed)
    parts += when (val road = recap.road) {
        RecapRoad.NotStarted -> stringResource(Res.string.recap_description_sentence, stringResource(Res.string.recap_road_not_started))
        is RecapRoad.RouteDone -> stringResource(Res.string.recap_description_sentence, stringResource(Res.string.recap_road_done, Formats.takts(road.balance)))
        is RecapRoad.Leg -> if (road.enough) {
            stringResource(Res.string.recap_description_sentence, stringResource(Res.string.recap_road_enough, cityToOf(road.nextIndex)))
        } else {
            stringResource(Res.string.recap_description_road, cityToOf(road.nextIndex), Formats.takts(road.missing))
        }
    }
    // a streak of 0 is not said, as it is not shown
    if (recap.streakDays > 0) {
        val days = stringResource(
            Formats.plural(
                recap.streakDays,
                Res.string.practice_streak_days_description_one,
                Res.string.practice_streak_days_description_few,
                Res.string.practice_streak_days_description_many,
            ),
            recap.streakDays,
        )
        parts += stringResource(Res.string.recap_description_sentence, days)
        if (recap.streakExtended) parts += stringResource(Res.string.recap_description_sentence, stringResource(Res.string.recap_streak_up))
    }
    val after = recap.levelAfter
    val name = stringArrayResource(Res.array.progress_level_names).getOrElse(after.level - 1) { "" }
    val next = after.nextLevel
    val left = after.toNextMs
    parts += when {
        next != null && left != null && recap.levelUp ->
            stringResource(Res.string.recap_description_level_up, after.level, name, next, Formats.remainingTime(left))
        next != null && left != null -> stringResource(Res.string.recap_description_level, after.level, name, next, Formats.remainingTime(left))
        recap.levelUp -> stringResource(Res.string.recap_description_level_up_last, after.level, name)
        else -> stringResource(Res.string.recap_description_level_last, after.level, name)
    }
    return parts.joinToString(" ")
}
