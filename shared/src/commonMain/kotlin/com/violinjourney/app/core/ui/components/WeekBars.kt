package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlin.math.sqrt

// The bars of a week (spec 5.29; components.html, «Прогресс»).
private val BarsGap = 6.dp
private val FrameWidth = 1.5.dp
private val DashOn = 4.dp
private val DashOff = 3.dp
private val HatchStripe = 5.dp
private const val HATCH_ALPHA = 0.7f

/** Where a day of the week stands against today. */
enum class WeekDayWhen { Past, Today, Future }

/** One day of [WeekBars]: its short name under the bar («пн»), its minutes, and where it stands against today. */
@Immutable
data class WeekBarDay(val label: String, val minutes: Int, val day: WeekDayWhen)

/** [Regular] — 48 high, 30 wide at most, a corner of 8; [Small] — 32, 26, 7: the left column of «Занятия» in landscape. */
enum class WeekBarsSize(val bar: Dp, val corner: Dp, val maxWidth: Dp, val labelGap: Dp) {
    Regular(bar = 48.dp, corner = 8.dp, maxWidth = 30.dp, labelGap = 6.dp),
    Small(bar = 32.dp, corner = 7.dp, maxWidth = 26.dp, labelGap = 4.dp),
}

/**
 * The bars of a week (spec 3.36.1, 5.29): seven columns 6 dp apart, a bar in each — as high as its day's minutes against the
 * longest day and the bottom of the scale ([scaleMinutes], the caller gives `PracticeConfig.fillLevelMinutes.last()`), at least
 * 4 dp for any time — and the short name of the day under it, in the third level of text; today's is in the accent and bold.
 * A past day without time is a dashed outline without figures; a day to come, an empty track; today has a frame of the accent,
 * and while a practice runs ([running]) its fill is hatched at 135°. Drawn — no clip, no layer.
 *
 * The component draws the minutes it is given: what goes into today while a practice runs is the caller's choice (R2). For TalkBack
 * the bars are one [description] («Неделя: 5 ч 45 мин; сегодня 45 мин»).
 */
@Composable
fun WeekBars(
    days: List<WeekBarDay>,
    running: Boolean,
    scaleMinutes: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: WeekBarsSize = WeekBarsSize.Regular,
) {
    val colors = MaterialTheme.colorScheme
    val look = BarColors(
        track = colors.surfaceContainerHigh,
        fill = colors.primaryContainer,
        today = colors.primary,
        missed = colors.outlineVariant,
    )
    val weekMax = days.maxOfOrNull { it.minutes } ?: 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(BarsGap),
    ) {
        days.forEach { day ->
            val today = day.day == WeekDayWhen.Today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val share = WeekBarsMath.share(day.minutes, weekMax, scaleMinutes)
                val fill = WeekBarsMath.fillDp(share, size.bar.value).dp
                Bar(WeekBarsMath.look(day, running), fill, size, look)
                Spacer(Modifier.height(size.labelGap))
                Text(
                    text = day.label,
                    color = if (today) colors.primary else ViolinTheme.textTertiary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (today) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Immutable
private class BarColors(val track: Color, val fill: Color, val today: Color, val missed: Color)

@Composable
private fun Bar(look: WeekBarsMath.BarLook, fill: Dp, size: WeekBarsSize, colors: BarColors) {
    Box(
        modifier = Modifier
            .widthIn(max = size.maxWidth)
            .fillMaxWidth()
            .height(size.bar)
            .drawWithCache {
                val corner = size.corner.toPx()
                val frame = FrameWidth.toPx()
                val bar = RoundRect(0f, 0f, this.size.width, this.size.height, CornerRadius(corner))
                val barPath = Path().apply { addRoundRect(bar) }
                // the fill rounds its own top and follows the curve of the bar at its bottom, as a child of a rounded box does
                val fillPx = fill.toPx()
                val fillPath = if (fillPx > 0f) {
                    val top = this.size.height - fillPx
                    val own = Path().apply {
                        addRoundRect(RoundRect(0f, top, this@drawWithCache.size.width, this@drawWithCache.size.height, CornerRadius(minOf(corner, fillPx / 2))))
                    }
                    Path().apply { op(own, barPath, PathOperation.Intersect) }
                } else {
                    null
                }
                val inner = Size(this.size.width - frame, this.size.height - frame)
                val innerCorner = CornerRadius(corner - frame / 2)
                val hatch = hatchBrush(colors.today, HatchStripe.toPx())
                val dashed = Stroke(width = frame, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())))
                onDrawBehind {
                    when (look) {
                        WeekBarsMath.BarLook.Missed ->
                            drawRoundRect(colors.missed, topLeft = Offset(frame / 2, frame / 2), size = inner, cornerRadius = innerCorner, style = dashed)
                        WeekBarsMath.BarLook.Future -> drawPath(barPath, colors.track)
                        WeekBarsMath.BarLook.Filled -> {
                            drawPath(barPath, colors.track)
                            fillPath?.let { drawPath(it, colors.fill) }
                        }
                        WeekBarsMath.BarLook.Today, WeekBarsMath.BarLook.TodayRunning -> {
                            drawPath(barPath, colors.track)
                            if (fillPath != null) {
                                if (look == WeekBarsMath.BarLook.TodayRunning) drawPath(fillPath, hatch) else drawPath(fillPath, colors.today)
                            }
                            drawRoundRect(colors.today, topLeft = Offset(frame / 2, frame / 2), size = inner, cornerRadius = innerCorner, style = Stroke(frame))
                        }
                    }
                }
            },
    )
}

/** Stripes at 135° — 5 dp of the accent, 5 dp of the accent at 0.7 — as a repeated gradient with hard steps. */
private fun hatchBrush(accent: Color, stripe: Float): Brush {
    // along the diagonal the period of two stripes is 2 × stripe; its end point lies that far down the 45° line
    val step = 2 * stripe / sqrt(2f)
    val soft = accent.copy(alpha = HATCH_ALPHA)
    return Brush.linearGradient(
        0f to accent,
        0.5f to accent,
        0.5f to soft,
        1f to soft,
        start = Offset.Zero,
        end = Offset(step, step),
        tileMode = TileMode.Repeated,
    )
}
