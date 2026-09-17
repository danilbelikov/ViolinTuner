package com.example.violintuner.feature.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.session.WeekScore
import com.example.violintuner.core.ui.format.Formats

/** Geometry of the weekly chart (handoff 4c): a 120 dp plot over a row of date labels. */
internal object WeeklyChartMath {
    const val PLOT_HEIGHT = 120f
    const val BASELINE_Y = 110f
    const val LABELS_HEIGHT = 22f
    const val SIDE_INSET = 10f
    const val SCALE_MIN = 50
    const val SCALE_MAX = 100
    const val GUIDE_SCORE = 75

    /** dp per point of score: 50 sits on the baseline, 100 is 100 dp above it. */
    private const val DP_PER_POINT = 2f

    fun x(index: Int, count: Int, width: Float): Float =
        if (count <= 1) width / 2 else SIDE_INSET + index * (width - 2 * SIDE_INSET) / (count - 1)

    /** Scores below the scale sit on the baseline rather than under it. */
    fun y(score: Int): Float = BASELINE_Y - (score.coerceIn(SCALE_MIN, SCALE_MAX) - SCALE_MIN) * DP_PER_POINT

    /** "11 авг" for the first week and at a month change, the bare day otherwise. */
    fun label(weeks: List<WeekScore>, index: Int): String {
        val date = weeks[index].weekStart
        val newMonth = index == 0 || weeks[index - 1].weekStart.month != date.month
        return if (newMonth) Formats.dayAndShortMonth(date) else date.dayOfMonth.toString()
    }
}

/**
 * Average score per week (spec 3.11): a line through the weeks that have sessions, broken
 * where a week has none, a dot and the number on each, a dashed guide at 75.
 */
@Composable
fun WeeklyChart(weeks: List<WeekScore>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.labelSmall.fontFamily
    val valueStyle = TextStyle(color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = family)
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, fontFamily = family)
    val description = stringResource(R.string.history_chart_description)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((WeeklyChartMath.PLOT_HEIGHT + WeeklyChartMath.LABELS_HEIGHT).dp)
            .semantics { contentDescription = description },
    ) {
        val widthDp = size.width / density
        fun point(index: Int, score: Int) =
            Offset(WeeklyChartMath.x(index, weeks.size, widthDp).dp.toPx(), WeeklyChartMath.y(score).dp.toPx())

        val baseline = WeeklyChartMath.BASELINE_Y.dp.toPx()
        drawLine(colors.surfaceContainerHigh, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        val guide = WeeklyChartMath.y(WeeklyChartMath.GUIDE_SCORE).dp.toPx()
        drawLine(
            colors.surfaceContainerHigh, Offset(0f, guide), Offset(size.width, guide), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
        )

        weeks.forEachIndexed { index, week ->
            val score = week.averageScore
            val next = weeks.getOrNull(index + 1)?.averageScore
            if (score != null && next != null) {
                drawLine(colors.primary, point(index, score), point(index + 1, next), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        weeks.forEachIndexed { index, week ->
            val score = week.averageScore
            if (score != null) {
                val center = point(index, score)
                drawCircle(colors.surfaceContainer, radius = 5.dp.toPx(), center = center)
                drawCircle(colors.primary, radius = 5.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
                val value = textMeasurer.measure(score.toString(), valueStyle, softWrap = false, maxLines = 1)
                drawText(value, topLeft = Offset(center.x - value.size.width / 2f, center.y - 12.dp.toPx() - value.size.height))
            }
            val label = textMeasurer.measure(WeeklyChartMath.label(weeks, index), labelStyle, softWrap = false, maxLines = 1)
            val x = WeeklyChartMath.x(index, weeks.size, widthDp).dp.toPx() - label.size.width / 2f
            drawText(
                label,
                topLeft = Offset(x.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)), WeeklyChartMath.PLOT_HEIGHT.dp.toPx() + 4.dp.toPx()),
            )
        }
    }
}
