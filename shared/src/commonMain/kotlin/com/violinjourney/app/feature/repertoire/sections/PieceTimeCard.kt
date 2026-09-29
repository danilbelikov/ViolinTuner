package com.violinjourney.app.feature.repertoire.sections

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.piece_time_all
import com.violinjourney.app.shared.resources.piece_time_days_few
import com.violinjourney.app.shared.resources.piece_time_days_many
import com.violinjourney.app.shared.resources.piece_time_days_one
import com.violinjourney.app.shared.resources.piece_time_empty
import com.violinjourney.app.shared.resources.piece_time_less
import com.violinjourney.app.shared.resources.piece_time_row_description
import com.violinjourney.app.shared.resources.piece_time_row_today_description
import com.violinjourney.app.shared.resources.piece_time_title
import com.violinjourney.app.shared.resources.piece_time_today
import org.jetbrains.compose.resources.stringResource

/**
 * «Время по элементам» (spec 3.28, 3.36.4): the label of a section and «30 дней» at its right, then the card of the rows. Under the
 * sections upright ([top] 26), at the top of its own column in landscape.
 */
@Composable
fun PieceTimeSection(card: PieceTimeCard, visibleRows: Int, onIntent: (SectionsIntent) -> Unit, modifier: Modifier = Modifier, top: Dp = LabelTop) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = top, bottom = LabelBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(Res.string.piece_time_title), Modifier.weight(1f))
            Text(
                text = daysInWords(card.days),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        PieceTimeCardView(card, visibleRows, onIntent)
    }
}

/**
 * The card of «Время по элементам» (spec 3.28, 5.29 R4): where the time goes — a bar an element, its length by the time of the
 * period, today's part lighter at its end. The violet of time, as the calendar of «Занятия» has it; not the colours of the zones
 * and not the three tones of the learnt shares next to it. [visibleRows] stand folded (three upright, five in the landscape
 * column), «Все N» shows the rest in place. The bars grow once when the card appears; rows opened later do not grow — two motions
 * at once would be one too many. Once for the life of the card in its list, as the chart of «Записи» (5.15): scrolled out of sight
 * and back, or come back to from a section, it stands as it was ([PieceTimeGrowth]).
 */
@Composable
fun PieceTimeCardView(card: PieceTimeCard, visibleRows: Int, onIntent: (SectionsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(tween(EXPAND_MS)),
    ) {
        when (card.rows.size) {
            0 -> Text(
                text = stringResource(Res.string.piece_time_empty),
                modifier = Modifier.padding(vertical = RowPadding),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
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
            .heightIn(min = RowMinHeight)
            .clickable(role = Role.Button) { onIntent(SectionsIntent.TimePieceClicked(row.pieceId)) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = RowPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = row.title,
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = Formats.minutesInWords(row.totalMs),
                modifier = Modifier.padding(start = 12.dp),
                color = colors.primary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        if (row.todayMs > 0) {
            Text(
                text = stringResource(Res.string.piece_time_today, Formats.minutesInWords(row.todayMs)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
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
    val growing = minOf(visibleRows, card.rows.size)
    val growTotalMs = PieceTimeGrowth.totalMs(growing)
    // marked as grown when the growth begins, and kept by what the list saves of its item: a card that leaves in the middle of it,
    // or is scrolled out of the list and back (its item is composed anew then), comes back standing
    var grownBefore by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(PieceTimeGrowth.start(reduceMotion, grownBefore)) }
    LaunchedEffect(Unit) {
        grownBefore = true
        grow.animateTo(1f, tween(growTotalMs, easing = { it }))
    }
    Column {
        shown.forEachIndexed { index, row ->
            val description = rowDescription(row, card.days)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowMinHeight)
                    .clickable(role = Role.Button) { onIntent(SectionsIntent.TimePieceClicked(row.pieceId)) }
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(vertical = RowPadding),
                verticalArrangement = Arrangement.spacedBy(BarTop),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = row.title,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = Formats.minutesInWords(row.totalMs),
                        modifier = Modifier.padding(start = 12.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
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
                            val width = size.width * share * EaseOut.transform(PieceTimeGrowth.progress(grow.value, index, growing))
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
                    text = if (card.expanded) stringResource(Res.string.piece_time_less) else stringResource(Res.string.piece_time_all, card.rows.size),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

@Composable
private fun daysInWords(days: Int): String =
    stringResource(Formats.plural(days, Res.string.piece_time_days_one, Res.string.piece_time_days_few, Res.string.piece_time_days_many), days)

@Composable
private fun rowDescription(row: PieceTimeRow, days: Int): String {
    val total = Formats.minutesInWords(row.totalMs)
    return if (row.todayMs > 0) {
        stringResource(Res.string.piece_time_row_today_description, row.title, total, daysInWords(days), Formats.minutesInWords(row.todayMs))
    } else {
        stringResource(Res.string.piece_time_row_description, row.title, total, daysInWords(days))
    }
}

/** Rows before «Все N»: three upright and in one column, five in the landscape column (spec 3.28, 3.36.4). */
const val TIME_ROWS_UPRIGHT = 3
const val TIME_ROWS_LANDSCAPE = 5

/**
 * The growth of the bars of «Время по элементам» (spec 3.28, handoff 30 `anims`): one clock for all rows, 420 ms a row, each 40 ms
 * after the one above; once for the life of the card. Pure, with a test.
 */
internal object PieceTimeGrowth {
    const val GROW_MS = 420
    const val GROW_STEP_MS = 40

    /** The clock of [rows] growing rows, 0 to 1 over this many ms. */
    fun totalMs(rows: Int): Int = GROW_MS + GROW_STEP_MS * (rows - 1).coerceAtLeast(0)

    /** Where the clock starts: at its end — the bars standing — when they have grown before, or when motion is reduced. */
    fun start(reduceMotion: Boolean, grownBefore: Boolean): Float = if (reduceMotion || grownBefore) 1f else 0f

    /**
     * How far the bar of the row [index] has grown, 0 to 1 before its easing, when the clock of all is at [clock]. The rows past the
     * [growing] ones stand grown: they come with «Все N», not with the card.
     */
    fun progress(clock: Float, index: Int, growing: Int): Float {
        if (index >= growing) return 1f
        return ((clock * totalMs(growing) - index * GROW_STEP_MS) / GROW_MS).coerceIn(0f, 1f)
    }
}

private val LabelTop = 26.dp
private val LabelBottom = 10.dp
private val RowPadding = 8.dp
private val RowMinHeight = 48.dp
private val BarTop = 7.dp
private val BarHeight = 6.dp
private val MoreHeight = 48.dp
private const val EXPAND_MS = 280
private const val TABULAR_FIGURES = "tnum"
