package com.example.violintuner.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralRuTest {
    private fun form(count: Int) = Formats.pluralRu(count, "сессия", "сессии", "сессий")

    @Test
    fun `russian plural forms`() {
        assertEquals("сессий", form(0))
        assertEquals("сессия", form(1))
        assertEquals("сессии", form(2))
        assertEquals("сессии", form(4))
        assertEquals("сессий", form(5))
        assertEquals("сессий", form(11))
        assertEquals("сессий", form(14))
        assertEquals("сессия", form(21))
        assertEquals("сессии", form(102))
        assertEquals("сессий", form(111))
    }
}
