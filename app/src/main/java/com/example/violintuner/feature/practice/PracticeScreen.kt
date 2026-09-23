package com.example.violintuner.feature.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.icons.IconSizes
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.SessionCard
import com.example.violintuner.feature.practice.components.CalendarMetrics
import com.example.violintuner.feature.practice.components.EditTimeSheet
import com.example.violintuner.feature.practice.components.GiftSheet
import com.example.violintuner.feature.practice.components.RecapSheet
import com.example.violintuner.feature.practice.components.PracticeCalendar
import com.example.violintuner.feature.practice.components.PracticeMotion
import com.example.violintuner.feature.practice.components.ProfileHeader
import com.example.violintuner.feature.practice.components.ProfileHeaderMetrics
import com.example.violintuner.feature.practice.components.ProfileSheet
import com.example.violintuner.feature.practice.components.RunningDot
import com.example.violintuner.feature.practice.components.StartPracticeButton
import com.example.violintuner.feature.practice.components.StreakFlame
import com.example.violintuner.feature.practice.components.StreakFlameSize
import com.example.violintuner.feature.practice.components.SummarySheet
import com.example.violintuner.feature.practice.components.TrophiesSheet
import com.example.violintuner.feature.practice.components.rolledValue
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val BlockGap = 20.dp
private val MaxContentWidth = 560.dp
private val LandscapeLeftColumn = 280.dp
private val ButtonHeight = 56.dp
private val ButtonCorner = 28.dp
private val TodayLineGap = 10.dp
private val CardCorner = 16.dp
private val CardCornerLandscape = 14.dp
private val ActionHeight = 40.dp
private val ActionCorner = 20.dp
private val TodayChipCorner = 6.dp
private val SessionCardGap = 8.dp
private const val TABULAR_FIGURES = "tnum"
private const val ACTION_SWITCH_MS = 200
private const val ACTION_SWITCH_SCALE = 0.96f
private const val DAY_CROSSFADE_MS = 150
private const val MIN_CARD_LABEL_SIZE = 9
private const val MS_PER_MINUTE = 60_000L

/** A streak that grew by a day is simply shown; only a jump of days rolls. */
private const val STREAK_ROLL_FROM = 2L
private const val MIN_CARD_VALUE_SIZE = 11

/** Text sizes that differ between the layouts (handoff `sizes`). */
@Immutable
private data class Metrics(
    val header: ProfileHeaderMetrics,
    val timerSize: Int,
    val cardCorner: Dp,
    val cardPadding: Dp,
    val cardLabelSize: Int,
    val cardValueSize: Int,
    val dayDateSize: Int,
    val dayTimeSize: Int,
    /** «Не занимались» is a phrase, not a figure: smaller than the time. */
    val dayNoneSize: Int,
    val calendar: CalendarMetrics,
    /** The streak flame at its largest: the landscape cards are small (handoff 19g4). */
    val flameSize: Dp,
) {
    companion object {
        val Portrait = Metrics(
            header = ProfileHeaderMetrics.Portrait, timerSize = 64, cardCorner = CardCorner, cardPadding = 12.dp, cardLabelSize = 12, cardValueSize = 18,
            dayDateSize = 14, dayTimeSize = 32, dayNoneSize = 20, calendar = CalendarMetrics.Portrait, flameSize = StreakFlameSize.Full,
        )
        val Landscape = Metrics(
            header = ProfileHeaderMetrics.Landscape, timerSize = 56, cardCorner = CardCornerLandscape, cardPadding = 10.dp, cardLabelSize = 11, cardValueSize = 15,
            dayDateSize = 13, dayTimeSize = 16, dayNoneSize = 15, calendar = CalendarMetrics.Landscape, flameSize = StreakFlameSize.Small,
        )
    }
}

/** The practice screen (spec 3.12, handoff 10a–10g, 10j). Stateless. */
@Composable
fun PracticeScreen(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    // The window into the journey (spec 3.23) comes as a slot: it lives on its own flow, not in PracticeState.
    journeyCard: @Composable (compact: Boolean) -> Unit = {},
) {
    // Two movements at most (spec 3.16): while the streak flame sways, the start button rests. Drawing only.
    val flameSways = remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (maxWidth > maxHeight) {
            LandscapeLayout(state, onIntent, zone, journeyCard, flameSways)
        } else {
            PortraitLayout(state, onIntent, zone, journeyCard, flameSways)
        }
    }
    when (val sheet = state.sheet) {
        is PracticeSheet.Summary -> SummarySheet(sheet, state.stepMinutes, onIntent)
        is PracticeSheet.EditTime -> EditTimeSheet(sheet, state.stepMinutes, onIntent)
        is PracticeSheet.Profile -> ProfileSheet(sheet, state.header, onIntent)
        PracticeSheet.Trophies -> TrophiesSheet(state.trophies, state.header.totalMs, onIntent)
        is PracticeSheet.Recap -> RecapSheet(sheet.recap, onIntent)
        // The gift waits for the other sheets: the reducer offers it only when none is open.
        null -> state.gift?.let { GiftSheet(it, onIntent) }
    }
}

