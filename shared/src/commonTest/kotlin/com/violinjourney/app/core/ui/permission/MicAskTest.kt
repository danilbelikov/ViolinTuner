package com.violinjourney.app.core.ui.permission

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class MicAskTest {
    private val ask = MicAsk()

    @Test
    fun `the first tap may ask`() {
        assertTrue(ask.mayAsk())
    }

    @Test
    fun `a second tap before the answer may not`() {
        ask.mayAsk()
        assertFalse(ask.mayAsk())
        assertFalse(ask.mayAsk())
    }

    @Test
    fun `after the answer a tap may ask again`() {
        ask.mayAsk()
        ask.answered()
        assertTrue(ask.mayAsk())
    }
}
