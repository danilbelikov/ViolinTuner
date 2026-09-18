package com.example.violintuner.feature.practice.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialTest {
    @Test
    fun `the avatar letter is the first character in upper case`() {
        assertEquals("Д", initialOf("даня"))
        assertEquals("A", initialOf("anna"))
        assertEquals("", initialOf(""))
    }

    @Test
    fun `an emoji stays whole`() {
        assertEquals("🎻", initialOf("🎻 Даня"))
    }
}
