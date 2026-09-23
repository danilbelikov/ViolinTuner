package com.violinjourney.app.core.domain.backing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shift of spec 5.25. */
class BackingRulesTest {
    private val config = BackingConfig()
    private val ms = 1_000_000L

    @Test
    fun `the shift is the backing's start minus the take's on one clock, plus what the headphones add`() {
        // the backing left the output 35 ms after the take's first sample came in; wireless headphones add 180
        assertEquals(215, BackingOffset.offsetMs(backingStartNanos = 1_035 * ms, recordStartNanos = 1_000 * ms, headphoneLatencyMs = 180, config = config))
        // no clocks (the fake source of the emulator): the headphones alone
        assertEquals(180, BackingOffset.offsetMs(null, 1_000 * ms, 180, config))
        // never past the slider's ends
        assertEquals(2_000, BackingOffset.offsetMs(4_000 * ms, 1_000 * ms, 0, config))
        assertEquals(-2_000, BackingOffset.offsetMs(0, 3_000 * ms, 0, config))
        // two seconds each way: on the owner's phone a take in wireless headphones needed the whole second of 0.69
        assertEquals(740, BackingOffset.offsetMs(1_100 * ms, 1_000 * ms, 640, config))
    }

    @Test
    fun `wired headphones add nothing, wireless ones a guess — the rest is set by ear on each take`() {
        assertEquals(config.defaultWirelessLatencyMs, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, "Pixel Buds Pro"), config))
        assertEquals(0, BackingOffset.latencyMs(AudioRoute(BackingOutput.WIRED, "USB-C to 3.5mm"), config))
        assertEquals(0, BackingOffset.latencyMs(AudioRoute(BackingOutput.USB, null), config))
    }

    @Test
    fun `the shift snaps to whole steps and the level to half decibels`() {
        assertEquals(215, BackingOffset.snap(213, config))
        assertEquals(-775, BackingOffset.snap(-777, config))
        assertEquals(-2_000, BackingOffset.snap(-2_777, config))
        assertEquals(-6.5f, BackingOffset.snapGain(-6.4f, config))
        assertEquals(6f, BackingOffset.snapGain(9f, config))
    }

    @Test
    fun `a positive shift delays the backing against the violin`() {
        assertEquals(48_000 - 9_600, BackingOffset.backingSampleAt(48_000, offsetMs = 200, sampleRate = 48_000))
        assertEquals(-9_600, BackingOffset.backingSampleAt(0, offsetMs = 200, sampleRate = 48_000))
        assertEquals(4_410, BackingOffset.backingSampleAt(0, offsetMs = -100, sampleRate = 44_100))
    }
}
