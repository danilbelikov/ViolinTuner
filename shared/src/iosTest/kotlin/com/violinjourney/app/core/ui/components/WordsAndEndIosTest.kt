package com.violinjourney.app.core.ui.components

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What decides whether the end of a row stands beside its words or under them (spec 5.29 R7; the review of stage 118): every text
 * of the words, laid out in the room the end leaves, keeps its words whole — at some size from its style's down to its least, on no
 * more lines than its `Text` has ([textsStandWhole]). On 360 the name «Маленький деревянный домик» in «Дома» had 64 to 79 dp beside
 * «хватает · 3 000 ›», and its words broke. Laid out here by the text engine of the app, in the system font; every room is made from
 * widths the test measures itself, so the rule is checked whatever the font.
 */
class WordsAndEndIosTest {
    private val density = Density(1f)
    private val direction = LayoutDirection.Ltr
    private val measurer = TextMeasurer(createFontFamilyResolver(), density, direction)
    private val name = TextStyle(fontSize = NAME_SP.sp)
    private val note = TextStyle(fontSize = NOTE_SP.sp)

    private fun widthOf(text: String, style: TextStyle): Int = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width

    private fun laid(text: String, style: TextStyle, room: Int, maxLines: Int = Int.MAX_VALUE): TextLayoutResult =
        measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = room))

    private fun stand(room: Int, vararg texts: WholeText): Boolean = textsStandWhole(measurer, texts.toList(), room, density, direction)

    private fun nameOfTwoLines(leastSp: Float = LEAST_SP) = WholeText(NAME, name, maxLines = 2, leastSp = leastSp)

    @Test
    fun `a name on two lines of whole words at its size stands beside the end`() {
        // «Маленький» on the first line, «деревянный домик» on the second: no more
        val room = widthOf(LAST_TWO, name) + 2
        assertEquals(2, laid(NAME, name, room).lineCount, "the name in $room px at $NAME_SP sp")
        assertTrue(stand(room, nameOfTwoLines()), "two lines of whole words")
    }

    @Test
    fun `a name that needs a third line at its size steps down to two and then stands beside`() {
        // «деревянный домик» fits on a line at the least size, not at the size of the style: at 16 sp the name needs three lines
        val room = (widthOf(LAST_TWO, name) + widthOf(LAST_TWO, name.copy(fontSize = LEAST_SP.sp))) / 2
        assertEquals(3, laid(NAME, name, room).lineCount, "the name in $room px at $NAME_SP sp")
        assertTrue(stand(room, nameOfTwoLines()), "smaller, two lines of whole words")
        // where it may not step down, its two lines cut it: the end goes under
        assertFalse(stand(room, nameOfTwoLines(leastSp = NAME_SP)), "at its own size alone it is cut by its two lines")
        // where its lines are not limited, three lines of whole words stand too
        assertTrue(stand(room, WholeText(NAME, name)), "three lines of whole words, unlimited")
    }

    @Test
    fun `a word wider than the room at every size never stands beside`() {
        val room = widthOf(WIDEST, name.copy(fontSize = LEAST_SP.sp)) - 4
        assertFalse(stand(room, nameOfTwoLines()), "«$WIDEST» breaks by the letter even at $LEAST_SP sp")
    }

    @Test
    fun `every text must stand - a note that breaks sends the end under a name that fits`() {
        val room = widthOf(LAST_TWO, name) + 2
        assertTrue(stand(room, nameOfTwoLines(), WholeText(NOTE, note)), "the name and its note, on lines of whole words")
        assertTrue(widthOf(LONG_WORD, note) > room, "the premise: a word of the note wider than the room")
        assertTrue(stand(room, nameOfTwoLines()), "the name alone stands")
        assertFalse(stand(room, nameOfTwoLines(), WholeText(LONG_WORD, note)), "a word of the note breaks: the end goes under")
    }

    @Test
    fun `no room beside the end is no room`() {
        assertFalse(stand(0, WholeText("a", note)))
        assertFalse(stand(-10, WholeText("a", note)))
    }

    private companion object {
        const val NAME_SP = 16f
        const val LEAST_SP = 13f
        const val NOTE_SP = 13f
        const val NAME = "Маленький деревянный домик"
        const val LAST_TWO = "деревянный домик"
        const val WIDEST = "деревянный"
        const val NOTE = "крыльцо, печная труба, яблоня"

        /** One word of a note wider than any room of the test. */
        const val LONG_WORD = "достопримечательностидостопримечательности"
    }
}
