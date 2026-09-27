package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.Zone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun `a steady note is recorded with its start time — duration — config and perfect score`() {
        val result = record(FakeScenario.IN_TUNE, frames = 300).finish() as RecordingResult.Recorded
        val session = result.session
        assertEquals(1_000, session.startedAtEpochMs)
        assertEquals(3_471, session.durationMs) // frame 299 at 44.1 kHz / 512
        assertEquals(config, session.config)
        assertEquals(100, session.metrics.scorePercent)
        assertEquals(2.0, session.metrics.biasCents, 0.2)
        assertEquals(listOf(Zone.IN_TUNE), session.previewZones)
        assertEquals(70, session.samples.size)
        assertTrue(session.samples.take(2).all { it == null }, "the first 100 ms are the note lock")
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
    fun `progress reports elapsed time and one bar per note — scaled to at least 20 s`() {
        val progress = record(FakeScenario.IN_TUNE, frames = 431).progress() // 5.0 s
        assertEquals(4_992, progress.elapsedMs)
        val bar = progress.ribbon.pieces.single()
        assertEquals(Zone.IN_TUNE, bar.zone)
        assertEquals(97f / 400f, progress.ribbon.share(bar), 0.01f) // 4.85 s of note on a 20 s bar
    }

    @Test
    fun `demo loop paints bars of every zone and never overflows the bar`() {
        val progress = record(FakeScenario.DEMO, frames = 2_600).progress() // 30 s
        val ribbon = progress.ribbon
        assertTrue(ribbon.pieces.map { it.zone }.toSet().containsAll(listOf(Zone.IN_TUNE, Zone.OFF)))
        assertTrue(ribbon.pieces.sumOf { ribbon.share(it).toDouble() } <= 1.0)
    }

    /** One run of the ribbon: [buckets] of [midi] at [cents] (0 in tune, 12 near, 30 off). */
    private class Run(val midi: Int, val cents: Double, val buckets: Int)

    /** Plays [runs] one after another, a reading every 10 ms, five to a bucket; returns the time reached. */
    private fun SessionRecorder.play(runs: List<Run>, fromMs: Long = 0): Long {
        var t = fromMs
        for (run in runs) repeat(run.buckets * 5) {
            add(t, IntonationReading.Active(Note(run.midi), run.cents, Zone.IN_TUNE, null, holdProgress = 0.0))
            t += 10
        }
        return t
    }

    @Test
    fun `under the cap every run keeps its own piece`() {
        val recorder = SessionRecorder(config, startedAtEpochMs = 0)
        val t = recorder.play(listOf(Run(69, 0.0, 3), Run(69, 12.0, 2), Run(71, 30.0, 4)))
        recorder.add(t, IntonationReading.Silence) // closes the last bucket
        val ribbon = recorder.progress().ribbon
        assertEquals(listOf(RecordingBar(3, Zone.IN_TUNE), RecordingBar(2, Zone.NEAR), RecordingBar(4, Zone.OFF)), ribbon.pieces)
        assertEquals(400f, ribbon.span) // 20 s of 50 ms buckets
    }

    @Test
    fun `a long take never holds more pieces than the cap and keeps its width`() {
        val capped = SessionRecorder(config.copy(recordingBarMaxPieces = 8), startedAtEpochMs = 0)
        val plain = SessionRecorder(config, startedAtEpochMs = 0)
        var t = 0L
        repeat(600) { index ->
            // vibrato over the edge of the zone: a new run every two buckets
            val runs = listOf(Run(69 + index % 2, if (index % 3 == 0) 12.0 else 0.0, 2))
            capped.play(runs, t)
            t = plain.play(runs, t)
            val pieces = capped.progress().ribbon.pieces
            assertTrue(pieces.size <= 8 + 1, "${pieces.size} pieces after $index runs")
        }
        val cappedRibbon = capped.progress().ribbon
        val plainRibbon = plain.progress().ribbon
        assertTrue(plainRibbon.pieces.size >= 599, "the plain one keeps every run")
        assertEquals(plainRibbon.pieces.sumOf { it.buckets }, cappedRibbon.pieces.sumOf { it.buckets })
        assertEquals(plainRibbon.span, cappedRibbon.span)
    }

    @Test
    fun `a joined piece takes the zone with more buckets and a tie takes the worse one`() {
        val recorder = SessionRecorder(config.copy(recordingBarMaxPieces = 4), startedAtEpochMs = 0)
        // in tune 3 + 1, near 2 + 2, off 1: all five far narrower than the join allows on a 20 s ribbon
        val t = recorder.play(
            listOf(Run(60, 0.0, 3), Run(61, 12.0, 2), Run(62, 12.0, 2), Run(63, 30.0, 1), Run(64, 0.0, 1), Run(65, 0.0, 3)),
        )
        val pieces = recorder.progress().ribbon.pieces
        assertEquals(RecordingBar(9, Zone.NEAR), pieces.first())
        assertEquals(2, pieces.size)

        // the note being played goes on growing its own piece after the join
        recorder.play(listOf(Run(65, 0.0, 4)), t)
        val after = recorder.progress().ribbon.pieces
        assertEquals(2, after.size)
        assertEquals(Zone.IN_TUNE, after.last().zone)
        assertTrue(after.last().buckets > pieces.last().buckets)
    }

    @Test
    fun `a ribbon handed out stays as it was while the take goes on`() {
        val recorder = SessionRecorder(config.copy(recordingBarMaxPieces = 4), startedAtEpochMs = 0)
        val t = recorder.play(listOf(Run(60, 0.0, 2), Run(61, 12.0, 2), Run(62, 0.0, 2)))
        val early = recorder.progress().ribbon
        val before = early.pieces.toList()
        recorder.play(List(20) { Run(63 + it % 2, 30.0, 1) }, t)
        recorder.progress()
        assertEquals(before, early.pieces.toList())
    }

    @Test
    fun `limit is reached at the configured duration`() {
        val short = config.copy(maxSessionMs = 3_000)
        assertFalse(record(FakeScenario.IN_TUNE, frames = 250, config = short).limitReached)
        assertTrue(record(FakeScenario.IN_TUNE, frames = 260, config = short).limitReached)
    }
}
