package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_day_description
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_month_back
import com.violinjourney.app.shared.resources.practice_month_days_few
import com.violinjourney.app.shared.resources.practice_month_days_many
import com.violinjourney.app.shared.resources.practice_month_days_one
import com.violinjourney.app.shared.resources.practice_month_description
import com.violinjourney.app.shared.resources.practice_month_forward
import com.violinjourney.app.shared.resources.practice_month_line
import com.violinjourney.app.shared.resources.practice_weekdays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * The geometry of the calendar in the two layouts (spec 5.29 R2; events-kinds.html, «Геометрия клетки»). A cell is as wide as the
 * grid over seven and [rowHeight] high whatever the day holds — the bottom of it, from 38 (landscape 36), waits for the marks of
 * the events of R9, so the grid does not jump when they come.
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
    /** The calendar is never wider than this and stands at the left edge — R9 puts the legend of the kinds beside it; null — the whole width. */
    val maxWidth: Dp?,
) {
    /**
     * The circle in a column [column] wide: [circle], or less where the ring «выбран» — a gap and a ring outside the circle, Ø 48 in
     * all — would reach the circle of the next day (a column under 44: a screen under 340, «Размер экрана: максимальный» gives 320).
     * The ring may take the empty edge of the next cell, as on 360 (column 47), but never its circle: the next cell draws later and
     * would cover it, and its gap would cut the one before.
     */
    fun circleIn(column: Dp): Dp = minOf(circle, column - SelectedRingGap - SelectedRingWidth)

    companion object {
        val Portrait = CalendarMetrics(rowHeight = 52.dp, rowGap = 4.dp, circle = 40.dp, circleTop = 4.dp, headerTop = 16.dp, headerBottom = 8.dp, maxWidth = null)
        val Landscape = CalendarMetrics(rowHeight = 50.dp, rowGap = 2.dp, circle = 38.dp, circleTop = 4.dp, headerTop = 4.dp, headerBottom = 6.dp, maxWidth = 350.dp)
    }
}

private val ArrowSize = 44.dp
private val WeekdaysBottom = 4.dp
private val ToneOneContour = 1.dp
private val TodayRingWidth = 2.dp
private val SelectedRingWidth = 2.dp
private val SelectedRingGap = 2.dp
private const val DISABLED_ARROW_ALPHA = 0.5f
private const val DAYS_PER_WEEK = 7
private const val TABULAR_FIGURES = "tnum"
private const val MONTH_SLIDE_MS = 220
private const val FILL_MS = 300
private const val RING_MS = 100
private const val RING_START_SCALE = 0.8f
private const val ACCENT_TONE = 4
private val MonthSlide = 40.dp

/**
 * The month of «Занятия» (spec 3.36.2): the header — the month («Сентябрь»; with its year only in another year), under it the time of
 * the month and its days with practice, the arrows of 44 with a touch of 48 — and the grid, Monday first. A day with practice is a
 * circle in the tone of its time (5.6), the first tone with a thin contour of the second inside it; today is a ring inside the circle
 * and a bold number; the day whose sheet is open, a second ring outside through a gap; a day to come is not selectable. The whole
 * cell is its touch. No legend: the exact time is in the sheet of the day.
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
) {
    Column(
        modifier = modifier
            .then(if (metrics.maxWidth != null) Modifier.widthIn(max = metrics.maxWidth) else Modifier)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = metrics.headerTop, bottom = metrics.headerBottom),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(forward = false, enabled = true, onClick = onMonthBack, description = stringResource(Res.string.practice_month_back))
            MonthTitle(month, currentYear, monthMs, rolledMonthMs, monthDays, Modifier.weight(1f))
            ArrowButton(forward = true, enabled = canGoForward, onClick = onMonthForward, description = stringResource(Res.string.practice_month_forward))
        }
        Weekdays()
        // Month change slides in the direction of the arrow (handoff `anims`, «Смена месяца»).
        val slidePx = with(LocalDensity.current) { MonthSlide.roundToPx() }
        AnimatedContent(
            targetState = month to cells,
            transitionSpec = {
                val slide = if (targetState.first > initialState.first) slidePx else -slidePx
                (slideInHorizontally(tween(MONTH_SLIDE_MS)) { slide } + fadeIn(tween(MONTH_SLIDE_MS)))
                    .togetherWith(slideOutHorizontally(tween(MONTH_SLIDE_MS)) { -slide } + fadeOut(tween(MONTH_SLIDE_MS)))
            },
            contentKey = { it.first },
            label = "month",
        ) { (_, monthCells) ->
            Grid(monthCells, metrics, onDaySelected)
        }
    }
}

/**
 * «Сентябрь» and «17 ч 27 мин · 24 дня» under it — a month without practice says only its name. One heading to a reader, «Сентябрь,
 * 17 ч 27 мин, 24 дня», and a polite live region: after an arrow the reader hears the month it came to.
 */
