package com.violinjourney.app.feature.live.components

import com.violinjourney.app.feature.live.components.LiveCardWords.First
import com.violinjourney.app.feature.live.components.LiveCardWords.Fit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The words of a card of the bottom row of Live (spec 3.36.6, 5.29 R6): 13.5 / 12.5 sp, then both 0.5 smaller down to 12, then the
 * first line on two lines at its spaces; the name of an element ends in «…» and makes nothing give way; a time never wraps; no word
 * breaks inside. Beyond the spec: a word too wide for the room beside the lead takes the lead's room, and only then the second line
 * wraps. Measured here by a plain rule — every letter half a point wide, a line [SCALE] of its size high.
 */
class LiveCardWordsTest {
    /** The words as a text measurer of letters [LETTER] of a point wide would lay them out, at a font scale of [SCALE]. */
    private class Letters(private val first: String, private val second: String, private val scale: Float = 1f) {
        private fun width(text: String, sp: Float) = text.length * sp * LETTER * scale

        private fun lineOf(text: String, sp: Float) =
            LiveCardWords.Line(width(text, sp), LiveCardWords.wholeWords(text).maxOfOrNull { width(it, sp) } ?: 0f)

        /** Greedy, at the spaces: what a line breaker does with words that each fit. */
        private fun lines(text: String, sp: Float, room: Float): Int {
            var lines = 1
            var line = ""
            for (word in text.split(' ')) {
                val tried = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && width(tried, sp) > room) {
                    lines++
                    line = word
                } else {
                    line = tried
                }
            }
            return lines
        }