@Composable
private fun PortraitLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    zone: ZoneId,
    journeyCard: @Composable (Boolean) -> Unit,
    flameSways: MutableState<Boolean>,
) {
    val metrics = Metrics.Portrait
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            Header(state, onIntent, metrics)
            if (!state.loading) {
                journeyCard(false)
                MainAction(state, onIntent, metrics, flameSways)
                SummaryCards(state, metrics, flameSways)
                PracticeCalendar(
                    month = state.month,
                    cells = state.cells,
                    canGoForward = state.canGoForward,
                    onMonthBack = { onIntent(PracticeIntent.MonthBack) },
                    onMonthForward = { onIntent(PracticeIntent.MonthForward) },
                    onDaySelected = { onIntent(PracticeIntent.DaySelected(it)) },
                    metrics = metrics.calendar,
                )
                SelectedDayBlock(state.selected, onIntent, metrics, zone)
            }
        }
    }
}

@Composable
private fun LandscapeLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    zone: ZoneId,
    journeyCard: @Composable (Boolean) -> Unit,
    flameSways: MutableState<Boolean>,
) {
    val metrics = Metrics.Landscape
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(LandscapeLeftColumn)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            Header(state, onIntent, metrics)
            if (!state.loading) {
                MainAction(state, onIntent, metrics, flameSways)
                SummaryCards(state, metrics, flameSways)
                journeyCard(true)
            }
        }
        if (!state.loading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(BlockGap),
            ) {
                PracticeCalendar(
                    month = state.month,
                    cells = state.cells,
                    canGoForward = state.canGoForward,
                    onMonthBack = { onIntent(PracticeIntent.MonthBack) },
                    onMonthForward = { onIntent(PracticeIntent.MonthForward) },
                    onDaySelected = { onIntent(PracticeIntent.DaySelected(it)) },
                    metrics = metrics.calendar,
                )
                SelectedDayBlock(state.selected, onIntent, metrics, zone)
            }
        }
    }
}

/**
 * While the data is being read the header keeps its place but stays unseen: an empty one would
 * flash "0 мин · Уровень 1" at someone with hundreds of hours.
 */
@Composable
private fun Header(state: PracticeState, onIntent: (PracticeIntent) -> Unit, metrics: Metrics) {
    ProfileHeader(
        header = state.header,
        metrics = metrics.header,
        onProfileClick = { if (!state.loading) onIntent(PracticeIntent.ProfileClicked) },
        onTrophiesClick = { if (!state.loading) onIntent(PracticeIntent.TrophiesClicked) },
        modifier = if (state.loading) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
        animate = !state.loading,
    )
}

