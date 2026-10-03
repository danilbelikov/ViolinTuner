package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.drawMiniSign
import com.violinjourney.app.core.ui.icons.drawMore
import com.violinjourney.app.core.ui.theme.EventsColors
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.eventKindWord
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_day_description
import com.violinjourney.app.shared.resources.practice_day_event_all_day
import com.violinjourney.app.shared.resources.practice_day_event_at
import com.violinjourney.app.shared.resources.practice_day_events_description
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_pair_description
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

internal val ToneOneContour = 1.dp
internal val TodayRingWidth = 2.dp
internal val SelectedRingWidth = 2.dp
internal val SelectedRingGap = 2.dp
private const val DAYS_PER_WEEK = 7
private const val TABULAR_FIGURES = "tnum"
private const val FILL_MS = 300
private const val RING_MS = 100
private const val RING_START_SCALE = 0.8f
private const val ACCENT_TONE = 4

/** A day that does not answer a touch — before the first event in «Повторять до» (5.29 R9) — at the strength of anything disabled. */
private const val DISABLED_ALPHA = 0.38f

/**
 * How TalkBack names a day of a grid (plan D46): [PRACTICE] — the calendar of «Занятия», «13 октября, вторник, 45 минут; урок в 17:00»;
 * [FORM] — the sheets of the form of an event, the whole weekday first and no time: «суббота, 24 октября; оркестр в 11:00».
 */
enum class DaySpeech { PRACTICE, FORM }

/**
 * The days of a month in whole weeks, Monday first (spec 3.36.2, 3.36.9): the grid of «Занятия», and of the sheets of the form of an
 * event. [ground] is what the grid lies on — the ground of the screen on «Занятия», the colour of the sheet in a sheet: the gap of the
 * ring «выбран» and the plate of the marks are painted with it. [timeFill] false — no fill of the time of the days (the sheet «Дата»
 * chooses a day, it does not tell how long it was played); [dimFuture] false — the numbers of the days to come are not dimmed; [speech]
 * — how a reader names a day.
 */
@Composable
fun MonthGrid(
    cells: List<CalendarCell?>,
    metrics: CalendarMetrics,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    ground: Color = MaterialTheme.colorScheme.surface,
    timeFill: Boolean = true,
    dimFuture: Boolean = true,
    speech: DaySpeech = DaySpeech.PRACTICE,
) {
    // The marks of every cell are of one size, chosen by the column of the grid (CalendarMetrics.marksOfGrid), not by the pixels each
    // cell gets: the width is taken when the grid is measured and read where the marks are drawn — the grid is not composed anew.
    var gridPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val gridMarks: () -> MarkSize? = remember(metrics, density) { { gridPx.takeIf { it > 0 }?.let { metrics.marksOfGrid(it, density) } } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { gridPx = it.width }
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
                        DayCell(cell, metrics, Modifier.weight(1f), ground, timeFill, dimFuture, gridMarks, speech = speech, onClick = { onDaySelected(cell.date) })
                    }
                }
            }
        }
    }
}

/**
 * One day (spec 3.36.2, 3.36.9; events-kinds.html, «Геометрия клетки»): the whole cell is the touch, the circle at its top draws in one
 * pass the fill of the tone, the contour of the first tone, the ring of today inside the circle, the gap and the ring of the selected
 * day outside it, and the number over it; then, on top of the cell, the marks of its events — a plate of [ground] over the bottom of
 * the circle from [CalendarMetrics.marksTop] to the bottom of the cell, the mini signs of the kinds on it in the order of the day and «+»
 * after three. The plate cuts the bottom of the rings, never the signs, so the marks read on any tone and on the selected day. The cell
 * lays out nothing new for them: a day without events is drawn as it was before them. The ripple stays in the circle.
 *
 * Bold (800): today, the fourth tone, and the selected day — one to come too (5.29 R2: «выбран» 800); the number of a day to come is
 * the tertiary text, its marks in full. TalkBack: the date, its time — none for a day to come — and its events, «13 октября, вторник,
 * 45 минут; урок в 17:00».
 *
 * [gridMarks] — the size of the marks of the grid the cell stands in, read where they are drawn ([MonthGrid]: one size for all its
 * cells); null, or none from it yet — the size of the cell's own width (a cell alone, as in the sheet «Вид»). [interactive] false — a
 * picture of a day, not a day to choose: the preview of the sheet «Вид» shows how a kind lies in a cell, and a reader hears nothing of
 * it — the words beside it name the kind.
 */
