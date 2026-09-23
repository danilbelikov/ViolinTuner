package com.violinjourney.app.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenKeyTest {
    @Test
    fun `a route without arguments is the key itself`() {
        assertEquals("live", screenKeyOf("live"))
    }

    @Test
    fun `an argument never leaves the phone, filled in or not`() {
        assertEquals("piece", screenKeyOf("piece/{pieceId}"))
        assertEquals("piece", screenKeyOf("piece/42"))
        assertEquals("session", screenKeyOf("session?take=42"))
    }

    @Test
    fun `no route is no event`() {
        assertNull(screenKeyOf(null))
        assertNull(screenKeyOf(""))
        assertNull(screenKeyOf("/"))
    }
}
