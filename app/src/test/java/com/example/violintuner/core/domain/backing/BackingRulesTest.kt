package com.example.violintuner.core.domain.backing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shift, the calibration and the remembered latencies of spec 5.25. */
class BackingRulesTest {
    private val config = BackingConfig()
    private val calibration = config.calibration
    private val ms = 1_000_000L

    @Test
    fun `the shift is the backing's start minus the take's on one clock, plus what the headphones add`() {
        // the backing left the output 35 ms after the take's first sample came in; wireless headphones add 180
        assertEquals(215, BackingOffset.offsetMs(backingStartNanos = 1_035 * ms, recordStartNanos = 1_000 * ms, headphoneLatencyMs = 180, config = config))
        // no clocks (the fake source of the emulator): the headphones alone
        assertEquals(180, BackingOffset.offsetMs(null, 1_000 * ms, 180, config))
        // never past the slider's ends
        assertEquals(500, BackingOffset.offsetMs(2_000 * ms, 1_000 * ms, 0, config))
        assertEquals(-500, BackingOffset.offsetMs(0, 1_000 * ms, 0, config))
    }

    @Test
    fun `wired headphones add nothing unless calibrated, wireless ones a guess until they are`() {
        val latencies = HeadphoneLatencies.EMPTY.with("Pixel Buds Pro", 212, config)
        assertEquals(212, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, "Pixel Buds Pro"), latencies, config))
        assertEquals(config.uncalibratedBluetoothMs, BackingOffset.latencyMs(AudioRoute(BackingOutput.BLUETOOTH, "Other"), latencies, config))
        assertEquals(0, BackingOffset.latencyMs(AudioRoute(BackingOutput.WIRED, "USB-C to 3.5mm"), latencies, config))
        assertEquals(212, BackingOffset.latencyMs(AudioRoute(BackingOutput.WIRED, "Pixel Buds Pro"), latencies, config))
    }

    @Test
    fun `the shift snaps to whole steps and the level to half decibels`() {
        assertEquals(215, BackingOffset.snap(213, config))
        assertEquals(-500, BackingOffset.snap(-777, config))
        assertEquals(-6.5f, BackingOffset.snapGain(-6.4f, config))
        assertEquals(6f, BackingOffset.snapGain(9f, config))
    }

    @Test
    fun `a shift moved by ear corrects the headphones' latency by the same amount`() {
        val take = TakeBacking(1, 1, offsetMs = 250, recordedOffsetMs = 210, gainDb = -6f, playedMs = 0, output = BackingOutput.BLUETOOTH, deviceName = "Buds")
        assertEquals(240, BackingOffset.correctedLatencyMs(take, currentLatencyMs = 200))
        assertEquals(0, BackingOffset.correctedLatencyMs(take.copy(offsetMs = -300), currentLatencyMs = 100))
    }

    @Test
    fun `a positive shift delays the backing against the violin`() {
        assertEquals(48_000 - 9_600, BackingOffset.backingSampleAt(48_000, offsetMs = 200, sampleRate = 48_000))
        assertEquals(-9_600, BackingOffset.backingSampleAt(0, offsetMs = 200, sampleRate = 48_000))
        assertEquals(4_410, BackingOffset.backingSampleAt(0, offsetMs = -100, sampleRate = 44_100))
    }

    private fun clicks() = Calibration.clickTimesNanos(firstClickNanos = 10_000 * ms, calibration)

    @Test
    fun `a steady hand gives the median of note minus click, the lead-in is not measured`() {
        val clicks = clicks()
        assertEquals(calibration.leadInClicks + calibration.clicks, clicks.size)
        assertEquals(calibration.beatMs * ms, clicks[1] - clicks[0])
        val lags = listOf(205, 212, 210, 220, 208, 214, 211, 209)
        // notes on the lead-in clicks too — they must not count
        val onsets = listOf(clicks[0] + 100 * ms, clicks[1] + 90 * ms) + clicks.drop(2).zip(lags) { c, l -> c + l * ms }
        val result = Calibration.measure(clicks, onsets, calibration) as CalibrationResult.Measured
        assertEquals(211, result.latencyMs)
        assertEquals(8, result.hits)
        assertEquals(8, result.of)
    }

    @Test
    fun `one wild note does not spoil a steady hand, a scattered one is not trusted`() {
        val clicks = clicks().drop(2)
        val steady = listOf(200, 205, 198, 202, 201, 199, 203, 400)
        assertTrue(Calibration.measure(clicks(), clicks.zip(steady) { c, l -> c + l * ms }, calibration) is CalibrationResult.Measured)
        val scattered = listOf(20, 300, 90, 400, 150, 250, 60, 350)
        assertTrue(Calibration.measure(clicks(), clicks.zip(scattered) { c, l -> c + l * ms }, calibration) is CalibrationResult.Failed)
    }

    @Test
    fun `too few notes on the beat fail, notes out of the window do not count`() {
        val clicks = clicks().drop(2)
        val onsets = clicks.take(4).map { it + 200 * ms } + clicks.drop(4).map { it + 500 * ms } // between two clicks, in the window of neither
        val result = Calibration.measure(clicks(), onsets, calibration)
        assertEquals(CalibrationResult.Failed(hits = 4, of = 8), result)
        assertEquals(listOf(true, true, true, true, false, false, false, false), Calibration.answered(clicks(), onsets, calibration))
    }

    @Test
    fun `a note counts once, for the click it answers`() {
        val clicks = clicks().drop(2)
        // one note between two clicks cannot answer both
        val onsets = listOf(clicks[0] + 400 * ms)
        val result = Calibration.measure(clicks(), onsets, calibration) as CalibrationResult.Failed
        assertEquals(1, result.hits)
    }

    @Test
    fun `an onset is a rise of the level, and the next one waits for the level to settle`() {
        val detector = OnsetDetector(calibration)
        var t = 0L
        fun frame(db: Double) = detector.add(t, db).also { t += 10 * ms }
        repeat(5) { assertFalse(frame(-60.0)) }
        assertTrue(frame(-30.0))
        assertFalse(frame(-29.0)) // the same note sustains
        repeat(5) { frame(-60.0) }
        assertTrue(frame(-35.0))
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
