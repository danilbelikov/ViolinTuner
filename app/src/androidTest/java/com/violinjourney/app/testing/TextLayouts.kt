package com.violinjourney.app.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/**
 * The layout of the words of a text node as its semantics hands it ([SemanticsActions.GetTextLayoutResult]) — which is not always
 * the layout drawn.
 *
 * A `Text` of a plain String — material3's `Text(String)` without `onTextLayout` and without auto size, as nearly every text of the
 * app is — is drawn by foundation's `TextStringSimpleNode`, and its semantics lays the words out anew at the whole width the node was
 * offered (`ParagraphLayoutCache.slowCreateTextLayoutResultOrNull`: `prevConstraints.copyMaxDimensions()`), then pairs that paragraph
 * with the size the node took. There `multiParagraph.width` is the width offered (`MultiParagraph.width` is its `maxWidth`) and
 * `size.width` the width taken, so [TextLayoutResult.didOverflowWidth] (`size.width < multiParagraph.width`) — and
 * [TextLayoutResult.hasVisualOverflow] with it — is true of every text narrower than the room it was offered: a chip, a button, the
 * length of the line of a card, each standing whole. The rest is true to the screen: whenever the words wrap, the new paragraph
 * breaks them at the same width as the one drawn, so its lines, their ends and ellipses, the lines maxLines drops and its height
 * are those drawn. (A text of an AnnotatedString or with inline content hands the layout drawn itself.) Ask it with
 * [assertWholeOnOneLine], never with `hasVisualOverflow`.
 */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single()
}

/**
 * The words of [node] stand whole on one line: one line, no line dropped by maxLines and nothing cut off below, no ellipsis, and
 * the width the words need on one line ([androidx.compose.ui.text.MultiParagraph.maxIntrinsicWidth]) not more than the width the
 * node took — a text that does not wrap (`softWrap = false`) is clipped at its end without an ellipsis, and only the width sees it.
 * [what] names the words; a failure says the numbers ([numbers]).
 */
fun assertWholeOnOneLine(node: SemanticsNodeInteraction, what: String) {
    val layout = node.textLayout()
    val whole = layout.lineCount == 1 &&
        !layout.didOverflowHeight &&
        !layout.isLineEllipsized(0) &&
        layout.multiParagraph.maxIntrinsicWidth <= layout.size.width
    assertTrue("«$what» is whole on one line — ${layout.numbers(node)}", whole)
}

/**
 * The words of [node] stand whole on as many lines as they take: every line but the last ends at a space — no word broken by the
 * letter or at its hyphen — and nothing is cut, by an ellipsis or by the lines its `maxLines` drops. The semantics layout of a plain
 * text is laid out anew at the width it was offered, but whenever the words wrap its lines break where the drawn ones do; an
 * auto-sized text hands the layout drawn (with the asked size in its style — its lines are still those drawn). [what] names the words.
 */
fun assertWordsWhole(node: SemanticsNodeInteraction, what: String) {
    val layout = node.textLayout()
    val text = layout.layoutInput.text.text
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        assertTrue("«$what» breaks inside a word after «${text.substring(0, end)}» — ${layout.numbers(node)}", end > 0 && text[end - 1].isWhitespace())
    }
    val cut = layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1) || layout.multiParagraph.didExceedMaxLines
    assertTrue("«$what» is not cut — ${layout.numbers(node)}", !cut)
}

/**
 * The numbers of this layout of [node], for a failure to explain itself: its lines and what cut them, the width the words need on
 * one line, the size of the node against the paragraph and the room it was laid out in (pixels), and where the node stands (dp).
 */
fun TextLayoutResult.numbers(node: SemanticsNodeInteraction): String {
    val paragraph = multiParagraph
    val ellipsized = (0 until lineCount).filter { isLineEllipsized(it) }
    return "lines $lineCount (maxLines ${layoutInput.maxLines}, cut by it ${paragraph.didExceedMaxLines}, ellipsized $ellipsized); " +
        "the words need ${paragraph.maxIntrinsicWidth} px on one line; the node ${size.width} × ${size.height} px, " +
        "the paragraph ${paragraph.width} × ${paragraph.height} px in ${layoutInput.constraints}; " +
        "didOverflowWidth $didOverflowWidth, didOverflowHeight $didOverflowHeight; " +
        "the node at ${node.getUnclippedBoundsInRoot()}, ${layoutInput.density.density} px a dp"
}
