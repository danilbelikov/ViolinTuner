package com.violinjourney.app.feature.sound.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** The fall of the level bars of «Звук»: up at once, the whole bar down in ~300 ms (spec 5.11). */
class MeterFallTest {
    @Test
    fun `a full bar is empty in exactly the fall time`() {
        assertEquals(0.5f, MeterFall.next(1f, 0f, MeterFall.FULL_FALL_MS / 2), 1e-6f)
        assertEquals(0f, MeterFall.next(1f, 0f, MeterFall.FULL_FALL_MS), 1e-6f)
    }

    @Test
    fun `a higher reading is taken at once`() {
        assertEquals(0.8f, MeterFall.next(0.2f, 0.8f, 1), 1e-6f)
        // and a lower one is fallen to, not jumped to
        assertEquals(0.7f, MeterFall.next(0.8f, 0.1f, 30), 1e-6f)
    }

    @Test
    fun `the bar never goes below empty and stands without time`() {
        assertEquals(0f, MeterFall.next(0.1f, 0f, 10 * MeterFall.FULL_FALL_MS))
        assertEquals(0.4f, MeterFall.next(0.4f, 0f, 0))
    }
}
