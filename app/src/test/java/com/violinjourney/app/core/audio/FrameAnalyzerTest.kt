package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.SignalSynth
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.IntonationReading.Active
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAnalyzerTest {
    private val config = IntonationConfig()
    private val rate = 48_000

    private fun analyze(signal: FloatArray): List<PitchFrame> {
        val analyzer = FrameAnalyzer(MpmDetector(config), config, rate)
        val pcm = SignalSynth.toPcm16(signal)
        return pcm.toList().chunked(config.hopSizeSamples)
            .mapNotNull { analyzer.push(it.toShortArray()) }
    }

    private fun readings(signal: FloatArray, mode: TargetMode = TargetMode.Chromatic): List<IntonationReading> {
        val engine = IntonationEngine(config)
        return analyze(signal).map { engine.process(it, mode) }
    }

    @Test
    fun `frames start once the window is full and follow the sample clock`() {
        val frames = analyze(SignalSynth.tone(440.0, rate, config.hopSizeSamples * 10))
        assertEquals(7, frames.size) // hops 4..10 complete a 2048-sample window
        assertEquals(2_048L * 1_000 / rate, frames.first().tMs)
        assertEquals(5_120L * 1_000 / rate, frames.last().tMs)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.tMs > a.tMs })
    }

    @Test
    fun `partial hops are accepted`() {
        val analyzer = FrameAnalyzer(MpmDetector(config), config, rate)
        val pcm = SignalSynth.toPcm16(SignalSynth.tone(440.0, rate, 4_096))
        assertNull(analyzer.push(pcm.copyOfRange(0, 512), count = 100))
        assertNull(analyzer.push(ShortArray(512), count = 0))
    }

    @Test
    fun `rms is relative to full scale`() {
        val fullScaleSine = SignalSynth.tone(440.0, rate, 8_192)
        assertEquals(0.707, analyze(fullScaleSine).last().rms, 0.005)
        assertEquals(0.0, analyze(FloatArray(8_192)).last().rms, 0.0)
    }

    @Test
    fun `pitched frame carries note and cents, silent frame carries nulls`() {
        val frame = analyze(SignalSynth.tone(SignalSynth.hz(69, 12.0), rate, 8_192, SignalSynth.VIOLIN)).last()
        assertEquals(69, frame.midi)
        assertEquals(12.0, frame.cents!!, 0.5)
        assertNull(analyze(FloatArray(8_192)).last().freqHz)
    }

    @Test
    fun `violin A4 through the whole chain is in tune`() {
        val last = readings(SignalSynth.tone(SignalSynth.hz(69, 3.0), rate, rate, SignalSynth.VIOLIN)).last() as Active
        assertEquals("A4", last.note.name)
        assertEquals(Zone.IN_TUNE, last.zone)
        assertEquals(3.0, last.cents, 0.5)
        assertTrue(last.holdProgress > 0.3)
    }

    @Test
    fun `flat G string with strong even harmonics reads as G3, flat, in tuning mode`() {
        val signal = SignalSynth.tone(SignalSynth.hz(55, -60.0), rate, rate, SignalSynth.STRONG_EVEN_HARMONICS)
        val last = readings(signal, TargetMode.Strings()).last() as Active
        assertEquals("G3", last.note.name)
        assertEquals(-60.0, last.cents, 1.0)
        assertEquals(Zone.OFF, last.zone)
        assertEquals(Direction.FLAT, last.direction)
    }

    @Test
    fun `loud noise ends up as too noisy, quiet noise as silence`() {
        val loud = SignalSynth.whiteNoise(rate * 2, seed = 7, amplitude = 0.3)
        assertEquals(IntonationReading.TooNoisy, readings(loud).last())
        val quiet = SignalSynth.whiteNoise(rate * 2, seed = 7, amplitude = 0.002)
        assertEquals(IntonationReading.Silence, readings(quiet).last())
    }

    @Test
    fun `note change is picked up within a quarter of a second`() {
        val a4 = SignalSynth.tone(SignalSynth.hz(69), rate, rate / 2, SignalSynth.VIOLIN)
        val b4 = SignalSynth.tone(SignalSynth.hz(71), rate, rate / 2, SignalSynth.VIOLIN)
        val frames = analyze(a4 + b4)
        val engine = IntonationEngine(config)
        val firstB4 = frames.firstOrNull { (engine.process(it, TargetMode.Chromatic) as? Active)?.note?.name == "B4" }
        assertTrue("B4 shown at ${firstB4?.tMs} ms", firstB4 != null && firstB4.tMs < 750)
    }
}
