package com.violinjourney.app.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class CodePointsTest {
    private val violin = "\uD83C\uDFBB" // a violin

    @Test
    fun `a cut keeps an emoji whole or leaves it out`() {
        assertEquals("a".repeat(23) + violin, ("a".repeat(23) + violin).takeCodePoints(24))
        assertEquals("a".repeat(24), ("a".repeat(24) + violin).takeCodePoints(24))
        assertEquals("", violin.takeCodePoints(0))
        assertEquals("Даня", "Даня".takeCodePoints(24))
    }

    @Test
    fun `an emoji counts as one character`() {
        assertEquals(1, violin.codePointLength())
        assertEquals(24, ("a".repeat(23) + violin).codePointLength())
        assertEquals(0, "".codePointLength())
    }

    @Test
    fun `the first symbol keeps what sticks to it`() {
        val flag = "\uD83C\uDDF7\uD83C\uDDFA" // two regional indicators
        assertEquals(flag, "$flag Даня".firstSymbol())
        val wave = "\uD83D\uDC4B\uD83C\uDFFD" // a waving hand with a skin tone
        assertEquals(wave, "$wave Аня".firstSymbol())
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // three people joined by ZWJ
        assertEquals(family, "$family семья".firstSymbol())
        assertEquals("е\u0308", "е\u0308ва".firstSymbol()) // «ё» typed as «е» and a combining diaeresis
        assertEquals("1\uFE0F\u20E3", "1\uFE0F\u20E3".firstSymbol()) // a keycap
        assertEquals("\u2764\uFE0F", "\u2764\uFE0F Ира".firstSymbol()) // a heart with its emoji selector
        // a subdivision flag: a black flag and its tags
        val england = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
        assertEquals(england, "$england Том".firstSymbol())
        assertEquals("д", "даня".firstSymbol())
        assertEquals("", "".firstSymbol())
    }

    @Test
    fun `a lone half of a pair is a symbol of its own`() {
        assertEquals("\uD83C", "\uD83Cа".firstSymbol())
        assertEquals(2, "\uD83Cа".codePointLength())
    }
}
