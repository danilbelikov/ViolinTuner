package com.violinjourney.app.feature.live.components

import com.violinjourney.app.feature.live.venue.VenueLook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bottom row of Live in the dark (spec 3.36.6, «Свет и приглушение»; 5.29 R6 «Приглушение»): the cards and the key at rest go
 * down with the light to 0.38; «готово» of a block and the key with its stop stay whole — the end of a block and of a performance
 * are for the corner of the eye; the key where it may not record is 0.4 times the light.
 */
class BottomRowLightTest {
    private val out = VenueLook.chromeAlpha(1f)

    @Test
    fun `a card goes down with the light and its paper comes up to its whole as a block is done`() {
        assertEquals(0.38f, out, 0.0001f)
        // glass, a running block, a running practice: with the light
        assertEquals(out, CardLight.alpha(light = out, done = 0f), 0.0001f)
        assertEquals(1f, CardLight.alpha(light = 1f, done = 0f), 0.0001f)
        // «готово»: whole in the dark
        assertEquals(1f, CardLight.alpha(light = out, done = 1f), 0.0001f)
        // on its way to «готово» (400 ms) it comes up from the light, not in a frame
        val halfway = CardLight.alpha(light = out, done = 0.5f)
        assertTrue(halfway > out && halfway < 1f, "halfway is $halfway")
        assertEquals((1f + out) / 2, halfway, 0.0001f)
    }

    @Test
    fun `the record key at rest goes down with the light and with its stop stays whole`() {
        assertEquals(out, RecordKeyLight.alpha(recording = false, enabled = true, light = out), 0.0001f)
        assertEquals(1f, RecordKeyLight.alpha(recording = false, enabled = true, light = 1f), 0.0001f)
        // a take records: the stop is how the performance ends
        assertEquals(1f, RecordKeyLight.alpha(recording = true, enabled = true, light = out), 0.0001f)
    }

    @Test
    fun `the record key where it may not record is 0 point 4 times the light`() {
        // «Настройка», «Микрофон недоступен», no permission
        assertEquals(LiveDimens.DISABLED_ALPHA, RecordKeyLight.alpha(recording = false, enabled = false, light = 1f), 0.0001f)
        assertEquals(LiveDimens.DISABLED_ALPHA * out, RecordKeyLight.alpha(recording = false, enabled = false, light = out), 0.0001f)
    }
}
