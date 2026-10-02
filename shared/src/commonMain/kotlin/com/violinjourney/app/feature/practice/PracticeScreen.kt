package com.violinjourney.app.feature.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.domain.events.Reminder
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.feature.journey.WindowLook
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.FirstWeekCard
import com.violinjourney.app.feature.practice.components.PathRow
import com.violinjourney.app.feature.practice.components.PracticeSheetHost
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import com.violinjourney.app.feature.practice.components.ReminderCard
import com.violinjourney.app.feature.practice.components.ReminderFit
import com.violinjourney.app.feature.practice.components.RunningCard
import com.violinjourney.app.feature.practice.components.ShownNumbers
import com.violinjourney.app.feature.practice.components.ShownReminder
import com.violinjourney.app.feature.practice.components.StartPracticeButton
import com.violinjourney.app.feature.practice.components.TodayCard
import com.violinjourney.app.feature.practice.components.TodayMetrics
import com.violinjourney.app.feature.practice.components.WeekCard
import com.violinjourney.app.feature.practice.components.rememberShownNumbers
import com.violinjourney.app.feature.practice.components.rememberShownReminder
import com.violinjourney.app.feature.practice.components.settled
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_stop
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

private val ScreenPadding = 16.dp
private val BlockGap = 12.dp
private val MaxContentWidth = 560.dp
private val LandscapeLeftColumn = 280.dp

/** The sides of the bottom zone of «Занятия» are the fields of the screen (5.29 R2): the button stands flush with the cards. */
private val DockSide = 16.dp
private const val ACTION_SWITCH_MS = 200

/** Sizes that differ between the layouts (spec 5.29 R2). */
@Immutable
private data class Metrics(val today: TodayMetrics, val calendar: CalendarMetrics) {
    companion object {
        val Portrait = Metrics(TodayMetrics.Portrait, CalendarMetrics.Portrait)
        val Landscape = Metrics(TodayMetrics.Landscape, CalendarMetrics.Landscape)
    }
}

/**
 * The practice screen (spec 3.12, 3.36.2). Stateless. It answers from the top down: where I am on the path — how today and the
 * week go — where my home is — the month; the main action is at the bottom, under the thumb: the middle scrolls, the button and
 * the tab bar stand.
 */
@Composable
fun PracticeScreen(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    modifier: Modifier = Modifier,
    // asked once: a new zone on every recomposition is a new object each time, and on iOS a read of its file
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    // The window into the journey (spec 3.23) comes as a slot: it lives on its own flow, not in PracticeState. The screen says how it
    // stands (3.36.2): the picture as high as the first screen leaves room for, a line, or beside its line in landscape.
    journeyCard: @Composable (look: WindowLook) -> Unit = {},
    // The clock of the running practice lives on its own flow too, and is read only by the parts that show the time: a tick
    // redraws them, not the screen. Null — not read yet.
    timer: () -> PracticeTimer? = { null },
) {
    // Two movements at most (spec 3.16): while the streak flame sways, the start button rests. Drawing only.
    val flameSways = remember { mutableStateOf(false) }
    // the photo of the ring — in the path row and in «Мой путь» — from the cache of small pictures: there from the first frame
    val photo = rememberSmallFileImage(state.header.avatarPath)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (maxWidth > maxHeight) {
            // the right column: what is left of the width after the left one and the field at the end
            val rightColumn = maxWidth - LandscapeLeftColumn - ScreenPadding
            val windowLook = WindowLook.Beside(WindowFit.besideWidth(rightColumn))
            val legendBeside = CalendarMetrics.Landscape.legendBeside(rightColumn)
            LandscapeLayout(state, onIntent, window = { journeyCard(windowLook) }, flameSways, timer, photo, legendBeside)
        } else {
            PortraitLayout(state, onIntent, journeyCard, flameSways, timer, photo, height = maxHeight)
        }
    }
    // One frame for every sheet of «Занятия» and the gift (spec 3.36.3): one of them in the place of another changes what the frame
    // shows, in place — «Время за день» over the sheet of the day, «Трофеи» and «Имя и фото» over «Мой путь», the recap after
    // «Сохранить» (3.31), the gift after the recap; the frame slides away when the last one goes. While a record opened from the
    // sheet of the day is on the screen, the sheets step aside: the model keeps them, and the sheet of the day rises again when the
    // screen is back. The gift waits for the other sheets: the reducer offers it only when none is open.
    PracticeSheetHost(state, onIntent, zone, photo)
}