@Composable
fun DayCell(
    cell: CalendarCell,
    metrics: CalendarMetrics,
    modifier: Modifier = Modifier,
    ground: Color = MaterialTheme.colorScheme.surface,
    timeFill: Boolean = true,
    dimFuture: Boolean = true,
    gridMarks: (() -> MarkSize?)? = null,
    speech: DaySpeech = DaySpeech.PRACTICE,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val practice = ViolinTheme.practiceColors
    val filled = timeFill && cell.fillLevel > 0
    val accentTone = filled && cell.fillLevel == ACCENT_TONE
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
    val contour = if (filled && cell.fillLevel == 1) practice.fillFor(2) else null
    val todayRing = if (cell.isToday) (if (accentTone) colors.onPrimary else colors.primary) else null
    val selected = cell.isSelected
    val selectedRing = colors.onSurface
    val numberColor = when {
        cell.isFuture && dimFuture -> ViolinTheme.textTertiary
        filled -> practice.onFillFor(cell.fillLevel)
        else -> colors.onSurface
    }
    // the selected day is bold whatever it is, a day to come too; today and the fourth tone are bold of themselves
    val bold = cell.isSelected || (!cell.isFuture && (cell.isToday || accentTone))
    val description = dayDescription(cell, speech)
    val interaction = remember { MutableInteractionSource() }
    val circlePresses = remember(interaction) { ShiftedInteractionSource(interaction) }
    val events = ViolinTheme.eventsColors
    val more = colors.onSurfaceVariant

    Column(
        modifier = modifier
            .height(metrics.rowHeight)
            .then(if (cell.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .then(
                if (interactive) {
                    Modifier
                        .selectable(
                            selected = cell.isSelected,
                            interactionSource = interaction,
                            indication = null,
                            enabled = cell.enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .semantics { contentDescription = description }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            )
            .dayMarks(cell, metrics, ground, events, more, gridMarks),
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

/**
 * What TalkBack says of a day (spec 3.36.9): the date and its time — «13 октября, вторник, 45 минут»; a day to come has no time, only
 * its date — then its events by the word of their kind and their start, in the order of the day — «весь день» first (5.28): «;
 * выступление, весь день, урок в 17:00». In the sheets of the form ([DaySpeech.FORM]) the whole weekday comes first and no day has a
 * time: «суббота, 24 октября; оркестр в 11:00» (plan D46).
 */
@Composable
private fun dayDescription(cell: CalendarCell, speech: DaySpeech): String {
    val day = if (speech == DaySpeech.FORM) {
        Formats.weekdayFullDate(cell.date)
    } else if (cell.isFuture) {
        Formats.dayWithWeekday(cell.date)
    } else {
        val date = Formats.dayWithWeekday(cell.date)
        val time = if (cell.totalMs > 0) Formats.minutesInWords(cell.totalMs) else stringResource(Res.string.practice_day_none)
        stringResource(Res.string.practice_day_description, date, time)
    }
    if (cell.events.isEmpty()) return day
    var said: String? = null
    for (event in cell.events) {
        val word = eventKindWord(event.kind, event.ownName)
        val one = event.startMinutes?.let { stringResource(Res.string.practice_day_event_at, word, Formats.clockOf(it)) }
            ?: stringResource(Res.string.practice_day_event_all_day, word)
        said = said?.let { stringResource(Res.string.practice_pair_description, it, one) } ?: one
    }
    return stringResource(Res.string.practice_day_events_description, day, said.orEmpty())
}

/**
 * The marks of the events of [cell] over its bottom (spec 3.36.9, 5.29 R9 «Клетка»): nothing for a day without events — the cell is
 * drawn as before them. The sizes follow the column — of the grid ([gridMarks], the same in every cell of it), or of the cell itself
 * ([CalendarMetrics.marksIn]); the plate stands in the middle of the cell. The paths of the signs are parsed once for the whole app,
 * and a draw makes nothing of its own.
 */
private fun Modifier.dayMarks(
    cell: CalendarCell,
    metrics: CalendarMetrics,
    ground: Color,
    events: EventsColors,
    more: Color,
    gridMarks: (() -> MarkSize?)?,
): Modifier =
    if (cell.marks.isEmpty() && !cell.more) {
        this
    } else {
        drawWithContent {
            drawContent()
            val marks = gridMarks?.invoke() ?: metrics.marksIn(size.width.toDp())
            val sign = marks.sign.toPx()
            val gap = marks.gap.toPx()
            val moreWidth = marks.moreWidth.toPx()
            val moreHeight = marks.moreHeight.toPx()
            val plateHeight = EventsDimens.MarksPlateHeight.toPx()
            val plateWidth = marks.plateWidth(cell.marks.size, cell.more).toPx()
            val top = metrics.marksTop.toPx()
            val left = (size.width - plateWidth) / 2
            drawRoundRect(ground, Offset(left, top), Size(plateWidth, plateHeight), CornerRadius(EventsDimens.MarksPlateCorner.toPx()))
            var x = left + marks.side.toPx()
            val signTop = top + (plateHeight - sign) / 2
            for (index in cell.marks.indices) {
                val look = cell.marks[index]
                drawMiniSign(look.sign, events.of(look.color).color, Offset(x, signTop), sign)
                x += sign + gap
            }
            if (cell.more) drawMore(more, Offset(x, top + (plateHeight - moreHeight) / 2), moreWidth, moreHeight)
        }
    }

/** The circle of the day, as wide as [CalendarMetrics.circleIn] lets it be in the column it stands in. */
private fun Modifier.dayCircle(metrics: CalendarMetrics): Modifier = layout { measurable, constraints ->
    val side = metrics.circleIn(constraints.maxWidth.toDp()).roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}
