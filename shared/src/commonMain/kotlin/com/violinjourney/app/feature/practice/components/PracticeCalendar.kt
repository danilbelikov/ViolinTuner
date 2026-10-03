package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_count_few
import com.violinjourney.app.shared.resources.event_count_many
import com.violinjourney.app.shared.resources.event_count_one
import com.violinjourney.app.shared.resources.practice_month_back
import com.violinjourney.app.shared.resources.practice_month_days_few
import com.violinjourney.app.shared.resources.practice_month_days_many
import com.violinjourney.app.shared.resources.practice_month_days_one
import com.violinjourney.app.shared.resources.practice_month_description
import com.violinjourney.app.shared.resources.practice_month_forward
import com.violinjourney.app.shared.resources.practice_month_line
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_weekdays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * The geometry of the calendar in the two layouts (spec 5.29 R2, R9; events-kinds.html, «Геометрия клетки»). A cell is as wide as the
 * grid over seven and [rowHeight] high whatever the day holds — the bottom of it, from [marksTop], is the place of the marks of its
 * events, so the grid does not jump when they come.
 */
@Immutable
data class CalendarMetrics(
    val rowHeight: Dp,
    val rowGap: Dp,
    /** The circle of the day, [circleTop] below the top of its cell, in the middle of its column. */
    val circle: Dp,
    val circleTop: Dp,
    /** Above the header, on top of the gap of the column the calendar stands in (12): 28 from the block before it, 16 in landscape. */
    val headerTop: Dp,
    /** Between the header and the names of the weekdays. */
    val headerBottom: Dp,
    /** The grid is never wider than this and stands at the left edge — the legend of the kinds may stand beside it; null — the whole width. */
    val maxWidth: Dp?,
    /** The top of the plate of the marks of a cell, from the top of the cell: 38, landscape 36 — its bottom is the bottom of the cell. */
    val marksTop: Dp,
) {
    /**
     * The circle in a column [column] wide: [circle], or less where the ring «выбран» — a gap and a ring outside the circle, Ø 48 in
     * all — would reach the circle of the next day (a column under 44: a screen under 340, «Размер экрана: максимальный» gives 320).
     * The ring may take the empty edge of the next cell, as on 360 (column 47), but never its circle: the next cell draws later and
     * would cover it, and its gap would cut the one before.
     */
    fun circleIn(column: Dp): Dp = minOf(circle, column - SelectedRingGap - SelectedRingWidth)

    /**
     * The marks in a column [column] wide (spec 5.29 R9, «Клетка»): signs of 10 from a column of 50, of 9 under it (360 — 47), of 8 under
     * 44 (320 — 41) — three signs and «+» never reach the next cell; their number does not change (5.28: three at most).
     */
    fun marksIn(column: Dp): MarkSize = when {
        column >= EventsDimens.MarksWideFrom -> MarkSize.Wide
        column >= EventsDimens.MarksNarrowFrom -> MarkSize.Narrow
        else -> MarkSize.Narrowest
    }

    /**
     * The marks of every cell of a grid laid out [gridPx] pixels wide at [density] pixels a dp: one size for the whole grid, chosen by
     * its column — its width over seven — and not by the whole pixels each cell gets. A row shares its pixels out unevenly: 350 dp at
     * 2.625 is 919 px, cells of 132 and 131 px — 50.3 and 49.9 dp, on either side of the 50 the size changes at. The half pixel the
     * rounding of the grid itself may have taken off is given back: a grid of 350 has a column of 50 at any density.
     */
    fun marksOfGrid(gridPx: Int, density: Float): MarkSize = marksIn(((gridPx + HALF_PIXEL) / density / DAYS_IN_WEEK).dp)

    /**
     * The legend of the kinds stands in a column beside the grid (spec 3.36.9, 5.29 R9): only where the grid has a width of its own and
     * the row it stands in leaves at least the gap and [EventsDimens.LegendBesideMin] beside it — 892 × 412 yes, 640 × 360 no; else it
     * stands under the grid, as in portrait.
     */
    fun legendBeside(rowWidth: Dp): Boolean =
        maxWidth != null && rowWidth - maxWidth >= EventsDimens.LegendBesideGap + EventsDimens.LegendBesideMin

    companion object {
        val Portrait = CalendarMetrics(
            rowHeight = 52.dp, rowGap = 4.dp, circle = 40.dp, circleTop = 4.dp, headerTop = 16.dp, headerBottom = 8.dp, maxWidth = null,
            marksTop = EventsDimens.MarksTop,
        )
        val Landscape = CalendarMetrics(
            rowHeight = 50.dp, rowGap = 2.dp, circle = 38.dp, circleTop = 4.dp, headerTop = 4.dp, headerBottom = 6.dp, maxWidth = 350.dp,
            marksTop = EventsDimens.MarksTopLying,
        )
    }
}

/**
 * The size of the marks of a cell (spec 5.29 R9): the mini sign, the gap between the signs and «+», the plate beyond them at each side,
 * and the box of «+».
 */