        val measure = LiveCardWords.Measure(
            first = { sp -> lineOf(first, sp) },
            second = { sp -> lineOf(second, sp) },
            wrapped = { which, sp, room -> lines(if (which == 0) first else second, sp, room) },
            lineHeight = { sp, em -> sp * em * scale },
        )
    }

    private fun fit(first: String, second: String, leadRoom: Float?, bareRoom: Float, gives: First = First.WRAPS, scale: Float = 1f): Fit =
        LiveCardWords.fit(leadRoom, bareRoom, CARD, gives, Letters(first, second, scale).measure)

    /** The same fit, its height summed from sizes in floats: to a thousandth. */
    private fun assertFit(expected: Fit, actual: Fit) {
        assertEquals(expected.copy(height = 0f), actual.copy(height = 0f))
        assertEquals(expected.height, actual.height, 0.001f, "the height of the lines")
    }

    @Test
    fun `the sizes step down half a point at a time from 13 and a half and 12 and a half to 12`() {
        assertEquals(listOf(13.5f to 12.5f, 13f to 12f, 12.5f to 12f, 12f to 12f), LiveCardWords.steps)
    }

    @Test
    fun `words that fit stand at their full size on one line each beside the lead`() {
        // «Что играю» at 13.5: 60.75
        assertFit(Fit(lead = true, firstSp = 13.5f, secondSp = 12.5f, firstLines = 1, secondLines = 1, lineEm = 1.2f, height = 31.2f), fit("Что играю", "выбрать", 100f, 120f))
    }

    @Test
    fun `words a little too wide step down only as far as they must`() {
        // 13.5 → 60.75, 13 → 58.5, 12.5 → 56.25: the room of 58 takes 12.5, and the second line with it
        val stepped = fit("Что играю", "выбрать", 58f, 80f)
        assertEquals(true, stepped.lead)
        assertEquals(12.5f, stepped.firstSp)
        assertEquals(12f, stepped.secondSp)
        assertEquals(1, stepped.firstLines)
    }

    @Test
    fun `where not even 12 holds the first line it goes on two lines at its space and the lead stays`() {
        // «Что играю» at 12: 54 in 50; «Что» and «играю» fit alone
        assertFit(Fit(lead = true, firstSp = 12f, secondSp = 12f, firstLines = 2, secondLines = 1, lineEm = 1.2f, height = 43.2f), fit("Что играю", "выбрать", 50f, 70f))
    }

    @Test
    fun `a word too wide for the room beside the lead takes the room of the lead at the size it fits there`() {
        // «Commencer» at 12: 54 — no space to wrap at in 50; without the lead the room of 77 holds it at 13.5
        val bare = fit("Commencer", "séance", 50f, 77f)
        assertEquals(false, bare.lead)
        assertEquals(13.5f, bare.firstSp)
        assertEquals(1, bare.firstLines)
    }

    @Test
    fun `the name of an element ends in an ellipsis and makes nothing give way`() {
        val named = fit("Концерт ля минор, соч. 64, I. Allegro molto appassionato", "ещё 7 мин", 77f, 104f, gives = First.ELLIPSIS)
        assertFit(Fit(lead = true, firstSp = 13.5f, secondSp = 12.5f, firstLines = 1, secondLines = 1, lineEm = 1.2f, height = 31.2f), named)
    }

    @Test
    fun `the minutes under a name take the room of the lead before they go on two lines`() {
        // «ещё 12 мин» at 12: 60 — not beside the lead (50), whole without it (64)
        val bare = fit("D-dur · 2 октавы", "ещё 12 мин", 50f, 64f, gives = First.ELLIPSIS)
        assertEquals(false, bare.lead)
        assertEquals(1, bare.secondLines)
        // where they fit nowhere on one line they wrap at a space, and the lead stays: three lines of 12, 43.2 high
        assertFit(
            Fit(lead = true, firstSp = 12f, secondSp = 12f, firstLines = 1, secondLines = 2, lineEm = 1.2f, height = 43.2f),
            fit("D-dur · 2 октавы", "ещё 12 мин", 50f, 55f, gives = First.ELLIPSIS),
        )
    }

    @Test
    fun `three lines at a large font stand closer where only so they stand in the card`() {
        // at 1.5 three lines of 12 at 1.2 are 64.8, more than the card of 60; at 1.1 — 59.4
        val close = fit("D-dur · 2 октавы", "ещё 12 мин", 75f, 82f, gives = First.ELLIPSIS, scale = 1.5f)
        assertFit(Fit(lead = true, firstSp = 12f, secondSp = 12f, firstLines = 1, secondLines = 2, lineEm = 1.1f, height = 59.4f), close)
    }

    @Test
    fun `a time never wraps and is never cut while any room holds it`() {
        // «24:18» at 12: 30 — not beside the dot in 28, at 13.5 (33.75) whole in 35 without it; «мин» (18.75) would stand beside the
        // dot, so it is the time alone that makes the dot give way: a time never stands where it would be cut
        assertFit(Fit(lead = false, firstSp = 13.5f, secondSp = 12.5f, firstLines = 1, secondLines = 1, lineEm = 1.2f, height = 31.2f), fit("24:18", "мин", 28f, 35f, gives = First.WHOLE))
    }

    @Test
    fun `where nothing holds every word the least size stands without the lead and a word too wide stays on one line`() {
        // «Commencer» at 12: 54 in 40 — no size, no lead and no space make it fit: one line, cut at its end rather than broken
        val last = fit("Commencer", "séance", 30f, 40f)
        assertEquals(false, last.lead)
        assertEquals(12f, last.firstSp)
        assertEquals(1, last.firstLines)
    }

    @Test
    fun `the words stand in the middle of the card and over its brass line where they must`() {
        // the line clear of the words from 48 down (60 − 7 − 3 − 2)
        assertEquals(0f, LiveCardWords.liftOverBar(31.2f, CARD, CLEAR)!!, 0.001f)
        assertEquals(4.56f, LiveCardWords.liftOverBar(40.56f, CARD, CLEAR)!!, 0.001f)
        assertEquals(12f, LiveCardWords.liftOverBar(48f, CARD, CLEAR)!!, 0.001f)
        // three lines that need the whole card: the brass line gives way
        assertNull(LiveCardWords.liftOverBar(49f, CARD, CLEAR))
    }

    @Test
    fun `the words to keep whole are those between spaces and not of scripts that break between letters`() {
        assertEquals(listOf("Was", "ich", "spiele"), LiveCardWords.wholeWords("Was ich spiele"))
        assertEquals(listOf("ещё", "12 мин"), LiveCardWords.wholeWords("ещё 12 мин"))
        assertEquals(listOf("12"), LiveCardWords.wholeWords("还剩 12 分钟"))
        assertEquals(emptyList(), LiveCardWords.wholeWords("弾く曲"))
    }

    private companion object {
        const val LETTER = 0.5f
        const val CARD = 60f
        const val CLEAR = 48f
    }
}
