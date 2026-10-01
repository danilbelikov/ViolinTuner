package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The line of one line of a button cut around its number (spec 3.36.7, 5.29 R7: «числа не режутся»): the second line of «В путь» —
 * «спишется 1 600 из 47 884» — and of «В дорогу», whose number stands in the middle in English.
 */
class ButtonLineTest {
    private val nbsp = ' '
    private val spend = "спишется 1${nbsp}600 из 47${nbsp}884"

    @Test
    fun `a number at the end - the words before it give way and it keeps the space before it`() {
        assertEquals(ButtonLine.Parts("спишется", " 1${nbsp}600 из 47${nbsp}884", ""), ButtonLine.split(spend, "1${nbsp}600 из 47${nbsp}884"))
    }

    @Test
    fun `a number in the middle - both sides give way and it keeps its spaces`() {
        assertEquals(ButtonLine.Parts("Vienna →", " 1${nbsp}128 ", "to Prague"), ButtonLine.split("Vienna → 1${nbsp}128 to Prague", "1${nbsp}128"))
    }

    @Test
    fun `a number at the start - only the words after it give way`() {
        assertEquals(ButtonLine.Parts("", "1${nbsp}600 of 47${nbsp}884 ", "will be spent"), ButtonLine.split("1${nbsp}600 of 47${nbsp}884 will be spent", "1${nbsp}600 of 47${nbsp}884"))
    }

    @Test
    fun `a keep that is not in the line or is empty keeps nothing - the line is cut at its end`() {
        assertNull(ButtonLine.split(spend, "15${nbsp}000"))
        assertNull(ButtonLine.split(spend, ""))
    }

    @Test
    fun `a keep that is the whole line leaves nothing to give way`() {
        assertEquals(ButtonLine.Parts("", spend, ""), ButtonLine.split(spend, spend))
    }

    @Test
    fun `the sides keep their own widths while both fit`() {
        assertEquals(40f to 60f, ButtonLine.sides(40f, 60f, 120f))
        assertEquals(40f to 60f, ButtonLine.sides(40f, 60f, 100f), "exactly")
    }

    @Test
    fun `the narrower side keeps its own when it needs no more than half - the other takes the rest`() {
        assertEquals(30f to 70f, ButtonLine.sides(30f, 90f, 100f))
        assertEquals(70f to 30f, ButtonLine.sides(90f, 30f, 100f))
        assertEquals(0f to 100f, ButtonLine.sides(0f, 140f, 100f), "no words before the number")
    }

    @Test
    fun `two wide sides get half each - and no room is no width`() {
        assertEquals(50f to 50f, ButtonLine.sides(80f, 90f, 100f))
        assertEquals(0f to 0f, ButtonLine.sides(80f, 90f, -12f), "the number alone is wider than the line")
    }

    @Test
    fun `the span of the numbers holds both in either order - and what stands between them`() {
        assertEquals("1${nbsp}600 из 47${nbsp}884", ButtonLine.span(spend, listOf("1${nbsp}600", "47${nbsp}884")))
        assertEquals("47${nbsp}884 のうち 1${nbsp}600", ButtonLine.span("47${nbsp}884 のうち 1${nbsp}600 を使います", listOf("1${nbsp}600", "47${nbsp}884")))
        // the price inside the purse: «600» first found in «1 600» — the span still runs from the first to the last of them
        assertEquals("1${nbsp}600 のうち 600", ButtonLine.span("1${nbsp}600 のうち 600 を使います", listOf("600", "1${nbsp}600")))
        assertEquals("300 из 300", ButtonLine.span("спишется 300 из 300", listOf("300", "300")), "a price as large as the purse")
    }

    @Test
    fun `no span where a number is missing from the line or none is given`() {
        assertNull(ButtonLine.span(spend, listOf("1${nbsp}600", "15${nbsp}000")))
        assertNull(ButtonLine.span(spend, emptyList()))
    }
}
