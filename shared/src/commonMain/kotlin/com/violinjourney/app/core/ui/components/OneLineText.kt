package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Words that stay on one line (spec 3.36.7, 5.29 R7: «не помещаются — мельче, до 15 и 12 sp, затем многоточие в названии; числа не
 * режутся»): the size of [style] where they fit, else 0.5 sp smaller at a time down to [minSp] ([ButtonFit.largest]); below it they
 * are cut with an ellipsis at the end — or, with [keep], around it: the part [keep] stands whole and the words on its two sides give
 * way, each cut with its own ellipsis ([ButtonLine]). The lines of [AppButton] with `oneLine`, of [ShortfallPlate] and of the bar of
 * the journey.
 *
 * Without [keep] it is a `Text` whose size is found in its own layout ([OneLineFit]), so its semantics are those of any text; with
 * [keep] the line is measured and drawn here, in one node: its three parts share one size, which only the whole line can decide. Both
 * answer intrinsic measurements — a row of buttons of one height (`IntrinsicSize.Min`) may hold them — and neither needs a second
 * frame to find its size. [color] — unspecified: the colour of [style], else the content colour.
 */
@Composable
internal fun OneLineText(
    text: String,
    style: TextStyle,
    minSp: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    keep: String? = null,
) {
    val maxSp = style.fontSize.value
    val leastSp = minOf(minSp, maxSp)
    val parts = remember(text, keep) { keep?.let { ButtonLine.split(text, it) } }
    if (parts == null) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            autoSize = remember(maxSp, leastSp) { OneLineFit(maxSp, leastSp) },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
        )
    } else {
        KeptLine(text, parts, style, leastSp, color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }, modifier)
    }
}

/**
 * The size of a line of one line in its own layout: the largest from the size of the style down, 0.5 sp at a time, at which the
 * words are not cut ([ButtonFit.largest]) — the same steps as the buttons of R4 measure in composition. The size found is laid out
 * last: the text keeps that layout as it is, and one laid out for another room would be taken for it.
 */
private data class OneLineFit(val maxSp: Float, val minSp: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val sizeSp = if (!constraints.hasBoundedWidth) {
            maxSp
        } else {
            ButtonFit.largest(maxSp, minSp) { sizeSp -> performLayout(constraints, text, sizeSp.sp).standsWhole() }
        }
        performLayout(constraints, text, sizeSp.sp)
        return sizeSp.sp
    }
}

/** One line, not cut: nothing dropped past it and no ellipsis in it. */
private fun TextLayoutResult.standsWhole(): Boolean = lineCount == 0 || (lineCount == 1 && !isLineEllipsized(0) && !multiParagraph.didExceedMaxLines)

/**
 * Words that may go on more lines but never break inside a word (the lesson of stages 108–110): the size of the style where every
 * line but the last ends at a space ([WholeWords]) — else 0.5 sp smaller at a time down to [minSp] ([WholeWords.largest]); below it
 * the word breaks, the limit. For a `Text`'s `autoSize`: the city of the journey, 26 sp in a column of 328 at a large font (spec
 * 3.36.7, 5.29 R7). As with [OneLineFit], the size found is laid out last.
 */
internal data class WholeWordsFit(val maxSp: Float, val minSp: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val sizeSp = if (!constraints.hasBoundedWidth) maxSp else WholeWords.largest(maxSp, minSp) { sizeSp -> performLayout(constraints, text, sizeSp.sp) }
        performLayout(constraints, text, sizeSp.sp)
        return sizeSp.sp
    }
}

/**
 * Words laid out whole (spec 5.29 R7: «слово не рвётся … «Санкт-Петербурга» — одно»): every line but the last ends at a space or a
 * new line, and nothing is cut. A word too wide for its line broken by the letter is not whole, nor a compound broken at its hyphen
 * — «до Санкт-» / «Петербурга», «vers Saint-» / «Pétersbourg»: iOS breaks after a hyphen where the line is short (SkParagraph, by
 * the rules of ICU), Android does not without hyphenation; the width of the widest word does not tell it, only the layout does. Over
 * a layout, so the rule is the same whoever lays the text out; tested on iOS, where text is laid out in the test itself.
 */
internal object WholeWords {
    fun of(layout: TextLayoutResult): Boolean {
        val text = layout.layoutInput.text.text
        if (layout.lineCount == 0) return true
        if (layout.isLineEllipsized(layout.lineCount - 1) || layout.multiParagraph.didExceedMaxLines) return false
        return (0 until layout.lineCount - 1).all { line ->
            val end = layout.getLineEnd(line)
            end > 0 && end <= text.length && text[end - 1].isWhitespace()
        }
    }

    /** [text] in [style] laid out [maxWidth] px wide — as a `Text` of that width lays it out — breaks only between words. */
    fun at(measurer: TextMeasurer, text: String, style: TextStyle, maxWidth: Int, density: Density, layoutDirection: LayoutDirection): Boolean =
        of(measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(0)), layoutDirection = layoutDirection, density = density))

    /**
     * The largest size from [maxSp] down, 0.5 sp at a time ([ButtonFit.largest]), at which the layout [at] a size keeps the words
     * whole; [minSp] where none does — the limit, not asked.
     */
    fun largest(maxSp: Float, minSp: Float, at: (sizeSp: Float) -> TextLayoutResult): Float = ButtonFit.largest(maxSp, minOf(minSp, maxSp)) { of(at(it)) }
}

