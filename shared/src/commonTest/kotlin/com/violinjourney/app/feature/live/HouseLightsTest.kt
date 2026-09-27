package com.violinjourney.app.feature.live

import kotlin.test.assertEquals
import kotlin.test.Test

class HouseLightsTest {
    private val second = 1_000_000_000L
    private val now = 1_000 * second

    @Test
    fun `no note ever keeps the light on`() {
        assertEquals(0, HouseLights.msStillOut(null, now))
    }

    @Test
    fun `a note two seconds ago keeps it out four more`() {
        assertEquals(4_000, HouseLights.msStillOut(now - 2 * second, now))
    }

    @Test
    fun `six seconds and more after the note it is on`() {
        assertEquals(0, HouseLights.msStillOut(now - 6 * second, now))
        assertEquals(0, HouseLights.msStillOut(now - 10 * second, now))
    }

    @Test
    fun `a moment ahead of now is taken as long ago`() {
        assertEquals(0, HouseLights.msStillOut(now + second, now))
    }

    @Test
    fun `a note that just ended keeps it out the whole six seconds`() {
        assertEquals(6_000, HouseLights.msStillOut(now, now))
    }
}
