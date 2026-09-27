package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.Zone
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class LiveReadoutTest {
    private val readout = LiveReadout(IntonationConfig())

    private fun frame(tMs: Long, rms: Double = 0.2) = PitchFrame.unpitched(tMs, clarity = 0.97, rms = rms)

    private fun active(midi: Int, cents: Double = 0.0, held: Boolean = false) =
        IntonationReading.Active(Note(midi), cents, Zone.NEAR, Direction.SHARP, holdProgress = 0.0, held = held)

    private fun shown(tMs: Long, reading: IntonationReading, rms: Double = 0.2) = readout.shownOf(frame(tMs, rms), reading)

    private fun sounding(tMs: Long, reading: IntonationReading, rms: Double = 0.2) =
        shown(tMs, reading, rms).signal as LiveSignal.Sounding

    @Test
    fun `silence and noise map one to one`() {
        assertEquals(LiveSignal.Silence, shown(0, IntonationReading.Silence).signal)
        assertEquals(LiveSignal.TooNoisy, shown(10, IntonationReading.TooNoisy).signal)
    }

    @Test
    fun `silence and noise carry an empty gauge`() {
        shown(0, active(69), rms = 0.3)
        assertEquals(LiveGauge(), shown(10, IntonationReading.Silence, rms = 0.3).gauge)
        assertEquals(LiveGauge(), shown(20, IntonationReading.TooNoisy, rms = 0.3).gauge)
    }

    @Test
    fun `an active reading keeps every field of the engine — the words in the signal and the numbers in the gauge`() {
        val reading = IntonationReading.Active(Note(78), 14.5, Zone.NEAR, Direction.SHARP, 0.3)
        val shown = shown(0, reading)
        val signal = shown.signal as LiveSignal.Sounding
        assertEquals(Note(78), signal.note)
        assertEquals(Zone.NEAR, signal.zone)
        assertEquals(Direction.SHARP, signal.direction)
        assertEquals(15, signal.displayCents)
        assertEquals(false, signal.holdComplete)
        assertEquals(14.5, shown.gauge.cents!!, 0.0)
        assertEquals(LiveReducer.glowTargetOf(Zone.NEAR, 0.3, IntonationConfig()), shown.gauge.glowTarget, 0f)
    }

    @Test
    fun `a note held to the full hold says so`() {
        val held = IntonationReading.Active(Note(69), 1.0, Zone.IN_TUNE, null, holdProgress = 1.0)
        val shown = shown(0, held)
        assertEquals(true, (shown.signal as LiveSignal.Sounding).holdComplete)
        assertEquals(1f, shown.gauge.glowTarget, 1e-6f)
    }

    @Test
    fun `the count moves on a note after silence and on a change of note — not while a note lasts`() {
        val first = sounding(0, active(69)).noteSerial
        assertEquals(first, sounding(10, active(69, cents = 3.0)).noteSerial)
        assertEquals(first, sounding(20, active(69, held = true)).noteSerial, "held through a pitch gap is the same note")

        val second = sounding(30, active(71)).noteSerial
        assertEquals(first + 1, second)

        shown(40, IntonationReading.Silence)
        assertEquals(second + 1, sounding(50, active(71)).noteSerial, "the same note again, after a rest")
    }

    @Test
    fun `the digits are calm while the cents move`() {
        assertEquals(10, sounding(0, active(69, cents = 10.0)).displayCents)
        val soon = shown(50, active(69, cents = 16.0))
        assertEquals(16.0, soon.gauge.cents!!, 0.0)
        assertEquals(10, (soon.signal as LiveSignal.Sounding).displayCents)
        assertEquals(16, sounding(200, active(69, cents = 16.0)).displayCents)
    }

    @Test
    fun `after silence the digits of the next note do not wait for their turn`() {
        sounding(0, active(69, cents = 10.0))
        shown(10, IntonationReading.Silence)
        assertEquals(-5, sounding(20, active(69, cents = -5.0)).displayCents)
    }

    @Test
    fun `the level follows the loudness of the frames — through silence too`() {
        val loud = shown(0, active(69), rms = 0.3).gauge.level
        assertTrue(loud > 0.9f)
        shown(10, IntonationReading.Silence, rms = 0.0005)
        val after = shown(20, active(69), rms = 0.3).gauge.level
        assertTrue(after > 0.85f, "one quiet frame barely dents it")
    }

    @Test
    fun `reset starts the level over and the count still moves`() {
        val before = sounding(0, active(69), rms = 0.3).noteSerial
        readout.reset()
        val shown = shown(10, active(69), rms = 0.001)
        assertTrue(shown.gauge.level < 0.2f)
        assertEquals(before + 1, (shown.signal as LiveSignal.Sounding).noteSerial)
    }
}
