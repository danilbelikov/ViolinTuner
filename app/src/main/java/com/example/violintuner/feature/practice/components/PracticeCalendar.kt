package com.example.violintuner.feature.practice.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.practice.CalendarCell
import java.time.LocalDate
import java.time.YearMonth

/** Cell geometry of the two layouts (handoff `sizes`: «Клетка», «Landscape экрана»). */
@Immutable
data class CalendarMetrics(
    val rowHeight: Dp,
    val plateSize: Dp,
    val plateCorner: Dp,
    val numberSize: Int,
) {
    companion object {
        val Portrait = CalendarMetrics(rowHeight = 44.dp, plateSize = 40.dp, plateCorner = 12.dp, numberSize = 15)
        val Landscape = CalendarMetrics(rowHeight = 44.dp, plateSize = 38.dp, plateCorner = 11.dp, numberSize = 14)
    }
}

private val HeaderHeight = 40.dp
private val ArrowButtonSize = 40.dp
private val RowGap = 4.dp
private val DotSize = 4.dp
private val DotBottomInset = 6.dp
private val NumberLiftWithDot = 4.dp
private val TodayRingWidth = 1.5.dp
private val SelectedRingWidth = 2.dp
private val SelectedRingGap = 2.dp
private val LegendSquare = 12.dp
private val LegendCorner = 4.dp
private const val DISABLED_ARROW_ALPHA = 0.3f
private const val FUTURE_ALPHA = 0.4f
private const val DAYS_PER_WEEK = 7
private const val TABULAR_FIGURES = "tnum"
private const val MONTH_SLIDE_MS = 220
private const val FILL_MS = 300
private const val DOT_MS = 150
private const val RING_MS = 100
private const val RING_START_SCALE = 0.8f
private val MonthSlide = 40.dp

/** Month grid of the practice screen (spec 3.12, handoff 10a): Monday first, four fill tones, today and selection rings. */
@Composable
fun PracticeCalendar(
    month: YearMonth,
    cells: List<CalendarCell?>,
    canGoForward: Boolean,
    onMonthBack: () -> Unit,
    onMonthForward: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    metrics: CalendarMetrics = CalendarMetrics.Portrait,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(forward = false, enabled = true, onClick = onMonthBack, description = stringResource(R.string.practice_month_back))
            Text(
                text = Formats.monthAndYear(month),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            ArrowButton(forward = true, enabled = canGoForward, onClick = onMonthForward, description = stringResource(R.string.practice_month_forward))
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
        Legend(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ArrowButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit, description: String) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(ArrowButtonSize)) {
        AppIcon(
            icon = if (forward) AppIcons.ChevronRight else AppIcons.ChevronLeft,
            contentDescription = description,
            tint = color,
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ARROW_ALPHA),
        )
    }
}

@Composable
private fun Weekdays() {
    val names = stringArrayResource(R.array.practice_weekdays)
    Row(modifier = Modifier.fillMaxWidth()) {
        names.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun Grid(cells: List<CalendarCell?>, metrics: CalendarMetrics, onDaySelected: (LocalDate) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(metrics.rowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) DayCell(cell, metrics, onClick = { onDaySelected(cell.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, metrics: CalendarMetrics, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val practice = ViolinTheme.practiceColors
    val filled = cell.fillLevel > 0
    val fill by animateColorAsState(
        targetValue = if (filled) practice.fillFor(cell.fillLevel) else Color.Transparent,
        animationSpec = tween(FILL_MS),
        label = "fill",
    )
    val numberColor = if (filled) practice.onFillFor(cell.fillLevel) else colors.onSurface
    val ringScale by animateFloatAsState(
        targetValue = if (cell.isSelected) 1f else RING_START_SCALE,
        animationSpec = tween(RING_MS),
        label = "ring",
    )
    val selectedRing = colors.primary
    val todayRing = colors.onSurface
    val surface = colors.surface
    val emphasised = cell.isToday || cell.isSelected
    val timeText = if (cell.totalMs > 0) Formats.minutesInWords(cell.totalMs) else stringResource(R.string.practice_day_none)
    val description = stringResource(R.string.practice_day_description, Formats.dayWithWeekday(cell.date), timeText)

    Box(
        modifier = Modifier
            .size(metrics.plateSize)
            .alpha(if (cell.isFuture) FUTURE_ALPHA else 1f)
            .drawBehind {
                val corner = CornerRadius(metrics.plateCorner.toPx())
                drawRoundRect(fill, cornerRadius = corner)
                // "Today": a thin ring on the plate itself. "Selected": a ring outside it with a
                // gap of surface. Both at once collapse into the selected ring (handoff 10a).
                if (cell.isToday && !cell.isSelected) {
                    val inset = TodayRingWidth.toPx() / 2
                    drawRoundRect(
                        color = todayRing,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = corner,
                        style = Stroke(TodayRingWidth.toPx()),
                    )
                }
                if (cell.isSelected) {
                    val gap = SelectedRingGap.toPx() * ringScale
                    val width = SelectedRingWidth.toPx()
                    val outset = gap + width / 2
                    drawRoundRect(
                        color = surface,
                        topLeft = Offset(-gap, -gap),
                        size = Size(size.width + 2 * gap, size.height + 2 * gap),
                        cornerRadius = CornerRadius(corner.x + gap),
                        style = Stroke(gap),
                    )
                    drawRoundRect(
                        color = selectedRing,
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + 2 * outset, size.height + 2 * outset),
                        cornerRadius = CornerRadius(corner.x + outset),
                        style = Stroke(width),
                    )
                }
            }
            .clip(RoundedCornerShape(metrics.plateCorner))
            .selectable(selected = cell.isSelected, enabled = !cell.isFuture, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            modifier = Modifier.offset(y = if (filled) -NumberLiftWithDot else 0.dp),
            color = numberColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = metrics.numberSize.sp,
                fontWeight = if (emphasised) FontWeight.ExtraBold else FontWeight.Medium,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        // Shape doubles the tone: not everyone tells four purples apart (spec 3.12).
        AnimatedVisibility(
            visible = filled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DotBottomInset),
            enter = fadeIn(tween(DOT_MS)),
            exit = fadeOut(tween(DOT_MS)),
        ) {
            Box(
                Modifier
                    .size(DotSize)
                    .background(numberColor, RoundedCornerShape(DotSize / 2)),
            )
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val practice = ViolinTheme.practiceColors
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.practice_legend_less), color = variant, style = style)
        practice.fills.forEach { color ->
            Box(
                Modifier
                    .size(LegendSquare)
                    .background(color, RoundedCornerShape(LegendCorner)),
            )
        }
        Text(stringResource(R.string.practice_legend_more), color = variant, style = style)
    }
}