@Immutable
data class MarkSize(val sign: Dp, val gap: Dp, val side: Dp, val moreWidth: Dp, val moreHeight: Dp) {
    /** The plate of [signs] mini signs and «+» when [more]: three and «+» — 48 of the widest, 42.5 and 36 of the others. */
    fun plateWidth(signs: Int, more: Boolean): Dp {
        val items = signs + if (more) 1 else 0
        if (items == 0) return 0.dp
        return sign * signs + (if (more) moreWidth else 0.dp) + gap * (items - 1) + side * 2
    }

    companion object {
        val Wide = MarkSize(EventsDimens.MarkSign, EventsDimens.MarkGap, EventsDimens.MarkSide, EventsDimens.MoreWidth, EventsDimens.MoreHeight)
        val Narrow = MarkSize(EventsDimens.MarkSignNarrow, EventsDimens.MarkGapNarrow, EventsDimens.MarkSideNarrow, EventsDimens.MoreWidth, EventsDimens.MoreHeight)
        val Narrowest = MarkSize(
            EventsDimens.MarkSignNarrowest, EventsDimens.MarkGapNarrowest, EventsDimens.MarkSideNarrowest,
            EventsDimens.MoreWidthNarrowest, EventsDimens.MoreHeightNarrowest,
        )
    }
}

/** What the rounding of a grid to whole pixels may take off its width ([CalendarMetrics.marksOfGrid]). */
private const val HALF_PIXEL = 0.5f
private const val DAYS_IN_WEEK = 7

private val ArrowSize = 44.dp
private val WeekdaysBottom = 4.dp
private const val DISABLED_ARROW_ALPHA = 0.5f
private const val TABULAR_FIGURES = "tnum"
private const val MONTH_SLIDE_MS = 220
private val MonthSlide = 40.dp

/**
 * The month of «Занятия» (spec 3.36.2, 3.36.9): the header — the month («Сентябрь»; with its year only in another year), under it the
 * time of the month and its days with practice, or for a month to come the number of its events — the arrows of 44 with a touch of
 * 48, and the grid, Monday first ([MonthGrid]). A day with practice is a circle in the tone of its time (5.6); today is a ring inside
 * the circle; the day whose sheet is open, a second ring outside through a gap; the marks of its events lie over the bottom of the
 * cell. Any day is selectable, a day to come too. Under the grid — or beside it, [legendBeside] (landscape where there is room) — the
 * legend of the kinds of the month ([legend]), or the hint while there is no event at all ([hint]); a month without events has
 * nothing there. Both slide with the grid when the month changes: they belong to the month.
 *
 * [rolledMonthMs] is the time of the month as it rolls to [monthMs] (3.16); a reader hears [monthMs] itself.
 */
@Composable
fun PracticeCalendar(
    month: YearMonth,
    cells: List<CalendarCell?>,
    canGoForward: Boolean,
    onMonthBack: () -> Unit,
    onMonthForward: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    currentYear: Int,
    monthMs: Long,
    monthDays: Int,
    modifier: Modifier = Modifier,
    rolledMonthMs: Long = monthMs,
    metrics: CalendarMetrics = CalendarMetrics.Portrait,
    legend: List<EventKind> = emptyList(),
    hint: Boolean = false,
    monthEvents: Int = 0,
    monthIsFuture: Boolean = false,
    legendBeside: Boolean = false,
) {
    // the header, the weekdays and the grid are as wide as the grid: 350 at the left edge in landscape
    val gridWidth = if (metrics.maxWidth != null) Modifier.widthIn(max = metrics.maxWidth) else Modifier
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = gridWidth
                .fillMaxWidth()
                .padding(top = metrics.headerTop, bottom = metrics.headerBottom),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(forward = false, enabled = true, onClick = onMonthBack, description = stringResource(Res.string.practice_month_back))
            MonthTitle(month, currentYear, monthMs, rolledMonthMs, monthDays, monthEvents.takeIf { monthIsFuture }, Modifier.weight(1f))
            ArrowButton(forward = true, enabled = canGoForward, onClick = onMonthForward, description = stringResource(Res.string.practice_month_forward))
        }
        Weekdays(gridWidth)
        // Month change slides in the direction of the arrow (handoff `anims`, «Смена месяца»); the marks and the legend go with the grid.
        val slidePx = with(LocalDensity.current) { MonthSlide.roundToPx() }
        AnimatedContent(
            targetState = ShownMonth(month, cells, legend, hint),
            transitionSpec = {
                val slide = if (targetState.month > initialState.month) slidePx else -slidePx
                (slideInHorizontally(tween(MONTH_SLIDE_MS)) { slide } + fadeIn(tween(MONTH_SLIDE_MS)))
                    .togetherWith(slideOutHorizontally(tween(MONTH_SLIDE_MS)) { -slide } + fadeOut(tween(MONTH_SLIDE_MS)))
            },
            contentKey = { it.month },
            label = "month",
        ) { shown ->
            // A month is as wide as the column whatever it holds under its grid: the change of the month then slides it and changes
            // only its height (5 or 6 weeks), as in R2 — the legend has no movement of its own (spec 3.36.9), it is not unveiled by
            // a width growing from the grid's to the column's.
            if (legendBeside && metrics.maxWidth != null) {
                Row(Modifier.fillMaxWidth()) {
                    MonthGrid(shown.cells, metrics, onDaySelected, Modifier.width(metrics.maxWidth))
                    Spacer(Modifier.width(EventsDimens.LegendBesideGap))
                    // a column level with the first row of the grid, under the names of the weekdays
                    UnderGrid(shown, column = true, Modifier.weight(1f).padding(top = metrics.rowGap))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    MonthGrid(shown.cells, metrics, onDaySelected, gridWidth)
                    // under the grid, as in portrait (spec 3.36.9): as wide as the grid — 350 lying, not the column beside it
                    UnderGrid(shown, column = false, gridWidth.padding(top = EventsDimens.LegendTop))
                }
            }
        }
    }
}