/**
 * Portrait: the path row, «Сегодня» (or the running practice), the window of the home and the month scroll over the bottom zone.
 * [height] — of the whole screen body: less the zone, it is the scroll window the picture of the window is fitted to.
 */
@Composable
private fun PortraitLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    journeyCard: @Composable (WindowLook) -> Unit,
    flameSways: MutableState<Boolean>,
    timer: () -> PracticeTimer?,
    photo: ImageBitmap?,
    height: Dp,
) {
    val metrics = Metrics.Portrait
    val numbers = rememberShownNumbers(state)
    // «Сегодня» as it stands once its figures have rolled: the window is fitted to where they go, not to each frame of the roll — the
    // same content while they roll, so the still twin is neither recomposed nor remeasured to another height meanwhile
    val settled = numbers.settled()
    val today = state.today
    val floorMinutes = state.weekFloorMinutes
    val still = remember(settled, today, floorMinutes) {
        @Composable { StillToday(settled, today, floorMinutes, metrics.today) }
    }
    // the reminder: compact in a window lower than 700 (360 × 640, spec 3.36.9), and the window of the home is fitted to it as well
    val compact = ReminderFit.compact(windowHeight(), lying = false)
    val reminder = rememberShownReminder(state.reminder, loading = state.loading, unseen = state.reminderEpoch)
    val reminderTwin = remember(reminder.value, compact) {
        @Composable {
            val shown = reminder.value
            if (shown != null) StillReminder(shown, compact)
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AppDock(
            dock = { MainAction(state, onIntent, flameSways) },
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
            metrics = currentDockMetrics().copy(side = DockSide),
        ) {
            val dockInset = LocalDockInset.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenPadding, top = ScreenPadding, end = ScreenPadding, bottom = dockInset + ScreenPadding),
            ) {
                PortraitBody(
                    viewport = height - dockInset,
                    loading = state.loading,
                    path = { Path(state, onIntent, photo) },
                    today = {
                        TodayBlock(state, numbers, metrics.today, flameSways, timer, onIntent, plainWeek = false) {
                            Reminder(reminder, compact, lying = false, onIntent)
                        }
                    },
                    still = still,
                    reminder = reminderTwin,
                    window = journeyCard,
                    calendar = { Calendar(state, numbers, metrics.calendar, onIntent, legendBeside = false) },
                )
            }
        }
    }
}

private enum class BodySlot { Path, Today, Still, Reminder, Under, Window, Calendar }

/**
 * The blocks of the portrait one under another, 12 apart, as a column would put them — but the window of the home learns in the
 * same pass how it stands (spec 3.36.2, «Маленький экран и крупный шрифт»): its picture takes what is left of the first screen
 * ([viewport]) under the top field, the path row, «Сегодня» and the reminder and over what stands under the picture, 148 at most, and
 * with less than 72 left the window is a line ([WindowFit]). «Сегодня» is measured as the layout without a running practice has it —
 * [still], never placed — so beginning or ending a practice does not change the window: while one runs its window keeps its height
 * below the fold, and the card of the running practice growing and shrinking in its fade is never measured for it. The reminder too
 * is a still twin ([reminder], spec 3.36.9): the card as it is shown — the one going out held until it has faded — measured and never
 * placed, nothing when there is none; so the window gives way to the card that comes and takes its place back once the card is gone,
 * each time once. What stands under the picture — the line, taller when its call takes more lines, and the bar while the takts are
 * short — is the window's own [WindowLook.UnderPicture], measured and never placed, so the whole window fits the first screen. All of
 * it is measured before the window is composed, so the first frame does not show a picture of 148 to take it back. While the data is
 * read — only the path row, keeping its place.
 */
