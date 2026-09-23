package com.example.violintuner.core.domain.backing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shift and the remembered latencies of spec 5.25. */
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
        assertEquals(1_000, BackingOffset.offsetMs(3_000 * ms, 1_000 * ms, 0, config))
        assertEquals(-1_000, BackingOffset.offsetMs(0, 2_000 * ms, 0, config))
        // a second each way: wireless headphones and a late ear together go past half a second
        assertEquals(740, BackingOffset.offsetMs(1_100 * ms, 1_000 * ms, 640, config))
    }

    @Test
    fun `wired headphones add nothing unless set, wireless ones a guess until they are`() {
        val latencies = HeadphoneLatencies.EMPTY.with("Pixel Buds Pro", 212, config)
        assertEquals(212, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, "Pixel Buds Pro"), latencies, config))
        assertEquals(config.defaultWirelessLatencyMs, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, "Other"), latencies, config))
        assertEquals(0, BackingOffset.latencyMs(AudioRoute(BackingOutput.WIRED, "USB-C to 3.5mm"), latencies, config))
        assertEquals(212, BackingOffset.latencyMs(AudioRoute(BackingOutput.WIRED, "Pixel Buds Pro"), latencies, config))
        // headphones without a name keep theirs under their kind
        assertEquals(90, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, null), HeadphoneLatencies.EMPTY.with("BLUETOOTH", 90, config), config))
        assertNull(AudioRoute(BackingOutput.SPEAKER, "Speaker").latencyKey)
    }

    @Test
    fun `the shift snaps to whole steps and the level to half decibels`() {
        assertEquals(215, BackingOffset.snap(213, config))
        assertEquals(-775, BackingOffset.snap(-777, config))
        assertEquals(-1_000, BackingOffset.snap(-1_777, config))
        assertEquals(-6.5f, BackingOffset.snapGain(-6.4f, config))
        assertEquals(6f, BackingOffset.snapGain(9f, config))
    }

    @Test
    fun `the headphones' latency snaps to whole steps, from none to a second`() {
        assertEquals(215, BackingOffset.snapLatency(213, config))
        assertEquals(0, BackingOffset.snapLatency(-40, config))
        assertEquals(1_000, BackingOffset.snapLatency(1_400, config))
    }

    @Test
    fun `a shift moved by ear corrects the headphones' latency by the same amount`() {
        val take = TakeBacking(1, 1, offsetMs = 250, recordedOffsetMs = 210, gainDb = -6f, playedMs = 0, output = BackingOutput.BLUETOOTH, deviceName = "Buds", latencyMs = 200)
        assertEquals(240, BackingOffset.correctedLatencyMs(take, config))
        assertEquals(0, BackingOffset.correctedLatencyMs(take.copy(offsetMs = -300, latencyMs = 100), config))
        assertEquals(1_000, BackingOffset.correctedLatencyMs(take.copy(offsetMs = 1_000, latencyMs = 900), config))
    }

    @Test
    fun `a positive shift delays the backing against the violin`() {
        assertEquals(48_000 - 9_600, BackingOffset.backingSampleAt(48_000, offsetMs = 200, sampleRate = 48_000))
        assertEquals(-9_600, BackingOffset.backingSampleAt(0, offsetMs = 200, sampleRate = 48_000))
        assertEquals(4_410, BackingOffset.backingSampleAt(0, offsetMs = -100, sampleRate = 44_100))
    }

    @Test
    fun `latencies are remembered by name, the last used first, at most twenty`() {
        var latencies = HeadphoneLatencies.EMPTY
        repeat(25) { latencies = latencies.with("Buds $it", it, config) }
        assertEquals(20, latencies.entries.size)
        assertEquals("Buds 24", latencies.entries.first().first)
        assertNull(latencies.of("Buds 0"))
        latencies = latencies.with("Buds 10", 99, config)
        assertEquals("Buds 10" to 99, latencies.entries.first())
        assertEquals(20, latencies.entries.size)
    }

    @Test
    fun `the codec keeps names with anything but a line break, and reads garbage as nothing`() {
        val latencies = HeadphoneLatencies(listOf("WH-1000XM5 = mine" to 180, "Galaxy Buds2" to 240))
        assertEquals(latencies, HeadphoneLatencyCodec.decode(HeadphoneLatencyCodec.encode(latencies)))
        assertEquals(HeadphoneLatencies.EMPTY, HeadphoneLatencyCodec.decode(null))
        assertEquals(HeadphoneLatencies(listOf("ok" to 5)), HeadphoneLatencyCodec.decode("garbage\n=12\nok=5\nbad=x"))
    }
}
