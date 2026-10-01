package com.violinjourney.app.feature.home

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.WholeText
import com.violinjourney.app.core.ui.components.WholeWords
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a row «ключ — значение» of the card of a thing stands (spec 5.29 R7, «число не режется», the key «переносится по пробелу»; the
 * review of stage 119): the value keeps the room of its number glued to its word, the key takes what that leaves; beside only where the
 * key keeps its words whole there — at its size or smaller, down to its least — and the value its own; else the value goes under the
 * key. On a phone of 360 at the font 1.3 «Per San Pietroburgo» beside «mancano 1 900» broke the number, and iOS broke «Pour
 * Saint-Pétersbourg» after its hyphen. Laid out here by the text engine of the app, in the system font; every room is made from widths
 * the test measures itself, so the rule is checked whatever the font.
 */
class CardRowFitIosTest {
    private val density = Density(1f)
    private val direction = LayoutDirection.Ltr
    private val measurer = TextMeasurer(createFontFamilyResolver(), density, direction)
    private val keyStyle = TextStyle(fontSize = 14.sp)
    private val valueStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    private val gap = 12

    private fun widthOf(text: String, style: TextStyle): Int = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width

    private fun laid(text: String, style: TextStyle, room: Int): TextLayoutResult = measurer.measure(text, style, constraints = Constraints(maxWidth = room))

    private fun keyAt(sizeSp: Float) = keyStyle.copy(fontSize = sizeSp.sp)

    private fun keyRoom(key: String, value: String, width: Int): Int? = CardRowFit.keyRoom(
        measurer,
        WholeText(key, keyStyle, leastSp = CardRowFit.KEY_LEAST_SP),
        listOf(WholeText(value, valueStyle, maxLines = CardRowFit.VALUE_LINES)),
        width,
        gap,
        density,
        direction,
    )

    /** The size the key takes in [room] — as its `Text` steps down there — and its layout at it. */
    private fun keyIn(key: String, room: Int): TextLayoutResult {
        val at = { sizeSp: Float -> laid(key, keyAt(sizeSp), room) }
        return at(WholeWords.largest(keyStyle.fontSize.value, CardRowFit.KEY_LEAST_SP, at))
    }

    @Test
    fun `the value keeps the room of its number and the key goes on at its spaces in what is left`() {
        val number = widthOf(NUMBER, valueStyle)
        // a room for the key that holds its widest word, not the whole of it: the number binds, not the share of the key
        val room = widthOf(LAST_WORD, keyStyle) + 4
        val width = gap + number + room
        assertTrue(room <= (width * CardRowFit.KEY_SHARE).toInt(), "the share of the key does not bind: $room of $width")
        // why: beside the key on one line the number would not fit
        assertTrue(width - gap - widthOf(SPACED_KEY, keyStyle) < number, "the key on one line leaves the number less than it needs")
        val taken = assertNotNull(keyRoom(SPACED_KEY, NUMBER, width), "beside")
        assertTrue(abs(taken - room) <= 1, "the key takes what the number leaves: $taken, $room")
        val key = keyIn(SPACED_KEY, taken)
        assertTrue(key.lineCount > 1 && WholeWords.of(key), "the key on more lines, at its spaces")
        val value = laid(NUMBER, valueStyle, width - gap - key.size.width)
        assertEquals(1, value.lineCount, "the number whole on its line")
    }

    @Test
    fun `a key that would break at its hyphen beside the value steps down until it stands whole`() {
        val at14 = widthOf(HYPHEN_KEY, keyStyle)
        val atLeast = widthOf(HYPHEN_KEY, keyAt(CardRowFit.KEY_LEAST_SP))
        // the share of the key between the key on one line at its least and at its size
        val width = ((at14 + atLeast) / 2 / CardRowFit.KEY_SHARE).toInt()
        val share = (width * CardRowFit.KEY_SHARE).toInt()
        assertTrue(share in (atLeast + 1) until at14, "the share $share between $atLeast and $at14")
        // why: iOS breaks the name at its hyphen there at 14 sp
        assertFalse(WholeWords.of(laid(HYPHEN_KEY, keyStyle, share)), "at 14 sp «До Санкт-» / «Петербурга»")
        assertEquals(share, keyRoom(HYPHEN_KEY, WORDS, width), "beside, in the share of the key")
        val key = keyIn(HYPHEN_KEY, share)
        assertEquals(1, key.lineCount, "smaller, on one line")
        assertTrue(key.layoutInput.style.fontSize.value < keyStyle.fontSize.value, "smaller: ${key.layoutInput.style.fontSize}")
    }

    @Test
    fun `where the key breaks at its hyphen at every size the value goes under it`() {
        val word = widthOf(FR_WORD, keyStyle)
        val atLeast = widthOf(FR_KEY, keyAt(CardRowFit.KEY_LEAST_SP))
        assertTrue(word + 2 < atLeast, "the premise: the name at 14 sp is narrower than the whole key at its least ($word, $atLeast)")
        val width = ((word + atLeast) / 2 / CardRowFit.KEY_SHARE).toInt()
        val share = (width * CardRowFit.KEY_SHARE).toInt()
        // at every size the key goes on two lines, and iOS breaks it after «Saint-»
        assertFalse(WholeWords.of(laid(FR_KEY, keyStyle, share)), "at 14 sp")
        assertFalse(WholeWords.of(laid(FR_KEY, keyAt(CardRowFit.KEY_LEAST_SP), share)), "at its least")
        assertNull(keyRoom(FR_KEY, FR_WORDS, width), "under the key")
    }

    @Test
    fun `a value wider than the row goes under the key`() {
        assertNull(keyRoom(SPACED_KEY, NUMBER, widthOf(NUMBER, valueStyle)), "no room for the key beside the number")
        assertNull(keyRoom(SPACED_KEY, NUMBER, 0), "no row")
    }

    private companion object {
        const val SPACED_KEY = "Per San Pietroburgo"
        const val LAST_WORD = "Pietroburgo"
        const val NUMBER = "mancano 1 900"
        const val HYPHEN_KEY = "До Санкт-Петербурга"
        const val WORDS = "всё ещё хватает"
        const val FR_KEY = "Pour Saint-Pétersbourg"
        const val FR_WORD = "Saint-Pétersbourg"
        const val FR_WORDS = "toujours assez"
    }
}