@Composable
private fun PortraitBody(
    viewport: Dp,
    loading: Boolean,
    path: @Composable () -> Unit,
    today: @Composable () -> Unit,
    still: @Composable () -> Unit,
    reminder: @Composable () -> Unit,
    window: @Composable (WindowLook) -> Unit,
    calendar: @Composable () -> Unit,
) {
    val card = rememberUpdatedState(window)
    val windowContent = remember { LookedWindow(card) }
    // what stands under the picture, alone: measured, never placed, silent — the card itself, so it is measured as it is drawn
    val underContent: @Composable () -> Unit = remember {
        @Composable { Box(Modifier.clearAndSetSemantics { }) { card.value(WindowLook.UnderPicture) } }
    }
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val child = Constraints(maxWidth = width)
        val gap = BlockGap.roundToPx()
        // one under another, 12 apart, as a column puts them; a slot that shows nothing (the window before the road is read)
        // takes no place and no gap
        val placed = mutableListOf<Placeable>()
        fun stacked(blocks: List<Placeable>): Int = blocks.sumOf { it.height } + gap * blocks.size
        val pathBlocks = subcompose(BodySlot.Path, path).map { it.measure(child) }
        placed += pathBlocks
        if (!loading) {
            val stillBlocks = subcompose(BodySlot.Still, still).map { it.measure(child) }
            val reminderBlocks = subcompose(BodySlot.Reminder, reminder).map { it.measure(child) }
            val above = (ScreenPadding.roundToPx() + stacked(pathBlocks) + stacked(stillBlocks) + stacked(reminderBlocks)).toDp()
            val under = subcompose(BodySlot.Under, underContent).sumOf { it.measure(child).height }.toDp()
            val look = WindowFit.picture(viewport, above, under)?.let { WindowLook.Picture(it) } ?: WindowLook.Line
            placed += subcompose(BodySlot.Today, today).map { it.measure(child) }
            placed += subcompose(BodySlot.Window, windowContent.of(look)).map { it.measure(child) }
            placed += subcompose(BodySlot.Calendar, calendar).map { it.measure(child) }
        }
        val total = (stacked(placed) - gap).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            placed.forEach { block ->
                block.place(0, y)
                y += block.height + gap
            }
        }
    }
}

/**
 * The window's slot content for one look at a time: the same lambda while the look stays, so measuring again composes nothing anew;
 * [card] is read inside it, so a new card is recomposed in place.
 */
private class LookedWindow(private val card: State<@Composable (WindowLook) -> Unit>) {
    private var look: WindowLook? = null
    private var content: @Composable () -> Unit = {}

    fun of(look: WindowLook): @Composable () -> Unit {
        if (look != this.look) {
            this.look = look
            content = { card.value(look) }
        }
        return content
    }
}

/**
 * «Сегодня» as the layout without a running practice shows it — the first run's card or the card of today — composed only to be
 * measured: still (no flame, no motion to wake a frame), silent, and telling nothing to the start button. [numbers] are the
 * settled ones (`ShownNumbers.settled`): the figures it rolls to, so its height does not follow the roll of the living «Сегодня».
 */
@Composable
private fun StillToday(numbers: ShownNumbers, today: LocalDate, floorMinutes: Int, metrics: TodayMetrics) {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            if (numbers.hasHistory) {
                TodayCard(numbers, today, floorMinutes, metrics, onSway = {})
            } else {
                FirstWeekCard(numbers, today, floorMinutes, metrics)
            }
        }
    }
}

/**
 * Landscape (practice-extra.html, screen 1): the columns stay. The left one, 280 wide — «Сегодня» compact, or the running
 * practice and the bars of the week under it without a card, and the compact reminder under «Сегодня» or the timer (spec 3.36.9) —
 * scrolls over the bottom zone with «Начать» / «Закончить» at the bottom of the column, its fade short (over the button there are 236
 * dp on 892 × 412). The right one scrolls by itself: the path row, the window of the home — a row of 96 with the picture on its left
 * ([window]) — the calendar, the legend of its kinds beside the grid where there is room for it ([legendBeside]).
 */
