package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScreenOnHoldsTest {
    private val written = mutableListOf<Boolean>()
    private val holds = ScreenOnHolds { written += it }

    @Test
    fun `nobody holds the screen at first`() {
        assertFalse(holds.held)
        assertEquals(emptyList(), written, "nothing is written before anyone comes")
    }

    @Test
    fun `the screen stays on through a crossfade and goes dark only after the last holder has gone`() {
        // the NavHost crossfade: the screen that comes in (B) takes the flag before the leaving one (A) gives it back
        holds.take() // A — a piece with a take running
        holds.take() // B — the stand
        holds.give() // A leaves
        assertEquals(listOf(true, true, true), written, "the stand must stay lit while the take goes on")
        holds.give() // B leaves
        assertEquals(listOf(true, true, true, false), written)
    }

    @Test
    fun `holders that come and go under another one do not put the screen out`() {
        holds.take() // one holder stays the whole time
        holds.take() // others come…
        holds.give() // …and go
        holds.take()
        holds.give()
        assertEquals(listOf(true, true, true, true, true), written)
        holds.give()
        assertEquals(false, written.last())
        assertFalse(holds.held)
    }

    @Test
    fun `a give without a take does not go below nobody`() {
        holds.give()
        holds.take()
        assertEquals(listOf(false, true), written, "one take after a stray give holds the screen")
        holds.give()
        assertEquals(listOf(false, true, false), written)
    }
}
