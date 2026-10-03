package com.violinjourney.app.feature.events.form

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private const val TABULAR_FIGURES = "tnum"

/** The arithmetic of a wheel (spec 3.36.9, plan D26): a list of [count] values round and round. Pure, with a test. */
internal object WheelMath {
    /** The row the list starts at for [value]: in the middle of the turns, [value] in the middle of the five rows seen. */
    fun startRow(value: Int, count: Int): Int = (EventsDimens.WHEEL_TURNS / 2) * count + value - EventsDimens.WHEEL_ROWS / 2

    /** Whether more than half of the first row seen has gone up: [offset] pixels of a row [row] pixels high. */
    fun isPast(offset: Int, row: Float): Boolean = offset > row / 2

    /** The row in the middle of the five seen, when [top] is the first row seen — [past] — more than half of it gone up. */
    fun middleRow(top: Int, past: Boolean): Int = top + (if (past) 1 else 0) + EventsDimens.WHEEL_ROWS / 2

    /** The value of the row [middle] of a list of [count] values round and round. */
    fun valueOf(middle: Int, count: Int): Int = middle.mod(count)

    /**
     * The first row to bring [value] to the middle from the middle row [middle], the nearest way round: the wheel never spins through a
     * whole turn to come to a value a step away.
     */
    fun topFor(value: Int, middle: Int, count: Int): Int {
        val base = middle - middle.mod(count) + value
        val nearest = listOf(base - count, base, base + count).minBy { abs(it - middle) }
        return nearest - EventsDimens.WHEEL_ROWS / 2
    }
}

/**
 * A wheel of «Время» (spec 3.36.9, 5.29 R9; plan D26): the values of [labels] round and round on a `LazyColumn` that snaps a row to its
 * place, five rows seen, the one in the middle chosen — 28 sp / 800, its neighbours 20 sp / 600 in the second level of text and the outer
 * ones in the third. The value is told ([onSettle]) when the wheel stops, not on every frame; [selected] given from outside — a chip of
 * «Частое» — turns the wheel to it the nearest way. For TalkBack one node: its value in words ([said]) within its range — the value is
 * its state, said once («18 часов»: no name of its own, the words of the value carry the unit) — and the actions «Больше» and «Меньше»
 * ([up], [down]). Public for the tests of the app.
 */
@Composable
fun TimeWheel(
    labels: List<String>,
    selected: Int,
    said: (Int) -> String,
    up: String,
    down: String,
    onSettle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = labels.size
    val state = rememberLazyListState(initialFirstVisibleItemIndex = WheelMath.startRow(selected, count))
    val rowPx = with(LocalDensity.current) { EventsDimens.WheelRow.toPx() }
    // the middle changes when a row passes it, not on every frame of a fling
    val middle by remember(state, rowPx) {
        derivedStateOf { WheelMath.middleRow(state.firstVisibleItemIndex, WheelMath.isPast(state.firstVisibleItemScrollOffset, rowPx)) }
    }
    val current by rememberUpdatedState(selected)
    val settle by rememberUpdatedState(onSettle)
    LaunchedEffect(state) {
        // told when the finger and the fling have let it go — the first «not scrolling» is the start, not a stop
        snapshotFlow { state.isScrollInProgress }.drop(1).filter { !it }.collect {
            val value = WheelMath.valueOf(middle, count)
            if (value != current) settle(value)
        }
    }
    LaunchedEffect(selected, count) {
        if (!state.isScrollInProgress && WheelMath.valueOf(middle, count) != selected) state.scrollToItem(WheelMath.topFor(selected, middle, count))
    }
    val colors = MaterialTheme.colorScheme
    // a line as high as the figures: 28 sp at the font of 1.3 is 36 of the row of 44
    val chosenStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = EventsDimens.WheelChosen, lineHeight = EventsDimens.WheelChosen, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
    )
    val nearStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = EventsDimens.WheelNear, lineHeight = EventsDimens.WheelNear, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES,
    )
    val spoken = said(selected)
    Box(
        modifier = modifier
            .width(EventsDimens.WheelWidth)
            .height(EventsDimens.WheelRow * EventsDimens.WHEEL_ROWS)
            .clearAndSetSemantics {
                stateDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..(count - 1).toFloat(), steps = (count - 2).coerceAtLeast(0))
                customActions = listOf(
                    CustomAccessibilityAction(up) { settle((current + 1).mod(count)); true },
                    CustomAccessibilityAction(down) { settle((current - 1).mod(count)); true },
                )
            },
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            modifier = Modifier.fillMaxWidth().height(EventsDimens.WheelRow * EventsDimens.WHEEL_ROWS),
        ) {
            items(count * EventsDimens.WHEEL_TURNS) { row ->
                val away = abs(row - middle)
                Box(Modifier.fillMaxWidth().height(EventsDimens.WheelRow), contentAlignment = Alignment.Center) {
                    Text(
                        text = labels[row.mod(count)],
                        color = when {
                            away == 0 -> colors.onSurface
                            away == 1 -> colors.onSurfaceVariant
                            else -> ViolinTheme.textTertiary
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = if (away == 0) chosenStyle else nearStyle,
                    )
                }
            }
        }
    }
}
