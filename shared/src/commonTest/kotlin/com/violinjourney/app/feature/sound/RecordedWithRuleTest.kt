package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.backing.BackingOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The line of the headphones of «Минусовка» (spec 3.36.5): what a take under a backing was recorded in, from what it keeps. */
class RecordedWithRuleTest {
    @Test
    fun `wireless headphones with a name say it and the lag the guess added`() {
        assertEquals(RecordedWith.Wireless("Pixel Buds", latencyMs = 200), RecordedWithRule.of(BackingOutput.BLUETOOTH, "Pixel Buds", 200))
    }

    @Test
    fun `wireless headphones whose lag the clocks held already say their name alone`() {
        assertEquals(RecordedWith.Wireless("AirPods", latencyMs = 0), RecordedWithRule.of(BackingOutput.BLUETOOTH, "AirPods", 0))
    }

    @Test
    fun `wired and USB headphones are wired whatever their name`() {
        assertEquals(RecordedWith.Wired, RecordedWithRule.of(BackingOutput.WIRED, "Jack", 0))
        assertEquals(RecordedWith.Wired, RecordedWithRule.of(BackingOutput.USB, null, 0))
    }

    @Test
    fun `wireless headphones without a name and the speaker have no line`() {
        assertNull(RecordedWithRule.of(BackingOutput.BLUETOOTH, null, 200))
        assertNull(RecordedWithRule.of(BackingOutput.BLUETOOTH, "  ", 200))
        assertNull(RecordedWithRule.of(BackingOutput.SPEAKER, "Phone", 0))
    }

    @Test
    fun `a name is said as it is without the spaces around it`() {
        assertEquals(RecordedWith.Wireless("Buds 2", latencyMs = 150), RecordedWithRule.of(BackingOutput.BLUETOOTH, " Buds 2 ", 150))
    }
}
