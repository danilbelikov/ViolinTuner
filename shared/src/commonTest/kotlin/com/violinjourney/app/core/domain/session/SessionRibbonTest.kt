package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionRibbonTest {
    private val config = IntonationConfig()

    private fun run(midi: Int?, count: Int, cents: Double = 0.0): List<SessionSample?> =
        List(count) { midi?.let { SessionSample(it, cents) } }

    @Test
    fun `a piece is a run of one note in one zone and a pause takes no room`() {
        val samples = run(69, 3, 2.0) + run(69, 2, 12.0) + run(null, 4) + run(69, 2, 12.0) + run(71, 3, 30.0) + run(72, 1, 30.0)
        assertEquals(
            listOf(
                RecordingBar(3, Zone.IN_TUNE),
                RecordingBar(2, Zone.NEAR),
                RecordingBar(2, Zone.NEAR),
                RecordingBar(3, Zone.OFF),
                RecordingBar(1, Zone.OFF),
            ),
            SessionRibbon.of(samples, config),
        )
    }

    @Test
    fun `the tolerance of the recording decides the zones`() {
        val samples = run(69, 4, 10.0)
        assertEquals(listOf(RecordingBar(4, Zone.NEAR)), SessionRibbon.of(samples, config))
        assertEquals(listOf(RecordingBar(4, Zone.IN_TUNE)), SessionRibbon.of(samples, config.copy(toleranceCents = 12.0)))
    }

    @Test
    fun `no notes — no pieces`() {
        assertEquals(emptyList(), SessionRibbon.of(run(null, 10), config))
    }

    @Test
    fun `a stored take ends with the ribbon its recording drew`() {
        // a drifting note through the real engine: the runs change zone on the way, as on Live
        val source = FakePitchSource(FakeScenario.DRIFT_SHARP)
        val engine = IntonationEngine(config)
        val recorder = SessionRecorder(config, startedAtEpochMs = 0)
        for (index in 0 until 600L) {
            val frame = source.frameAt(index, config)
            recorder.add(frame.tMs, engine.process(frame, TargetMode.Chromatic))
        }
        val drawn = recorder.progress().ribbon.pieces.toList()
        val stored = (recorder.finish() as RecordingResult.Recorded).session.samples
        assertTrue(drawn.map { it.zone }.toSet().size > 1, "the scenario has to cross a zone for the test to mean anything")
        // the stored samples hold the bucket still running as well; the ribbon is made of the closed ones
        assertEquals(drawn, SessionRibbon.of(stored.dropLast(1), config))
    }
}