@Composable
private fun LandscapeLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    window: @Composable () -> Unit,
    flameSways: MutableState<Boolean>,
    timer: () -> PracticeTimer?,
    photo: ImageBitmap?,
    legendBeside: Boolean,
) {
    val metrics = Metrics.Landscape
    val numbers = rememberShownNumbers(state)
    val reminder = rememberShownReminder(state.reminder, loading = state.loading, unseen = state.reminderEpoch)
    val compact = ReminderFit.compact(windowHeight(), lying = true)
    Row(modifier = Modifier.fillMaxSize()) {
        AppDock(
            dock = { MainAction(state, onIntent, flameSways) },
            modifier = Modifier.width(LandscapeLeftColumn).fillMaxHeight(),
            fade = DockDefaults.FadeLeftColumn,
            metrics = currentDockMetrics().copy(side = DockSide),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenPadding, top = ScreenPadding, end = ScreenPadding, bottom = LocalDockInset.current + ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(BlockGap),
            ) {
                if (!state.loading) {
                    TodayBlock(state, numbers, metrics.today, flameSways, timer, onIntent, plainWeek = true) {
                        Reminder(reminder, compact = compact, lying = true, onIntent)
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            Path(state, onIntent, photo)
            if (!state.loading) {
                window()
                Calendar(state, numbers, metrics.calendar, onIntent, legendBeside)
            }
        }
    }
}

/**
 * The month (spec 3.36.2, 3.36.9): under the fold, below the window of the home; the time of the month rolls as the numbers of
 * «Сегодня» do. A tapped day opens its sheet — a day to come too; under the grid, or beside it, the legend of the kinds of its events.
 */
@Composable
private fun Calendar(state: PracticeState, numbers: ShownNumbers, metrics: CalendarMetrics, onIntent: (PracticeIntent) -> Unit, legendBeside: Boolean) {
    PracticeCalendar(
        month = state.month,
        cells = state.cells,
        canGoForward = state.canGoForward,
        onMonthBack = { onIntent(PracticeIntent.MonthBack) },
        onMonthForward = { onIntent(PracticeIntent.MonthForward) },
        onDaySelected = { onIntent(PracticeIntent.DaySelected(it)) },
        currentYear = state.today.year,
        monthMs = numbers.monthMs,
        monthDays = numbers.monthDays,
        rolledMonthMs = numbers.rolledMonthMs,
        metrics = metrics,
        legend = state.legend,
        hint = state.eventsHint,
        monthEvents = state.monthEvents,
        monthIsFuture = state.monthIsFuture,
        legendBeside = legendBeside,
    )
}

/**
 * The path row. While the data is being read it keeps its place but stays unseen and silent: an empty one would flash
 * «Уровень 1 · Первый звук» at someone with hundreds of hours.
 */
@Composable
private fun Path(state: PracticeState, onIntent: (PracticeIntent) -> Unit, photo: ImageBitmap?) {
    PathRow(
        header = state.header,
        photo = photo,
        onClick = { onIntent(PracticeIntent.PathClicked) },
        modifier = if (state.loading) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
        animate = !state.loading,
        enabled = !state.loading,
    )
}

/**
 * «Сегодня», or the card of the running practice with the week under it, or the first run: the running practice and «Сегодня»
 * take each other's place with the short fade of 3.12. [plainWeek] — the week under the running practice without a card
 * (landscape). The [reminder] stands in the block (spec 3.36.9): under «Сегодня» or the first run, and between the card of the running
 * practice and the week — so it changes with them in their fade, and has no fade of its own at the start and the stop of a practice.
 */
@Composable
private fun TodayBlock(
    state: PracticeState,
    numbers: ShownNumbers,
    metrics: TodayMetrics,
    flameSways: MutableState<Boolean>,
    timer: () -> PracticeTimer?,
    onIntent: (PracticeIntent) -> Unit,
    plainWeek: Boolean,
    reminder: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = state.running,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(tween(ACTION_SWITCH_MS)).togetherWith(fadeOut(tween(ACTION_SWITCH_MS))).using(SizeTransform(clip = false))
        },
        label = "today",
    ) { running ->
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
            when {
                running -> {
                    // only a practice begun today adds to today and hatches its bar: one left running past midnight belongs to yesterday
                    RunningCard(
                        timer = timer,
                        numbers = numbers,
                        todaySavedMs = state.todayMs,
                        withToday = state.withRunningToday,
                        metrics = metrics,
                        onBackToLive = if (metrics.backToLive) ({ onIntent(PracticeIntent.BackToLiveClicked) }) else null,
                    )
                    reminder()
                    WeekCard(numbers, state.today, state.weekFloorMinutes, hatch = state.runningToday, timer = timer, metrics = metrics, plain = plainWeek)
                }
                !numbers.hasHistory -> {
                    FirstWeekCard(numbers, state.today, state.weekFloorMinutes, metrics)
                    reminder()
                }
                else -> {
                    TodayCard(numbers, state.today, state.weekFloorMinutes, metrics, onSway = { flameSways.value = it })
                    reminder()
                }
            }
        }
    }
}

