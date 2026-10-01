package com.violinjourney.app.core.ui.components

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Words laid out whole on iOS (spec 5.29 R7; the review of stage 117). SkParagraph breaks a line after a hyphen by the rules of ICU —
 * Android does not without hyphenation — so «до Санкт-Петербурга · кораблём · 4 дня» goes on as «до Санкт-» / «Петербурга …» in a
 * room where its widest word, «Санкт-Петербурга», still fits: the width of that word said «beside the number» there, and the card of
 * the path broke the name of the city on an iPhone with a large font. The layout decides now ([WholeWords], [wordsStandBeside]), and
 * the name of a city steps down rather than break at its hyphen ([WholeWords.largest], the city of the journey). Laid out here by the
 * text engine of the app, in the system font; every width is measured by the test itself, so the rule is checked whatever the font.
 */
class WholeWordsIosTest {
    private val density = Density(1f)
    private val direction = LayoutDirection.Ltr
    private val measurer = TextMeasurer(createFontFamilyResolver(), density, direction)
    private val words = TextStyle(fontSize = 14.sp)

    private fun widthOf(text: String, style: TextStyle = words): Int = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width

    private fun laid(text: String, maxWidth: Int, style: TextStyle = words): TextLayoutResult =
        measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))

    private fun sized(sizeSp: Float) = TextStyle(fontSize = sizeSp.sp)

    @Test
    fun `a line broken after the hyphen of a name is not whole where its widest word still fits`() {
        val word = widthOf(NAME)
        val phrase = widthOf(TO_NAME)
        // the room the rule of the widest word took for enough: the word fits in it, «до» with the word does not
        val room = (word + phrase) / 2
        assertTrue(room > word + 1 && room < phrase, "the word $word, the phrase $phrase, the room $room")
        val layout = laid(LINE, room)
        // why the rule is the layout: iOS breaks the line after the hyphen here
        assertEquals("до Санкт-", LINE.substring(0, layout.getLineEnd(0)), "the first line in $room px")
        assertFalse(WholeWords.of(layout), "a name broken at its hyphen is not whole")
        assertFalse(wordsStandBeside(measurer, LINE, words, room, density, direction), "the number goes under the line")
    }

    @Test
    fun `a line that goes on at its spaces is whole and stands beside the number`() {
        // «до Санкт-Петербурга ·» on the first line, the rest after its space
        val room = widthOf("$TO_NAME ·") + 4
        val layout = laid(LINE, room)
        assertTrue(layout.lineCount > 1, "on more lines than one: ${layout.lineCount}")
        assertTrue(WholeWords.of(layout), "broken at spaces only")
        assertTrue(wordsStandBeside(measurer, LINE, words, room, density, direction), "beside, on two lines")
        assertTrue(wordsStandBeside(measurer, LINE, words, widthOf(LINE) + 1, density, direction), "beside, on one line")
        assertFalse(wordsStandBeside(measurer, LINE, words, 0, density, direction), "no room beside the number: under it")
    }

    @Test
    fun `the name of a city steps down rather than break at its hyphen and keeps its size where it fits`() {
        val at26 = widthOf(CITY, sized(26f))
        val at20 = widthOf(CITY, sized(20f))
        // 26 sp does not fit here, 20 does
        val room = (at26 + at20) / 2
        assertFalse(WholeWords.of(laid(CITY, room, sized(26f))), "at 26 sp the name breaks in $room px")
        val size = WholeWords.largest(26f, 20f) { sizeSp -> laid(CITY, room, sized(sizeSp)) }
        assertTrue(size < 26f && size >= 20f, "steps down in $room px: $size sp")
        assertEquals(1, laid(CITY, room, sized(size)).lineCount, "on one line at $size sp")
        assertEquals(26f, WholeWords.largest(26f, 20f) { sizeSp -> laid(CITY, at26 + 2, sized(sizeSp)) }, "where it fits it keeps its size")
        // a name with a space goes on at it and keeps its size
        val spaced = widthOf(SPACED, sized(26f)) - 10
        assertEquals(26f, WholeWords.largest(26f, 20f) { sizeSp -> laid(SPACED, spaced, sized(sizeSp)) }, "a space is where it goes on")
        // where no size keeps it whole, the least — the limit
        assertEquals(20f, WholeWords.largest(26f, 20f) { sizeSp -> laid(CITY, at20 / 2, sized(sizeSp)) }, "the least where nothing fits")
    }

    private companion object {
        const val NAME = "Санкт-Петербурга"
        const val TO_NAME = "до Санкт-Петербурга"
        const val LINE = "до Санкт-Петербурга · кораблём · 4 дня"
        const val CITY = "Санкт-Петербург"
        const val SPACED = "Sankt Petersburg"
    }
}
