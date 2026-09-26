package com.violinjourney.app.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentGateTest {
    private val gate = ConsentGate()
    private val sent = mutableListOf<String>()

    @Test
    fun `nothing goes before the consent is read and what came waits for it`() {
        assertFalse(gate.open)
        gate.pass { sent += "first screen" }
        assertEquals(emptyList(), sent)
        var openWhenTold: Boolean? = null
        gate.follow(true) { openWhenTold = gate.open }
        assertEquals(false, openWhenTold, "the library is unmuted before anything reaches it")
        assertEquals(listOf("first screen"), sent)
        assertTrue(gate.open)
        gate.pass { sent += "next" }
        assertEquals(listOf("first screen", "next"), sent)
    }

    @Test
    fun `nothing goes while the consent is off`() {
        gate.pass { sent += "before" }
        var openWhenTold: Boolean? = null
        gate.follow(false) { openWhenTold = gate.open }
        assertEquals(false, openWhenTold, "the gate closes before the library is muted")
        gate.pass { sent += "while off" }
        assertEquals(emptyList(), sent, "what waited for a consent that turned out off is dropped too")

        gate.follow(true) {}
        gate.follow(false) {}
        gate.pass { sent += "switched off again" }
        assertEquals(emptyList(), sent)
    }
}
