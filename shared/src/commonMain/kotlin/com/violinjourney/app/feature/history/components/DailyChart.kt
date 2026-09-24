package com.violinjourney.app.feature.history.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.session.DayCount
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_chart_bar
import com.violinjourney.app.shared.resources.history_chart_description
import com.violinjourney.app.shared.resources.history_count_few
import com.violinjourney.app.shared.resources.history_count_many
import com.violinjourney.app.shared.resources.history_count_one
import com.violinjourney.app.shared.resources.history_day_today
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource

/** Geometry of the chart of recordings per day (handoff 22c, `sizes`), in dp. */
internal object DailyChartMath {
    /** The number above the tallest bar, the gap under it, the bar itself, the baseline. */
    const val NUMBER_HEIGHT = 14f
    const val NUMBER_GAP = 4f
    const val BAR_MAX = 58f
    const val BASELINE = 1f
    const val PLOT_HEIGHT = NUMBER_HEIGHT + NUMBER_GAP + BAR_MAX + BASELINE
    const val LABELS_GAP = 6f
    const val LABELS_HEIGHT = 14f
    const val BAR_WIDTH = 17f
    const val EMPTY_MARK = 2f
    const val BAR_TOP_RADIUS = 3f
    const val BAR_BOTTOM_RADIUS = 1f

    /** An empty day is a mark on the baseline, not a bar: null. A day with recordings never sinks below the mark. */
    fun barHeight(count: Int, top: Int): Float? =
        if (count <= 0 || top <= 0) null else (BAR_MAX * count / top).coerceIn(EMPTY_MARK * 2, BAR_MAX)

    /** Bars share the width evenly; on a narrow card they get thinner, the gaps go with them. */
    fun barWidth(width: Float, count: Int): Float = if (count == 0) 0f else minOf(BAR_WIDTH, width / count * BAR_SHARE)

    fun barLeft(index: Int, count: Int, width: Float): Float {
        val bar = barWidth(width, count)
        return if (count <= 1) (width - bar) / 2 else index * (width - bar) / (count - 1)
    }

    /** The number stands over the busiest day only — over each of them when they are equal; the eye reads the rest by height. */
    fun numbered(days: List<DayCount>): Set<Int> {
        val max = days.maxOfOrNull { it.count } ?: 0
        return if (max == 0) emptySet() else days.indices.filter { days[it].count == max }.toSet()
    }

    /** Mondays are named under the axis; the last bar says «сегодня» instead (even on a Monday). */
    fun labelled(days: List<DayCount>): Set<Int> =
        days.indices.filter { it != days.lastIndex && days[it].date.dayOfWeek == DayOfWeek.MONDAY }.toSet()

    private const val BAR_SHARE = 0.68f
}

private const val GROW_MS = 300
private const val GROW_STEP_MS = 15
private const val NUMBER_FADE_FROM = 0.66f

/**
 * Recordings per day over the last two weeks (spec 3.21, handoff 22c): today on the right in the
 * accent, the rest quieter, an empty day a small mark on the baseline so that the axis does not
 * break. Not the colours of the zones: this is how much, not how well. It answers no touch.
 * The bars rise once when the chart appears; after that they simply follow the numbers.
 */
@Composable
fun DailyChart(days: List<DayCount>, top: Int, modifier: Modifier = Modifier, barWidthDp: Float = DailyChartMath.BAR_WIDTH) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val numberStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, fontFeatureSettings = "tnum")
    val todayWord = stringResource(Res.string.history_day_today)
    val barWords = days.filter { it.count > 0 }.map { day ->
        val count = stringResource(Formats.plural(day.count, Res.string.history_count_one, Res.string.history_count_few, Res.string.history_count_many), day.count)
        stringResource(Res.string.history_chart_bar, Formats.dayAndMonth(day.date), count)
    }
    val description = (listOf(stringResource(Res.string.history_chart_description)) + barWords).joinToString("; ")
    val reduceMotion = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val growTotalMs = GROW_MS + GROW_STEP_MS * (days.size - 1).coerceAtLeast(0)
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(growTotalMs, easing = { it })) }
    val numbered = remember(days) { DailyChartMath.numbered(days) }
    val labelled = remember(days) { DailyChartMath.labelled(days) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((DailyChartMath.PLOT_HEIGHT + DailyChartMath.LABELS_GAP + DailyChartMath.LABELS_HEIGHT).dp)
            .semantics { contentDescription = description },
    ) {
        val widthDp = size.width / density
        val baselineTop = (DailyChartMath.PLOT_HEIGHT - DailyChartMath.BASELINE).dp.toPx()
        drawRect(colors.surfaceContainerHigh, topLeft = Offset(0f, baselineTop), size = Size(size.width, DailyChartMath.BASELINE.dp.toPx()))
        val barWidth = minOf(barWidthDp, DailyChartMath.barWidth(widthDp, days.size)).dp.toPx()
        days.forEachIndexed { index, day ->
            val left = DailyChartMath.barLeft(index, days.size, widthDp).dp.toPx()
            // each bar starts a step after its left neighbour and takes the same time to rise
            val started = (grow.value * growTotalMs - index * GROW_STEP_MS) / GROW_MS
            val progress = FastOutSlowInEasing.transform(started.coerceIn(0f, 1f))
            val full = DailyChartMath.barHeight(day.count, top)
            if (full == null) {
                val mark = DailyChartMath.EMPTY_MARK.dp.toPx()
                drawRoundRect(colors.outlineVariant, Offset(left, baselineTop - mark), Size(barWidth, mark), CornerRadius(DailyChartMath.BAR_BOTTOM_RADIUS.dp.toPx()))
            } else {
                val height = full.dp.toPx() * progress
                val topRadius = CornerRadius(DailyChartMath.BAR_TOP_RADIUS.dp.toPx())
                val bottomRadius = CornerRadius(DailyChartMath.BAR_BOTTOM_RADIUS.dp.toPx())
                val bar = Path().apply {
                    addRoundRect(RoundRect(Rect(left, baselineTop - height, left + barWidth, baselineTop), topRadius, topRadius, bottomRadius, bottomRadius))
                }
                drawPath(bar, if (index == days.lastIndex) colors.primary else colors.primaryContainer)
                if (index in numbered) {
                    val number = textMeasurer.measure(day.count.toString(), numberStyle)
                    val alpha = ((progress - NUMBER_FADE_FROM) / (1f - NUMBER_FADE_FROM)).coerceIn(0f, 1f)
                    drawText(
                        number,
                        topLeft = Offset(left + (barWidth - number.size.width) / 2, baselineTop - full.dp.toPx() - DailyChartMath.NUMBER_GAP.dp.toPx() - number.size.height),
                        alpha = alpha,
                    )
                }
            }
            val word = when (index) {
                days.lastIndex -> todayWord
                in labelled -> Formats.dayAndShortMonth(day.date)
                else -> null
            }
            if (word != null) {
                val label = textMeasurer.measure(word, if (index == days.lastIndex) labelStyle.copy(color = colors.primary) else labelStyle, softWrap = false)
                val x = if (index == days.lastIndex) size.width - label.size.width else left
                drawText(label, topLeft = Offset(x.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)), (DailyChartMath.PLOT_HEIGHT + DailyChartMath.LABELS_GAP).dp.toPx()))
            }
        }
    }
}
