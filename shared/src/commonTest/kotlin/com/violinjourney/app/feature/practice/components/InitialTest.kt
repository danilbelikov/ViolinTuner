package com.violinjourney.app.feature.practice.components

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialTest {
    @Test
    fun `the avatar letter is the first character in upper case`() {
        assertEquals("Д", initialOf("даня"))
        assertEquals("A", initialOf("anna"))
        assertEquals("", initialOf(""))
    }

    @Test
    fun `an emoji stays whole`() {
        assertEquals("\uD83C\uDFBB", initialOf("\uD83C\uDFBB Даня"))
    }

    @Test
    fun `a flag - a skin tone - a family and a letter with its mark stay whole`() {
        assertEquals("\uD83C\uDDF7\uD83C\uDDFA", initialOf("\uD83C\uDDF7\uD83C\uDDFA Даня"))
        assertEquals("\uD83D\uDC4B\uD83C\uDFFD", initialOf("\uD83D\uDC4B\uD83C\uDFFD Аня"))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(family, initialOf("$family семья"))
        // «ёва» typed as «е» and a combining diaeresis: the mark is not left behind
        assertEquals("Е\u0308", initialOf("е\u0308ва"))
    }
}
