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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.session.DayCount
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_chart_bar
import com.violinjourney.app.shared.resources.history_chart_description
import com.violinjourney.app.shared.resources.history_count_few
import com.violinjourney.app.shared.resources.history_count_many
import com.violinjourney.app.shared.resources.history_count_one
import com.violinjourney.app.shared.resources.history_day_today
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource

/** Geometry of the strip of recordings per day (spec 5.29 R5; records.html 1), in dp. */
internal object DailyChartMath {
    /** The field of the bars: the tallest day fills it. */
    const val FIELD = 28f

    /** Between two bars; the bars share what the gaps leave. */
    const val GAP = 4f

    /** A day with a recording is never lower than this — however high the top of the scale. */
    const val MIN_BAR = 3f

    /** A day without one: a mark this high on the base of the field. */
    const val EMPTY_MARK = 3f
    const val CORNER = 3f
    const val LABELS_GAP = 5f
    const val LABELS_HEIGHT = 14f

    /** A Monday's label keeps at least this much air before «сегодня» — or is not written. */
    const val LABEL_AIR = 4f

    /** An empty day is a mark on the base, not a bar: null. A day with recordings never sinks below [MIN_BAR]. */
    fun barHeight(count: Int, top: Int): Float? =
        if (count <= 0 || top <= 0) null else (FIELD * count / top).coerceIn(MIN_BAR, FIELD)

    /** Fourteen bars in a row with [GAP] between them fill the width of the card, whatever it is (was 17 / 16 dp). */
    fun barWidth(width: Float, count: Int): Float = if (count <= 0) 0f else ((width - GAP * (count - 1)) / count).coerceAtLeast(0f)

    fun barLeft(index: Int, count: Int, width: Float): Float = index * (barWidth(width, count) + GAP)

    /** Mondays are named under the axis; the last bar says «сегодня» instead (even on a Monday). */
    fun labelled(days: List<DayCount>): Set<Int> =
        days.indices.filter { it != days.lastIndex && days[it].date.dayOfWeek == DayOfWeek.MONDAY }.toSet()

    /** A Monday of yesterday or the day before would run into «сегодня» at the right end: its label is left out then. */
    fun labelFits(left: Float, labelWidth: Float, todayLeft: Float): Boolean = left + labelWidth + LABEL_AIR <= todayLeft
}

private const val GROW_MS = 300
private const val GROW_STEP_MS = 15
private const val TABULAR_FIGURES = "tnum"

/**
 * Recordings per day over the last two weeks as a strip (spec 3.36.5, 5.29 R5): fourteen bars in a field of 28 — today on the right in
 * the accent, a day with recordings in the second tone, an empty day a mark on the base; under the axis the Mondays and «сегодня».
 * No number over a bar: the total stands by the title of the card. Not the colours of the zones: this is how much, not how well. It
 * answers no touch and says nothing to TalkBack by itself — the card says it, with the total ([stripDescription]).
 * The bars rise once when the strip appears; after that they simply follow the numbers. Once for the life of the strip in its list
 * (spec 5.15): scrolled out of sight and back, or come back to from a recording, it stands as it was — the list keeps what an item
 * saves, and the growth is saved as begun.
 */
@Composable
fun DailyChart(days: List<DayCount>, top: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    // A measurer takes no font from the theme: without its family the labels would be drawn in the font of the system (as PianoRoll's).
    val labelStyle = TextStyle(
        color = ViolinTheme.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES,
        fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
    )
    val todayStyle = labelStyle.copy(color = colors.primary)
    val todayWord = stringResource(Res.string.history_day_today)
    val reduceMotion = LocalReduceMotion.current
    // marked as grown when the growth begins: a strip that leaves in the middle of it comes back standing
    var grown by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(if (reduceMotion || grown) 1f else 0f) }
    val growTotalMs = GROW_MS + GROW_STEP_MS * (days.size - 1).coerceAtLeast(0)
    LaunchedEffect(Unit) {
        grown = true
        grow.animateTo(1f, tween(growTotalMs, easing = { it }))
    }
    val labelled = remember(days) { DailyChartMath.labelled(days) }
    val bar = colors.primaryContainer
    val todayBar = colors.primary
    val mark = colors.surfaceContainerHigh
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((DailyChartMath.FIELD + DailyChartMath.LABELS_GAP + DailyChartMath.LABELS_HEIGHT).dp),
    ) {
        val widthDp = size.width / density
        val base = DailyChartMath.FIELD.dp.toPx()
        val corner = CornerRadius(DailyChartMath.CORNER.dp.toPx())
        val barWidth = DailyChartMath.barWidth(widthDp, days.size).dp.toPx()
        val labelsTop = (DailyChartMath.FIELD + DailyChartMath.LABELS_GAP).dp.toPx()
        val today = textMeasurer.measure(todayWord, todayStyle, softWrap = false, maxLines = 1)
        val todayLeft = (size.width - today.size.width).coerceAtLeast(0f)
        days.forEachIndexed { index, day ->
            val left = DailyChartMath.barLeft(index, days.size, widthDp).dp.toPx()
            val full = DailyChartMath.barHeight(day.count, top)
            if (full == null) {
                val height = DailyChartMath.EMPTY_MARK.dp.toPx()
                drawRoundRect(mark, Offset(left, base - height), Size(barWidth, height), corner)
            } else {
                // each bar starts a step after its left neighbour and takes the same time to rise
                val started = (grow.value * growTotalMs - index * GROW_STEP_MS) / GROW_MS
                val progress = FastOutSlowInEasing.transform(started.coerceIn(0f, 1f))
                val height = full.dp.toPx() * progress
                if (height > 0f) drawRoundRect(if (index == days.lastIndex) todayBar else bar, Offset(left, base - height), Size(barWidth, height), corner)
            }
            if (index in labelled) {
                val label = textMeasurer.measure(Formats.dayAndShortMonth(day.date), labelStyle, softWrap = false, maxLines = 1)
                if (DailyChartMath.labelFits(left / density, label.size.width / density, todayLeft / density)) {
                    drawText(label, topLeft = Offset(left, labelsTop))
                }
            }
        }
        drawText(today, topLeft = Offset(todayLeft, labelsTop))
    }
}

/**
 * What TalkBack hears of the strip (spec 3.36.5): the old words of the chart with the total — «Записи по дням за две недели, 12
 * записей» — and then each day that has recordings, «20 сентября, 1 запись».
 */
@Composable
internal fun stripDescription(days: List<DayCount>, total: Int): String {
    val totalWords = stringResource(recordsCountOf(total), total)
    val barWords = days.filter { it.count > 0 }.map { day ->
        stringResource(Res.string.history_chart_bar, Formats.dayAndMonth(day.date), stringResource(recordsCountOf(day.count), day.count))
    }
    return (listOf(stringResource(Res.string.history_chart_description) + ", " + totalWords) + barWords).joinToString("; ")
}

/** «1 запись», «3 записи», «12 записей». */
internal fun recordsCountOf(count: Int) = Formats.plural(count, Res.string.history_count_one, Res.string.history_count_few, Res.string.history_count_many)
