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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.DaySheet
import com.violinjourney.app.feature.practice.components.EditTimeSheet
import com.violinjourney.app.feature.practice.components.FirstWeekCard
import com.violinjourney.app.feature.practice.components.GiftSheet
import com.violinjourney.app.feature.practice.components.PathRow
import com.violinjourney.app.feature.practice.components.PathSheet
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import com.violinjourney.app.feature.practice.components.ProfileSheet
import com.violinjourney.app.feature.practice.components.RecapSheet
import com.violinjourney.app.feature.practice.components.RunningCard
import com.violinjourney.app.feature.practice.components.ShownNumbers
import com.violinjourney.app.feature.practice.components.StartPracticeButton
import com.violinjourney.app.feature.practice.components.SummarySheet
import com.violinjourney.app.feature.practice.components.TodayCard
import com.violinjourney.app.feature.practice.components.TodayMetrics
import com.violinjourney.app.feature.practice.components.TrophiesSheet
import com.violinjourney.app.feature.practice.components.WeekCard
import com.violinjourney.app.feature.practice.components.rememberShownNumbers
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_stop
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
    // The window into the journey (spec 3.23) comes as a slot: it lives on its own flow, not in PracticeState.
    journeyCard: @Composable (compact: Boolean) -> Unit = {},
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
            LandscapeLayout(state, onIntent, journeyCard, flameSways, timer, photo)
        } else {
            PortraitLayout(state, onIntent, journeyCard, flameSways, timer, photo)
        }
    }
    // Each sheet is always there and shows itself when it has something: one closed by its own button slides away as a
    // swiped one does — but only when nothing comes in its place: the recap after «Сохранить» (spec 3.31), the gift
    // after a sheet, «Трофеи» and «Профиль» in the place of «Мой путь», «Время за день» in the place of the sheet of the day
    // and back (3.36.2) take the place at once. While a record opened from the sheet of the day is on the screen, the sheets
    // step aside: the model keeps them, and the sheet of the day rises again when the screen is back.
    val sheet = state.sheet.takeUnless { state.sheetsAway }
    val slideAway = sheet == null && state.gift == null
    SummarySheet(sheet as? PracticeSheet.Summary, state.stepMinutes, onIntent, slideAway)
    DaySheet(state.selected.takeIf { sheet is PracticeSheet.Day }, onIntent, zone, slideAway)
    EditTimeSheet(sheet as? PracticeSheet.EditTime, state.stepMinutes, onIntent, slideAway)
    PathSheet(state.header.takeIf { sheet == PracticeSheet.Path }, photo, onIntent, slideAway)
    ProfileSheet(sheet as? PracticeSheet.Profile, state.header, onIntent, slideAway)
    // no button closes it, only a swipe
    if (sheet == PracticeSheet.Trophies) TrophiesSheet(state.trophies, state.header.totalMs, onIntent)
    RecapSheet((sheet as? PracticeSheet.Recap)?.recap, onIntent, slideAway)
    // The gift waits for the other sheets: the reducer offers it only when none is open.
    GiftSheet(state.gift, onIntent, slideAway = sheet == null)
}

@Composable
private fun PortraitLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    journeyCard: @Composable (Boolean) -> Unit,
    flameSways: MutableState<Boolean>,
    timer: () -> PracticeTimer?,
    photo: ImageBitmap?,
) {
    val metrics = Metrics.Portrait
    val numbers = rememberShownNumbers(state)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AppDock(
            dock = { MainAction(state, onIntent, flameSways) },
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
            metrics = currentDockMetrics().copy(side = DockSide),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenPadding, top = ScreenPadding, end = ScreenPadding, bottom = LocalDockInset.current + ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(BlockGap),
            ) {
                Path(state, onIntent, photo)
                if (!state.loading) {
                    TodayBlock(state, numbers, metrics.today, flameSways, timer, onIntent, plainWeek = false)
                    journeyCard(false)
                    Calendar(state, numbers, metrics.calendar, onIntent)
                }
            }
        }
    }
}

/**
 * Landscape (practice-extra.html, screen 1): the columns stay. The left one, 280 wide — «Сегодня» compact, or the running
 * practice and the bars of the week under it without a card — scrolls over the bottom zone with «Начать» / «Закончить» at the
 * bottom of the column, its fade short (over the button there are 236 dp on 892 × 412). The right one scrolls by itself: the path
 * row, the window of the home, the calendar.
 */
@Composable
private fun LandscapeLayout(
    state: PracticeState,
    onIntent: (PracticeIntent) -> Unit,
    journeyCard: @Composable (Boolean) -> Unit,
    flameSways: MutableState<Boolean>,
    timer: () -> PracticeTimer?,
    photo: ImageBitmap?,
) {
    val metrics = Metrics.Landscape
    val numbers = rememberShownNumbers(state)
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
                if (!state.loading) TodayBlock(state, numbers, metrics.today, flameSways, timer, onIntent, plainWeek = true)
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
                journeyCard(true)
                Calendar(state, numbers, metrics.calendar, onIntent)
            }
        }
    }
}

/**
 * The month (spec 3.36.2): under the fold, below the window of the home; the time of the month rolls as the numbers of «Сегодня» do.
 * A tapped day opens its sheet.
 */
@Composable
private fun Calendar(state: PracticeState, numbers: ShownNumbers, metrics: CalendarMetrics, onIntent: (PracticeIntent) -> Unit) {
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
 * (landscape).
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
                    WeekCard(numbers, state.today, state.weekFloorMinutes, hatch = state.runningToday, timer = timer, metrics = metrics, plain = plainWeek)
                }
                !numbers.hasHistory -> FirstWeekCard(numbers, state.today, state.weekFloorMinutes, metrics)
                else -> TodayCard(numbers, state.today, state.weekFloorMinutes, metrics, onSway = { flameSways.value = it })
            }
        }
    }
}

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