/** What a month shows: its days, and under its grid the kinds of its events or the hint — they come and go with it. */
private data class ShownMonth(val month: YearMonth, val cells: List<CalendarCell?>, val legend: List<EventKind>, val hint: Boolean)

/** Under the grid (beside it, [column]): the legend of the kinds of the month, or the hint before the first event, or nothing. */
@Composable
private fun UnderGrid(shown: ShownMonth, column: Boolean, modifier: Modifier) {
    when {
        shown.legend.isNotEmpty() -> KindLegend(shown.legend, column, modifier)
        shown.hint -> EventsHint(modifier)
    }
}

/**
 * «Сентябрь» and «17 ч 27 мин · 24 дня» under it — a month without practice says only its name; a month to come says the number of its
 * events, «17 событий» ([futureEvents]; spec 3.36.9), and nothing without them: a month to come has no time, and «0 мин» would read as a
 * reproach. One heading to a reader, «Сентябрь, 17 ч 27 мин, 24 дня» or «Ноябрь, 17 событий», and a polite live region: after an arrow
 * the reader hears the month it came to.
 */
@Composable
private fun MonthTitle(month: YearMonth, currentYear: Int, monthMs: Long, rolledMonthMs: Long, monthDays: Int, futureEvents: Int?, modifier: Modifier) {
    if (futureEvents != null) {
        FutureMonthTitle(Formats.monthTitle(month, currentYear), futureEvents, modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val title = Formats.monthTitle(month, currentYear)
    val days = if (monthDays > 0) {
        stringResource(Formats.plural(monthDays, Res.string.practice_month_days_one, Res.string.practice_month_days_few, Res.string.practice_month_days_many), monthDays)
    } else {
        null
    }
    val description = days?.let { stringResource(Res.string.practice_month_description, title, Formats.minutesInWords(monthMs), it) } ?: title
    Column(
        modifier = modifier.clearAndSetSemantics {
            heading()
            liveRegion = LiveRegionMode.Polite
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (days != null) {
            Text(
                text = stringResource(Res.string.practice_month_line, Formats.minutesInWords(rolledMonthMs), days),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
    }
}

/**
 * An arrow of the month: a button of 44 on the card colour at a corner of 14 — the touch of 48 comes from Compose, which lets a
 * smaller target take a touch that lands just beside it. The one that cannot go on is the tertiary text at half its strength.
 * [container] — what it is filled with: the card colour on «Занятия», one step lighter in a sheet (the months of the form of an event).
 */
@Composable
internal fun ArrowButton(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(ArrowSize)
            .alpha(if (enabled) 1f else DISABLED_ARROW_ALPHA)
            // Surface(onClick) sets no role, the IconButton the arrows were did: a reader hears «кнопка» again
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = AppShapes.Control,
        color = container,
        contentColor = if (enabled) colors.onSurface else ViolinTheme.textTertiary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(icon = if (forward) AppIcons.ChevronRight else AppIcons.ChevronLeft, contentDescription = null)
        }
    }
}

/** A month to come (spec 3.36.9): its name and, with events, «17 событий» under it — the line of the time of R2 in its look. */
@Composable
private fun FutureMonthTitle(title: String, events: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val count = if (events > 0) {
        stringResource(Formats.plural(events, Res.string.event_count_one, Res.string.event_count_few, Res.string.event_count_many), events)
    } else {
        null
    }
    val description = count?.let { stringResource(Res.string.practice_pair_description, title, it) } ?: title
    Column(
        modifier = modifier.clearAndSetSemantics {
            heading()
            liveRegion = LiveRegionMode.Polite
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (count != null) {
            Text(
                text = count,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = EventsDimens.MonthLineText, lineHeight = EventsDimens.MonthLineHeight, fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
    }
}

/** The names of the weekdays over a grid, Monday first, in the third level of text. */
@Composable
internal fun Weekdays(modifier: Modifier) {
    val names = stringArrayResource(Res.array.practice_weekdays)
    Row(modifier = modifier.fillMaxWidth().padding(bottom = WeekdaysBottom)) {
        names.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = ViolinTheme.textTertiary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