@Composable
private fun MainAction(state: PracticeState, onIntent: (PracticeIntent) -> Unit, metrics: Metrics, flameSways: MutableState<Boolean>) {
    val colors = MaterialTheme.colorScheme
    // A sheet over the screen has the eye: the start button rests under it.
    val sheetOpen = state.sheet != null || state.gift != null
    AnimatedContent(
        targetState = state.runningMs != null,
        transitionSpec = {
            // unclipped: the glow of the start button lies past its bounds and must not be cut while it fades
            (fadeIn(tween(ACTION_SWITCH_MS)) + scaleIn(tween(ACTION_SWITCH_MS), initialScale = ACTION_SWITCH_SCALE))
                .togetherWith(fadeOut(tween(ACTION_SWITCH_MS)))
                .using(SizeTransform(clip = false))
        },
        label = "mainAction",
    ) { running ->
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (running) {
                val timer = Formats.timer(state.runningMs ?: 0L)
                val timerDescription = stringResource(R.string.practice_timer_description, timer)
                Text(
                    text = timer,
                    modifier = Modifier.semantics { contentDescription = timerDescription },
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = metrics.timerSize.sp,
                        lineHeight = (metrics.timerSize + 4).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RunningDot(
                        elapsedMs = state.runningMs ?: 0L,
                        color = colors.primary,
                        ringColor = ViolinTheme.practiceColors.timerRing,
                    )
                    Text(
                        text = stringResource(R.string.practice_running),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    )
                }
                // the block that runs within the practice (spec 3.28, handoff 30g4): a quiet line, no motion of its own
                state.runningBlock?.let { block ->
                    val left = block.minutesLeft
                    Text(
                        text = stringResource(
                            R.string.block_line,
                            block.title,
                            if (left == null) stringResource(R.string.block_done) else stringResource(Formats.plural(left, R.string.block_left_one, R.string.block_left_few, R.string.block_left_many), left),
                        ),
                        modifier = Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                OutlinedButton(
                    onClick = { onIntent(PracticeIntent.StopClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(ButtonHeight),
                    shape = RoundedCornerShape(ButtonCorner),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.outlineVariant)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                ) {
                    // A flag, not a square: the square is the stop of a recording (spec 3.16).
                    IconLabel(
                        icon = AppIcons.Flag,
                        text = stringResource(R.string.practice_stop),
                        iconSize = IconSizes.InFilledButton,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                // Alive (spec 3.16): the first thing seen when the app opens, and the one that asks to be pressed.
                StartPracticeButton(
                    onClick = { onIntent(PracticeIntent.StartClicked) },
                    calm = { sheetOpen || flameSways.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeight),
                )
                Text(
                    text = when {
                        !state.hasHistory -> stringResource(R.string.practice_empty)
                        state.todayMs > 0 -> stringResource(R.string.practice_today, Formats.minutesInWords(state.todayMs))
                        else -> stringResource(R.string.practice_today_none)
                    },
                    modifier = Modifier.padding(top = TodayLineGap),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCards(state: PracticeState, metrics: Metrics, flameSways: MutableState<Boolean>) {
    val none = stringResource(R.string.practice_no_value)
    // Another month, or the first numbers after loading, are other numbers — not these ones grown.
    val scope = state.month to state.loading
    val rollFrom = PracticeMotion.ROLL_FROM_MINUTES * MS_PER_MINUTE
    val weekMs = rolledValue(state.summary.weekMs, scope, PracticeMotion.ROLL_MS, rollFrom)
    val monthMs = rolledValue(state.summary.monthMs, scope, PracticeMotion.ROLL_MS, rollFrom)
    val streak = rolledValue(state.summary.streakDays.toLong(), scope, PracticeMotion.ROLL_STREAK_MS, rollFrom = STREAK_ROLL_FROM)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(
            label = stringResource(R.string.practice_week),
            value = if (state.hasHistory) Formats.minutesInWords(weekMs) else none,
            metrics = metrics,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = stringResource(R.string.practice_month),
            value = if (state.hasHistory) Formats.minutesInWords(monthMs) else none,
            metrics = metrics,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = stringResource(R.string.practice_streak),
            value = if (state.hasHistory) streak.toString() else none,
            metrics = metrics,
            modifier = Modifier.weight(1f),
        ) {
            // The flame follows the streak itself, not the number rolling towards it; while a practice runs it stands still.
            StreakFlame(
                streakDays = if (state.hasHistory) state.summary.streakDays else 0,
                running = state.runningMs != null,
                scope = scope,
                maxSize = metrics.flameSize,
                onSway = { flameSways.value = it },
            )
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, metrics: Metrics, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colors.surfaceContainer, RoundedCornerShape(metrics.cardCorner))
            .padding(metrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Three cards share 280 dp in landscape: «1 ч 36 мин» shrinks rather than clips.
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_CARD_LABEL_SIZE.sp, maxFontSize = metrics.cardLabelSize.sp, stepSize = 1.sp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = metrics.cardLabelSize.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = value,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_CARD_VALUE_SIZE.sp, maxFontSize = metrics.cardValueSize.sp, stepSize = 1.sp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = metrics.cardValueSize.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.invoke()
        }
    }
}

/** Date, time and the day's records (handoff 10c1, 10c2). Cross-fades when another day is picked. */
@Composable
private fun SelectedDayBlock(selected: SelectedDay, onIntent: (PracticeIntent) -> Unit, metrics: Metrics, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn(tween(DAY_CROSSFADE_MS)).togetherWith(fadeOut(tween(DAY_CROSSFADE_MS))) },
        contentKey = { it.date },
        label = "selectedDay",
    ) { day ->
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Formats.dayWithWeekday(day.date),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = metrics.dayDateSize.sp),
                )
                if (day.isToday) {
                    Text(
                        text = stringResource(R.string.practice_day_today),
                        modifier = Modifier
                            .border(1.dp, colors.primary, RoundedCornerShape(TodayChipCorner))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = colors.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (day.totalMs > 0) {
                    Text(
                        text = Formats.minutesInWords(day.totalMs),
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = metrics.dayTimeSize.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES,
                        ),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.practice_day_none),
                        modifier = Modifier.weight(1f),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.dayNoneSize.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                OutlinedButton(
                    onClick = { onIntent(PracticeIntent.EditTimeClicked) },
                    modifier = Modifier.height(ActionHeight),
                    shape = RoundedCornerShape(ActionCorner),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.outlineVariant)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                ) {
                    IconLabel(
                        icon = if (day.totalMs > 0) AppIcons.Pencil else AppIcons.Plus,
                        text = stringResource(if (day.totalMs > 0) R.string.practice_edit_time else R.string.practice_add_time),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    )
                }
            }
            if (day.sessions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.practice_day_records),
                    modifier = Modifier.padding(top = 4.dp),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Column(verticalArrangement = Arrangement.spacedBy(SessionCardGap)) {
                    day.sessions.forEach { card ->
                        SessionCard(
                            card = card,
                            zone = zone,
                            onClick = { onIntent(PracticeIntent.SessionClicked(card.id)) },
                        )
                    }
                }
            }
        }
    }
}
