package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class DigitalSilenceWatchdogTest {
    private val rate = 48_000
    private val watchdog = DigitalSilenceWatchdog(IntonationConfig(), rate)
    private val zeros = ShortArray(512)

    private fun feedZeros(millis: Int): Boolean {
        var dead = false
        repeat(millis * rate / 1_000 / zeros.size) { dead = watchdog.isDead(zeros, zeros.size) }
        return dead
    }

    @Test
    fun `two seconds of exact zeros is a dead input`() {
        assertFalse(feedZeros(1_900))
        assertTrue(feedZeros(200))
    }

    @Test
    fun `room silence is not digital silence`() {
        val quietRoom = ShortArray(512) { if (it % 97 == 0) 1 else 0 } // one LSB of noise
        repeat(1_000) { assertFalse(watchdog.isDead(quietRoom, quietRoom.size)) }
    }

    @Test
    fun `any signal restarts the count`() {
        feedZeros(1_900)
        assertFalse(watchdog.isDead(ShortArray(512) { 100 }, 512))
        assertFalse(feedZeros(1_900))
    }

    @Test
    fun `only the valid part of a hop is inspected`() {
        val hop = ShortArray(512).also { it[300] = 5 } // stale sample beyond the 256 that were read
        var dead = false
        repeat(400) { dead = watchdog.isDead(hop, 256) }
        assertTrue(dead)
    }
}