/** A part of a kept line as it is drawn: its layout, [x] px from the start of the line. */
private class ShownPart(val layout: TextLayoutResult, val x: Float)

/** A kept line as it is drawn: its parts in order, the width and height they take and the baseline of the line. */
private class ShownLine(val parts: List<ShownPart>) {
    val width: Int = parts.lastOrNull()?.let { (it.x + it.layout.size.width).roundToInt() } ?: 0
    val height: Int = parts.maxOfOrNull { it.layout.size.height } ?: 0
    val baseline: Int = parts.firstOrNull()?.layout?.firstBaseline?.roundToInt() ?: 0
}

/**
 * A line that keeps [parts] `.kept` whole: measured here and drawn in one node. The whole line decides the size ([ButtonFit.largest]
 * over its full width); where it does not stand whole even at [minSp], the kept part is laid out at its own width and the two sides
 * share what is left ([ButtonLine.sides]), each cut with an ellipsis — a side that has less room than an ellipsis is left out, and the
 * kept part loses the space it held towards it. For a reader it is the whole line; its layouts are the parts drawn.
 */
@Composable
private fun KeptLine(whole: String, parts: ButtonLine.Parts, style: TextStyle, minSp: Float, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val shown = remember { mutableStateOf<ShownLine?>(null) }
    val policy = remember(whole, parts, style, minSp, measurer) { KeptLinePolicy(whole, parts, style, minSp, measurer, shown) }
    Layout(
        modifier = modifier
            .semantics {
                text = AnnotatedString(whole)
                getTextLayoutResult { layouts ->
                    val line = shown.value
                    line?.parts?.forEach { layouts += it.layout }
                    line != null
                }
            }
            .drawBehind { shown.value?.parts?.forEach { drawText(it.layout, color = color, topLeft = Offset(it.x, 0f)) } },
        measurePolicy = policy,
    )
}

private class KeptLinePolicy(
    private val whole: String,
    private val parts: ButtonLine.Parts,
    private val style: TextStyle,
    private val minSp: Float,
    private val measurer: TextMeasurer,
    private val shown: MutableState<ShownLine?>,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val line = lay(if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity)
        // read where it is drawn and by a reader: written only here, never while intrinsics are asked
        shown.value = line
        return layout(
            constraints.constrainWidth(line.width),
            constraints.constrainHeight(line.height),
            mapOf(FirstBaseline to line.baseline, LastBaseline to line.baseline),
        ) {}
    }

    /** The least it can stand in: the kept part alone at the least size. */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measure(parts.kept.trim(), minSp).size.width

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        lay(Constraints.Infinity).width

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = lay(width).height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = lay(width).height

    private fun IntrinsicMeasureScope.lay(room: Int): ShownLine {
        val maxSp = style.fontSize.value
        val sizeSp = if (room == Constraints.Infinity) maxSp else ButtonFit.largest(maxSp, minSp) { measure(whole, it).size.width <= room }
        val line = measure(whole, sizeSp)
        if (line.size.width <= room) return ShownLine(listOf(ShownPart(line, 0f)))
        val before = parts.before.takeIf { it.isNotEmpty() }?.let { measure(it, sizeSp) }
        val after = parts.after.takeIf { it.isNotEmpty() }?.let { measure(it, sizeSp) }
        val keptNatural = measure(parts.kept, sizeSp)
        val (beforeRoom, afterRoom) = ButtonLine.sides(
            before?.size?.width?.toFloat() ?: 0f,
            after?.size?.width?.toFloat() ?: 0f,
            (room - keptNatural.size.width).toFloat(),
        )
        val ellipsis = measure(ELLIPSIS, sizeSp).size.width
        val beforeShown = before?.let { cut(it, parts.before, beforeRoom, ellipsis, sizeSp) }
        val afterShown = after?.let { cut(it, parts.after, afterRoom, ellipsis, sizeSp) }
        // a side left out takes the space it stood off the kept part with it
        var kept = parts.kept
        if (beforeShown == null) kept = kept.trimStart()
        if (afterShown == null) kept = kept.trimEnd()
        val keptShown = if (kept == parts.kept) keptNatural else measure(kept, sizeSp)
        var x = 0f
        return ShownLine(
            listOfNotNull(beforeShown, keptShown, afterShown).map { part -> ShownPart(part, x).also { x += part.size.width } },
        )
    }

    /** [natural] where [room] holds it, else [text] cut with an ellipsis in [room]; null where not even the ellipsis would stand. */
    private fun IntrinsicMeasureScope.cut(natural: TextLayoutResult, text: String, room: Float, ellipsis: Int, sizeSp: Float): TextLayoutResult? = when {
        room >= natural.size.width -> natural
        room < ellipsis -> null
        else -> measure(text, sizeSp, maxWidth = floor(room).toInt(), cut = true)
    }

    private fun IntrinsicMeasureScope.measure(text: String, sizeSp: Float, maxWidth: Int = Constraints.Infinity, cut: Boolean = false): TextLayoutResult =
        measurer.measure(
            text = text,
            style = style.copy(fontSize = sizeSp.sp),
            overflow = if (cut) TextOverflow.Ellipsis else TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
            layoutDirection = layoutDirection,
            density = this,
        )

    private companion object {
        const val ELLIPSIS = "…"
    }
}