/**
 * The reminder as shown ([rememberShownReminder]): its strength read where it is drawn, so its fade does not recompose the block.
 * «ещё N» opens the sheet of the day of the first event it stands for, and the calendar comes to its month (spec 3.36.9).
 */
@Composable
private fun Reminder(shown: ShownReminder, compact: Boolean, lying: Boolean, onIntent: (PracticeIntent) -> Unit) {
    val reminder = shown.value ?: return
    ReminderCard(
        reminder = reminder,
        compact = compact,
        lying = lying,
        onMore = { date -> onIntent(PracticeIntent.DaySelected(date, moveMonth = true)) },
        modifier = Modifier.graphicsLayer { alpha = shown.alpha() },
    )
}

/** The reminder for the window of the home to be fitted to: the card as shown, composed only to be measured — still and silent. */
@Composable
private fun StillReminder(reminder: Reminder, compact: Boolean) {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            ReminderCard(reminder, compact, lying = false, onMore = {})
        }
    }
}

/** The height of the window the app is in now, as the bottom zone reads it: the card of the reminder is chosen by it ([ReminderFit]). */
@Composable
private fun windowHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

/**
 * The bottom zone of «Занятия» (spec 3.36.2): the living «Начать занятие», or «Закончить занятие» outlined with its flag in the
 * same place while a practice runs — the short fade of 3.12 between them. It keeps its height and stays empty while the data is
 * read: neither of them flashes. The living button rests while any sheet of «Занятия» or the gift is open and while the flame
 * sways (3.16); its glow is not cut by the zone and lies on the tab bar.
 */
@Composable
private fun DockScope.MainAction(state: PracticeState, onIntent: (PracticeIntent) -> Unit, flameSways: MutableState<Boolean>) {
    if (state.loading) {
        Spacer(Modifier.fillMaxWidth().height(buttonHeight))
        return
    }
    val sheetOpen = state.sheet != null || state.gift != null
    val low = compact
    val height = buttonHeight
    AnimatedContent(
        targetState = state.running,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            // unclipped: the glow of the start button lies past its bounds
            fadeIn(tween(ACTION_SWITCH_MS)).togetherWith(fadeOut(tween(ACTION_SWITCH_MS))).using(SizeTransform(clip = false))
        },
        label = "mainAction",
    ) { running ->
        if (running) {
            // A flag, not a square: the square is the stop of a recording (spec 3.16).
            AppButton(
                text = stringResource(Res.string.practice_stop),
                onClick = { onIntent(PracticeIntent.StopClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonStyle.Outline,
                icon = AppIcons.Flag,
                compact = low,
            )
        } else {
            // Alive (spec 3.16): the first thing seen when the app opens, and the one that asks to be pressed.
            StartPracticeButton(
                onClick = { onIntent(PracticeIntent.StartClicked) },
                calm = { sheetOpen || flameSways.value },
                modifier = Modifier.fillMaxWidth().height(height),
            )
        }
    }
}
