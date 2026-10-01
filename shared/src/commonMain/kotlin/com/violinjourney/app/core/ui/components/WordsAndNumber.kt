package com.violinjourney.app.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Whether [text] in [style] stands beside a number in the [room] px the number leaves it ([WordsAndNumber]): laid out in that room, as
 * the `Text` beside the number then is, it goes on to a next line only at a space ([WholeWords]). Not the width of its widest word: on
 * iOS «до Санкт-Петербурга» breaks after its hyphen in a room where «Санкт-Петербурга» alone still fits.
 */
internal fun wordsStandBeside(measurer: TextMeasurer, text: String, style: TextStyle, room: Int, density: Density, layoutDirection: LayoutDirection): Boolean =
    room > 0 && WholeWords.at(measurer, text, style, room, density, layoutDirection)

/** Between the words and the number beside them, and above the number that went under them (spec 5.29 R7). */
object WordsAndNumberDefaults {
    val Gap = 12.dp
    val UnderGap = 4.dp
}

/**
 * [text] at the start of a row and a [number] at its end (spec 3.36.7, 5.29 R7: the card of the path — «до Праги · поездом · 4 часа»
 * and «472 / 1 600» — and «Мировое турне пройдено» with the purse): side by side while the words, laid out in the room beside the
 * number, go on to a next line only at a space ([wordsStandBeside]) — the number on the line of their first baseline; else the number
 * goes under the words, at the end ([WordsAndNumberDefaults.UnderGap] under them) — a word is never broken to make room for it: on
 * 360 at the font 1.3 «Санкт-Петербурга» takes 168 dp; and on iOS «до Санкт-Петербурга» would break after its hyphen in a room
 * where the word alone still fits. The words are measured in that room as the `Text` beside the number is then laid out, so what is
 * decided is what is drawn. [gap] between the words and a number beside them. At least as high as it is asked, the words in the
 * middle of that height then.
 */
@Composable
fun WordsAndNumber(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = WordsAndNumberDefaults.Gap,
    number: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Layout(content = { Text(text, color = color, style = style); number() }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val numberPlaced = measurables[1].measure(loose)
        val between = gap.roundToPx()
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth - numberPlaced.width - between else Constraints.Infinity
        val beside = !constraints.hasBoundedWidth || wordsStandBeside(measurer, text, style, room, this, layoutDirection)
        if (beside) {
            val wordsPlaced = measurables[0].measure(if (constraints.hasBoundedWidth) loose.copy(maxWidth = room) else loose)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else wordsPlaced.width + between + numberPlaced.width
            val height = maxOf(wordsPlaced.height, numberPlaced.height, constraints.minHeight)
            val wordsTop = (height - wordsPlaced.height) / 2
            val wordsLine = wordsPlaced[FirstBaseline]
            val numberLine = numberPlaced[FirstBaseline]
            val numberTop = if (wordsLine != AlignmentLine.Unspecified && numberLine != AlignmentLine.Unspecified) {
                (wordsTop + wordsLine - numberLine).coerceIn(0, height - numberPlaced.height)
            } else {
                (height - numberPlaced.height) / 2
            }
            layout(width, height) {
                wordsPlaced.place(0, wordsTop)
                numberPlaced.place(width - numberPlaced.width, numberTop)
            }
        } else {
            // only in a bounded width: an unbounded one always has room beside
            val wordsPlaced = measurables[0].measure(loose)
            val under = WordsAndNumberDefaults.UnderGap.roundToPx()
            val width = constraints.maxWidth
            val height = maxOf(wordsPlaced.height + under + numberPlaced.height, constraints.minHeight)
            layout(width, height) {
                wordsPlaced.place(0, 0)
                numberPlaced.place(width - numberPlaced.width, wordsPlaced.height + under)
            }
        }
    }
}
