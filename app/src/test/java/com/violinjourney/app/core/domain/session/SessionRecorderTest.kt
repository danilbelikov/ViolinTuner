package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {
    private val config = IntonationConfig()

    /** Records [frames] frames of [scenario] through the real engine, as the view model does. */
    private fun record(scenario: FakeScenario, frames: Long, config: IntonationConfig = this.config): SessionRecorder {
        val source = FakePitchSource(scenario)
        val engine = IntonationEngine(config)
        val recorder = SessionRecorder(config, startedAtEpochMs = 1_000)
        for (index in 0 until frames) {
            val frame = source.frameAt(index, config)
            recorder.add(frame.tMs, engine.process(frame, TargetMode.Chromatic))
        }
        return recorder
    }

    @Test
    fun `under two seconds is too short`() {
        assertEquals(RecordingResult.TooShort, record(FakeScenario.IN_TUNE, frames = 170).finish()) // 1.96 s
    }

    @Test
    fun `silence and noise give no notes`() {
        assertEquals(RecordingResult.NoNotes, record(FakeScenario.SILENCE, frames = 300).finish())
        assertEquals(RecordingResult.NoNotes, record(FakeScenario.NOISE, frames = 300).finish())
    }

    @Test
    fun `a steady note is recorded with its start time, duration, config and perfect score`() {
        val result = record(FakeScenario.IN_TUNE, frames = 300).finish() as RecordingResult.Recorded
        val session = result.session
        assertEquals(1_000, session.startedAtEpochMs)
        assertEquals(3_471, session.durationMs) // frame 299 at 44.1 kHz / 512
        assertEquals(config, session.config)
        assertEquals(100, session.metrics.scorePercent)
        assertEquals(2.0, session.metrics.biasCents, 0.2)
        assertEquals(listOf(Zone.IN_TUNE), session.previewZones)
        assertEquals(70, session.samples.size)
        assertTrue("the first 100 ms are the note lock", session.samples.take(2).all { it == null })
    }

    @Test
    fun `drifting sharp lowers the score and shows as bias`() {
        val session = (record(FakeScenario.DRIFT_SHARP, frames = 345).finish() as RecordingResult.Recorded).session
        assertTrue(session.metrics.scorePercent in 10..30) // in tune for the first 0.7 of 4 s
        assertTrue(session.metrics.offPercent > 40)
        assertTrue(session.metrics.biasCents > 15)
        assertEquals(listOf(69), session.metrics.problemNotes.map { it.midi })
    }

    @Test
    fun `progress reports elapsed time and one bar per note, scaled to at least 20 s`() {
        val progress = record(FakeScenario.IN_TUNE, frames = 431).progress() // 5.0 s
        assertEquals(4_992, progress.elapsedMs)
        val bar = progress.bars.single()
        assertEquals(Zone.IN_TUNE, bar.zone)
        assertEquals(97f / 400f, bar.fraction, 0.01f) // 4.85 s of note on a 20 s bar
    }

    @Test
    fun `demo loop paints bars of every zone and never overflows the bar`() {
        val progress = record(FakeScenario.DEMO, frames = 2_600).progress() // 30 s
        assertTrue(progress.bars.map { it.zone }.toSet().containsAll(listOf(Zone.IN_TUNE, Zone.OFF)))
        assertTrue(progress.bars.sumOf { it.fraction.toDouble() } <= 1.0)
    }

    @Test
    fun `limit is reached at the configured duration`() {
        val short = config.copy(maxSessionMs = 3_000)
        assertFalse(record(FakeScenario.IN_TUNE, frames = 250, config = short).limitReached)
        assertTrue(record(FakeScenario.IN_TUNE, frames = 260, config = short).limitReached)
    }
}
