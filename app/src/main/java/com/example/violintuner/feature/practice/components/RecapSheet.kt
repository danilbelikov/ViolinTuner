package com.example.violintuner.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.practice.PracticeRecap
import com.example.violintuner.core.domain.practice.RecapRoad
import com.example.violintuner.core.domain.progress.LevelProgress
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.journey.TaktIcon
import com.example.violintuner.feature.journey.cityOf
import com.example.violintuner.feature.journey.cityToOf
import com.example.violintuner.feature.journey.taktsInWords
import com.example.violintuner.feature.practice.PracticeIntent
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

private val SheetMaxWidth = 560.dp
private val CardCorner = 20.dp
private val CardPadding = 16.dp
private val Gap = 12.dp
private val SourceRowHeight = 34.dp
private val RoadBar = 8.dp
private val LevelBar = 6.dp
private val SmallCardMinHeight = 104.dp
private val TravelButtonHeight = 40.dp

// A window too low for one column (a phone on its side): the sheet goes in two, as the gift gives up its size.
private val LowWindowHeight = 520.dp
private const val DURATION_SIZE = 40
private const val DURATION_SIZE_LOW = 30
private const val TAKTS_SIZE = 28
private const val TAKTS_SIZE_LOW = 24
private const val STREAK_SIZE = 28
private const val TABULAR_FIGURES = "tnum"

