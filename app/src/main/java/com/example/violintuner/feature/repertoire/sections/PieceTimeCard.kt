package com.example.violintuner.feature.repertoire.sections

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.motion.LocalReduceMotion

/**
 * «Время по элементам» (spec 3.28, handoff 30h): where the time goes — a bar an element, its length by the time of
 * the period, today's part lighter at its end. The violet of time, as the calendar of «Занятия» has it; not the
 * colours of the zones and not the three tones of the learnt shares next to it. [visibleRows] stand folded
 * (three upright, five in the landscape column), «Все N» shows the rest in place. The bars grow once when the card
 * appears; rows opened later do not grow — two motions at once would be one too many.
 */
@Composable
fun PieceTimeCardView(card: PieceTimeCard, visibleRows: Int, onIntent: (SectionsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            .animateContentSize(tween(EXPAND_MS)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.piece_time_title),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = daysInWords(card.days),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        when (card.rows.size) {
            0 -> Text(
                text = stringResource(R.string.piece_time_empty),
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            )
            // one element with time: no bar — it would be a record with nobody to beat (handoff 30h5)
            1 -> SingleRow(card.rows.single(), card.days, onIntent)
            else -> Bars(card, visibleRows, onIntent)
        }
    }
}

@Composable
private fun SingleRow(row: PieceTimeRow, days: Int, onIntent: (SectionsIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = rowDescription(row, days)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onIntent(SectionsIntent.TimePieceClicked(row.pieceId)) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = row.title,
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = Formats.minutesInWords(row.totalMs),
                modifier = Modifier.padding(start = 12.dp),
                color = colors.primary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        if (row.todayMs > 0) {
            Text(
                text = stringResource(R.string.piece_time_today, Formats.minutesInWords(row.todayMs)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            )
        }
    }
}

@Composable
private fun Bars(card: PieceTimeCard, visibleRows: Int, onIntent: (SectionsIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val folded = card.rows.size > visibleRows
    val shown = if (card.expanded || !folded) card.rows else card.rows.take(visibleRows)
    val longest = card.rows.maxOf { it.totalMs }.coerceAtLeast(1)
    // one clock for the growth of all rows, each starting a step later (handoff 30 `anims`: 420 ms, 40 ms apart)
    val reduceMotion = LocalReduceMotion.current
    val grown = minOf(visibleRows, card.rows.size)
    val growTotalMs = GROW_MS + GROW_STEP_MS * (grown - 1).coerceAtLeast(0)
    val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(growTotalMs, easing = { it })) }
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        shown.forEachIndexed { index, row ->
            val description = rowDescription(row, card.days)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onIntent(SectionsIntent.TimePieceClicked(row.pieceId)) }
                    .semantics(mergeDescendants = true) { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = row.title,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = Formats.minutesInWords(row.totalMs),
                        modifier = Modifier.padding(start = 10.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                val track = colors.surfaceContainerHigh
                val bar = colors.primaryContainer
                val today = colors.primary
                val share = row.totalMs.toFloat() / longest
                val todayShare = row.todayMs.toFloat() / row.totalMs.coerceAtLeast(1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(BarHeight)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(track, cornerRadius = radius)
                            // the rows beyond the folded ones stand grown: they come with «Все N», not with the card
                            val started = if (index >= grown) 1f else ((grow.value * growTotalMs - index * GROW_STEP_MS) / GROW_MS).coerceIn(0f, 1f)
                            val width = size.width * share * EaseOut.transform(started)
                            if (width <= 0f) return@drawBehind
                            drawRoundRect(bar, size = Size(width, size.height), cornerRadius = radius)
                            if (todayShare > 0f) {
                                val todayWidth = (width * todayShare).coerceAtLeast(size.height)
                                drawRoundRect(today, topLeft = Offset(width - todayWidth.coerceAtMost(width), 0f), size = Size(todayWidth.coerceAtMost(width), size.height), cornerRadius = radius)
                            }
                        },
                )
            }
        }
        if (folded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MoreHeight)
                    .clickable(role = Role.Button) { onIntent(SectionsIntent.TimeToggled) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (card.expanded) stringResource(R.string.piece_time_less) else stringResource(R.string.piece_time_all, card.rows.size),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

@Composable
private fun daysInWords(days: Int): String =
    stringResource(Formats.plural(days, R.string.piece_time_days_one, R.string.piece_time_days_few, R.string.piece_time_days_many), days)

@Composable
private fun rowDescription(row: PieceTimeRow, days: Int): String {
    val total = Formats.minutesInWords(row.totalMs)
    return if (row.todayMs > 0) {
        stringResource(R.string.piece_time_row_today_description, row.title, total, daysInWords(days), Formats.minutesInWords(row.todayMs))
    } else {
        stringResource(R.string.piece_time_row_description, row.title, total, daysInWords(days))
    }
}

private val CardCorner = 16.dp
private val BarHeight = 8.dp
private val MoreHeight = 36.dp
private const val GROW_MS = 420
private const val GROW_STEP_MS = 40
private const val EXPAND_MS = 280
private const val TABULAR_FIGURES = "tnum"