@Composable
private fun MonthTitle(month: YearMonth, currentYear: Int, monthMs: Long, rolledMonthMs: Long, monthDays: Int, modifier: Modifier) {
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
 */
@Composable
private fun ArrowButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit, description: String) {
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
        color = colors.surfaceContainer,
        contentColor = if (enabled) colors.onSurface else ViolinTheme.textTertiary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(icon = if (forward) AppIcons.ChevronRight else AppIcons.ChevronLeft, contentDescription = null)
        }
    }
}

@Composable
private fun Weekdays() {
    val names = stringArrayResource(Res.array.practice_weekdays)
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = WeekdaysBottom)) {
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

@Composable
private fun Grid(cells: List<CalendarCell?>, metrics: CalendarMetrics, onDaySelected: (LocalDate) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = metrics.rowGap)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
    ) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f).height(metrics.rowHeight))
                    } else {
                        DayCell(cell, metrics, Modifier.weight(1f), onClick = { onDaySelected(cell.date) })
                    }
                }
            }
        }
    }
}

/**
 * One day: the whole cell is the touch, the circle at its top draws everything in one pass — the fill of the tone, the contour of the
 * first tone, the ring of today inside the circle, the gap and the ring of the selected day outside it — and the number over it. The
 * marks of the events of R9 go on top, in the empty bottom of the cell. The ripple stays in the circle.
 */
@Composable
private fun DayCell(cell: CalendarCell, metrics: CalendarMetrics, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val practice = ViolinTheme.practiceColors
    val filled = cell.fillLevel > 0
    val accentTone = cell.fillLevel == ACCENT_TONE
    val fill by animateColorAsState(
        targetValue = if (filled) practice.fillFor(cell.fillLevel) else Color.Transparent,
        animationSpec = tween(FILL_MS),
        label = "fill",
    )
    val ringScale by animateFloatAsState(
        targetValue = if (cell.isSelected) 1f else RING_START_SCALE,
        animationSpec = tween(RING_MS),
        label = "ring",
    )
    // the contour of the first tone is the second tone: a day under 20 minutes is told from an empty one (open question E)
    val contour = if (cell.fillLevel == 1) practice.fillFor(2) else null
    val todayRing = if (cell.isToday) (if (accentTone) colors.onPrimary else colors.primary) else null
    val selected = cell.isSelected
    val ground = colors.surface
    val selectedRing = colors.onSurface
    val numberColor = when {
        cell.isFuture -> ViolinTheme.textTertiary
        filled -> practice.onFillFor(cell.fillLevel)
        else -> colors.onSurface
    }
    val bold = !cell.isFuture && (cell.isToday || cell.isSelected || accentTone)
    val timeText = if (cell.totalMs > 0) Formats.minutesInWords(cell.totalMs) else stringResource(Res.string.practice_day_none)
    val description = stringResource(Res.string.practice_day_description, Formats.dayWithWeekday(cell.date), timeText)
    val interaction = remember { MutableInteractionSource() }
    val circlePresses = remember(interaction) { ShiftedInteractionSource(interaction) }

    Column(
        modifier = modifier
            .height(metrics.rowHeight)
            .selectable(
                selected = cell.isSelected,
                interactionSource = interaction,
                indication = null,
                enabled = !cell.isFuture,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(metrics.circleTop))
        Box(
            modifier = Modifier
                .onPlaced { circlePresses.shift = it.positionInParent() }
                .dayCircle(metrics)
                .drawBehind {
                    val radius = size.minDimension / 2
                    drawCircle(fill, radius)
                    contour?.let { drawCircle(it, radius - ToneOneContour.toPx() / 2, style = Stroke(ToneOneContour.toPx())) }
                    todayRing?.let { drawCircle(it, radius - TodayRingWidth.toPx() / 2, style = Stroke(TodayRingWidth.toPx())) }
                    if (selected) {
                        val gap = SelectedRingGap.toPx() * ringScale
                        val width = SelectedRingWidth.toPx()
                        drawCircle(ground, radius + gap / 2, style = Stroke(gap))
                        drawCircle(selectedRing, radius + gap + width / 2, style = Stroke(width))
                    }
                }
                .clip(CircleShape)
                .indication(circlePresses, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.day.toString(),
                color = numberColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
    }
}

/** The circle of the day, as wide as [CalendarMetrics.circleIn] lets it be in the column it stands in. */
private fun Modifier.dayCircle(metrics: CalendarMetrics): Modifier = layout { measurable, constraints ->
    val side = metrics.circleIn(constraints.maxWidth.toDp()).roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}