/**
 * «Занятие сохранено» (spec 3.31, mockup `docs/design/project/recap`): what the practice just saved earned —
 * takts by source — and what it changed: the road, the streak, the level. Quiet, like the gift: numbers
 * roll and bars grow once, no particles, no sound. Closing it any way is «Готово».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(recap: PracticeRecap, onIntent: (PracticeIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(PracticeIntent.RecapClosed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        RecapSheetContent(
            recap = recap,
            onClose = { onIntent(PracticeIntent.RecapClosed) },
            onTravel = { onIntent(PracticeIntent.RecapTravelClicked) },
        )
    }
}

@Composable
internal fun RecapSheetContent(
    recap: PracticeRecap,
    onClose: () -> Unit,
    onTravel: () -> Unit,
    modifier: Modifier = Modifier,
    low: Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < LowWindowHeight,
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

    val info: @Composable ColumnScope.() -> Unit = {
        Header(recap, low)
        TaktsCard(recap, grow.value, low)
    }
    val rest: @Composable ColumnScope.() -> Unit = {
        RoadCard(recap.road, grow.value, onTravel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreakCard(recap, Modifier.weight(1f))
            LevelCard(recap, if (recap.levelUp) level.value else grow.value, Modifier.weight(1f))
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (low) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Gap), content = info)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                    rest()
                    PrimaryButton(text = stringResource(R.string.profile_done), onClick = onClose)
                }
            }
        } else {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Gap), content = info)
            rest()
            PrimaryButton(text = stringResource(R.string.profile_done), onClick = onClose)
        }
    }
}

@Composable
private fun Header(recap: PracticeRecap, low: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = stringResource(R.string.recap_title),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = Formats.minutesInWords(recap.durationMs),
            color = colors.onSurface,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = (if (low) DURATION_SIZE_LOW else DURATION_SIZE).sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        recap.dayTotalMs?.let {
            Text(
                text = stringResource(R.string.recap_day_total, Formats.minutesInWords(it)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

@Composable
private fun TaktsCard(recap: PracticeRecap, grow: Float, low: Boolean) {
    val colors = MaterialTheme.colorScheme
    val sources = recap.sources
    RecapCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TaktIcon(size = 24.dp, tint = colors.primary)
            Text(
                text = stringResource(R.string.journey_earned, taktsInWords((recap.takts * grow).roundToLong())),
                color = colors.primary,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (if (low) TAKTS_SIZE_LOW else TAKTS_SIZE).sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
        // Only what paid: a practice without Live has no line of notes (spec 3.31).
        if (sources.notesTakts > 0) SourceRow(stringResource(R.string.recap_source_notes), sources.notesInTune.toString(), sources.notesTakts)
        // the time as the header says it (rounded), not the whole minutes the takts are counted in: «2 мин» above and «1 мин» here would read as a mistake
        if (sources.timeTakts > 0) SourceRow(stringResource(R.string.recap_source_time), Formats.minutesInWords(recap.durationMs), sources.timeTakts)
        if (sources.piecesTakts > 0) SourceRow(stringResource(R.string.block_entry), sources.pieces.toString(), sources.piecesTakts)
    }
}

@Composable
private fun SourceRow(label: String, count: String, takts: Int) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontFeatureSettings = TABULAR_FIGURES)
    Row(Modifier.fillMaxWidth().heightIn(min = SourceRowHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = colors.onSurface, style = style.copy(fontWeight = FontWeight.SemiBold))
        Text(stringResource(R.string.recap_source_count, count), color = colors.onSurfaceVariant, style = style, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.journey_earned, Formats.takts(takts.toLong())), color = colors.onSurface, style = style.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun RoadCard(road: RecapRoad, grow: Float, onTravel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RecapCard(Modifier.semantics(mergeDescendants = true) {}) {
        when (road) {
            RecapRoad.NotStarted -> Quiet(stringResource(R.string.recap_road_not_started))
            is RecapRoad.RouteDone -> Quiet(stringResource(R.string.recap_road_done, Formats.takts(road.balance)))
            is RecapRoad.Leg -> {
                // the bar grows from the purse before this practice to the purse after it
                val shown = road.balanceBefore + (road.balanceAfter - road.balanceBefore) * grow
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (road.enough) {
                            stringResource(R.string.recap_road_enough, cityToOf(road.nextIndex))
                        } else {
                            stringResource(R.string.recap_road_left, cityToOf(road.nextIndex), Formats.takts(road.missing))
                        },
                        color = if (road.enough) colors.primary else colors.onSurface,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                        modifier = Modifier.weight(1f),
                    )
                    if (road.enough) {
                        TextButton(onClick = onTravel, modifier = Modifier.height(TravelButtonHeight)) {
                            Text(stringResource(R.string.home_travel), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Bar(
                    before = (road.balanceBefore.toFloat() / road.price).coerceIn(0f, 1f),
                    after = (shown / road.price).coerceIn(0f, 1f),
                    height = RoadBar,
                    track = colors.surfaceContainerHigh,
                )
                Row(Modifier.fillMaxWidth()) {
                    val caption = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES)
                    Text(stringResource(R.string.recap_road_leg, cityOf(road.nextIndex - 1), cityOf(road.nextIndex)), color = colors.onSurfaceVariant, style = caption, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.recap_road_count, Formats.takts(road.balanceAfter), Formats.takts(road.price.toLong())), color = colors.onSurfaceVariant, style = caption)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(recap: PracticeRecap, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    RecapCard(modifier.heightIn(min = SmallCardMinHeight).semantics(mergeDescendants = true) {}, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = recap.streakDays.toString(),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = STREAK_SIZE.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
            // the flame of «Дней подряд», by its own rules: nothing under three days (spec 3.18)
            StreakFlame(streakDays = recap.streakDays, running = false, scope = recap)
        }
        Text(stringResource(R.string.practice_streak), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
        if (recap.streakExtended) {
            Text(
                text = stringResource(R.string.recap_streak_up),
                color = ViolinTheme.practiceColors.flameOuter,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun LevelCard(recap: PracticeRecap, t: Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(R.array.progress_level_names)
    val after = recap.levelAfter
    RecapCard(modifier.heightIn(min = SmallCardMinHeight).semantics(mergeDescendants = true) {}, spacing = 6.dp) {
        if (recap.levelUp) {
            Text(stringResource(R.string.recap_level_up), color = colors.primary, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        Text(
            text = stringResource(R.string.progress_level, after.level, names.getOrElse(after.level - 1) { "" }),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp),
        )
        val (before, shown) = levelBar(recap.levelBefore, after, recap.levelUp, t)
        Bar(before = before, after = shown, height = LevelBar, track = colors.surfaceContainerHigh)
        val next = after.nextLevel
        val left = after.toNextMs
        if (next != null && left != null) {
            Text(
                text = stringResource(R.string.progress_to_next_level, next, Formats.remainingTime(left)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/**
 * The level bar at [t] of its growth: the old share stays dark, the new one grows light. A new level runs
 * the old bar to its end in the first half and the new one from zero in the second (spec 5.24).
 */
private fun levelBar(before: LevelProgress, after: LevelProgress, levelUp: Boolean, t: Float): Pair<Float, Float> =
    if (!levelUp) {
        before.fraction to before.fraction + (after.fraction - before.fraction) * t
    } else if (t < HALF) {
        before.fraction to before.fraction + (1f - before.fraction) * (t / HALF)
    } else {
        0f to after.fraction * ((t - HALF) / HALF)
    }

private const val HALF = 0.5f

@Composable
private fun Bar(before: Float, after: Float, height: Dp, track: Color) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(height / 2)
    Box(Modifier.fillMaxWidth().height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxWidth(after.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(colors.primary))
        Box(Modifier.fillMaxWidth(before.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(colors.primaryContainer))
    }
}

@Composable
private fun Quiet(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, fontFeatureSettings = TABULAR_FIGURES),
    )
}

@Composable
private fun RecapCard(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

