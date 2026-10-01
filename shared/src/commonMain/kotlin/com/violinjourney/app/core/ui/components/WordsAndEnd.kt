package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A text of the words of a [WordsAndEnd] that keeps its words whole: [text] in [style] — the style its `Text` is drawn in — on
 * [maxLines] lines at most, as its `Text` has them; [leastSp] — the least size its `Text` steps down to where its words do not stand
 * whole at the size of [style] (its `autoSize` is [WholeWordsFit] down to it: the name of a home in «Дома»), else the size of the
 * style itself.
 */
@Immutable
data class WholeText(val text: String, val style: TextStyle, val maxLines: Int = Int.MAX_VALUE, val leastSp: Float = style.fontSize.value)

/**
 * Whether every one of [texts] laid out [room] px wide — as its `Text` then lays it out — keeps its words whole ([WholeWords]): at
 * some size from that of its style down to its least, 0.5 sp at a time, every line but the last ends at a space and nothing is cut or
 * dropped by its lines. No room — no.
 */
internal fun textsStandWhole(measurer: TextMeasurer, texts: List<WholeText>, room: Int, density: Density, layoutDirection: LayoutDirection): Boolean =
    room > 0 && texts.all { whole ->
        val at = { sizeSp: Float ->
            measurer.measure(
                text = whole.text,
                style = whole.style.copy(fontSize = sizeSp.sp),
                overflow = TextOverflow.Ellipsis,
                maxLines = whole.maxLines,
                constraints = Constraints(maxWidth = room),
                layoutDirection = layoutDirection,
                density = density,
            )
        }
        WholeWords.of(at(WholeWords.largest(whole.style.fontSize.value, whole.leastSp, at)))
    }

/** Between the words and what stands beside them, and over what went under them (spec 5.29 R7). */
object WordsAndEndDefaults {
    val Gap = 12.dp
    val UnderGap = 8.dp
}

/**
 * Words at the start of a row and what stands at its end — a chip, a price, a pill, a button (spec 3.36.7, 5.29 R7: «слово не рвётся»;
 * the rows of «Дома», the extras of a stop, the gift of the home): side by side, [gap] apart and both in the middle of the height,
 * while every text of [whole] laid out in the room the end leaves it keeps its words whole ([textsStandWhole]); else the end stands
 * under the words, at the end of the row, [underGap] under them, and the words take the whole width — a word is never broken, nor a
 * name cut with an ellipsis, to make room for what stands beside it (on 360 «Маленький деревянный домик» beside «хватает · 3 000 ›»
 * would have 64 dp; lying in 603 dp «Tageszeit» beside «✓ geöffnet» 69). The rule of the card of the path ([WordsAndNumber]) for
 * words of more than one text and an end of any kind.
 *
 * [whole] names the texts of [words] whose words must stay whole, in the styles they are drawn in: they are measured in the room as
 * their `Text`s are then laid out, so what is decided is what is drawn. [words] gets the whole room it stands in — a bar under them
 * fills it — and [end] its own width.
 */
@Composable
fun WordsAndEnd(
    whole: List<WholeText>,
    modifier: Modifier = Modifier,
    gap: Dp = WordsAndEndDefaults.Gap,
    underGap: Dp = WordsAndEndDefaults.UnderGap,
    words: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Layout(
        content = {
            Box(propagateMinConstraints = true) { words() }
            Box { end() }
        },
        modifier = modifier,
    ) { (wordsM, endM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val endPlaced = endM.measure(loose)
        val bounded = constraints.hasBoundedWidth
        val room = if (bounded) constraints.maxWidth - endPlaced.width - gap.roundToPx() else Constraints.Infinity
        val beside = !bounded || textsStandWhole(measurer, whole, room, this, layoutDirection)
        if (beside) {
            val wordsPlaced = wordsM.measure(if (bounded) loose.copy(minWidth = room, maxWidth = room) else loose)
            val width = if (bounded) constraints.maxWidth else wordsPlaced.width + gap.roundToPx() + endPlaced.width
            val height = maxOf(wordsPlaced.height, endPlaced.height, constraints.minHeight)
            layout(width, height) {
                wordsPlaced.place(0, (height - wordsPlaced.height) / 2)
                endPlaced.place(width - endPlaced.width, (height - endPlaced.height) / 2)
            }
        } else {
            // only in a bounded width: an unbounded one always has room beside
            val width = constraints.maxWidth
            val wordsPlaced = wordsM.measure(loose.copy(minWidth = width, maxWidth = width))
            val under = underGap.roundToPx()
            val height = maxOf(wordsPlaced.height + under + endPlaced.height, constraints.minHeight)
            layout(width, height) {
                wordsPlaced.place(0, 0)
                endPlaced.place(width - endPlaced.width, wordsPlaced.height + under)
            }
        }
    }
}
