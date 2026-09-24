package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SessionAnalyzerTest {
    private val config = IntonationConfig()

    /** [count] buckets of [midi] at [cents]; null midi is a pause. */
    private fun run(midi: Int?, count: Int, cents: Double = 0.0): List<SessionSample?> =
        List(count) { midi?.let { SessionSample(it, cents) } }

    @Test
    fun `segments are runs of one note — closed by a pause or a change`() {
        val samples = run(69, 10, 2.0) + run(null, 3) + run(69, 6, -4.0) + run(71, 8, 12.0)
        val segments = SessionAnalyzer.analyze(samples, config).segments
        assertEquals(listOf(69, 69, 71), segments.map { it.midi })
        assertEquals(listOf(0L, 650L, 950L), segments.map { it.startMs })
        assertEquals(listOf(500L, 950L, 1_350L), segments.map { it.endMs })
        assertEquals(listOf(2.0, -4.0, 12.0), segments.map { it.meanCents })
    }

    @Test
    fun `notes shorter than 200 ms are dropped and do not count`() {
        val samples = run(69, 10, 0.0) + run(72, 3, 45.0) + run(69, 10, 0.0)
        val analysis = SessionAnalyzer.analyze(samples, config)
        assertEquals(listOf(69, 69), analysis.segments.map { it.midi })
        assertEquals(100, analysis.metrics!!.scorePercent)
        assertEquals(4, SessionAnalyzer.analyze(run(72, 4), config).segments.single().sampleCount)
    }

    @Test
    fun `min — max and contour come from the samples of the segment`() {
        val samples = run(null, 2) + listOf(3.0, -6.0, 9.0, 0.0).map { SessionSample(69, it) }
        val segment = SessionAnalyzer.analyze(samples, config).segments.single()
        assertEquals(-6.0, segment.minCents, 0.0)
        assertEquals(9.0, segment.maxCents, 0.0)
        assertEquals(1.5, segment.meanCents, 1e-9)
        assertEquals(listOf(3.0, -6.0, 9.0, 0.0), SessionAnalyzer.contour(samples, segment))
    }

    @Test
    fun `score is the share of in-tune samples and the three shares sum to 100`() {
        // 8 in tune (boundary 8.0 included), 8 near (boundary 20.0 included), 4 off
        val samples = run(69, 7, 1.0) + run(69, 1, 8.0) + run(69, 7, -15.0) + run(69, 1, 20.0) + run(69, 4, 30.0)
        val metrics = SessionAnalyzer.analyze(samples, config).metrics!!
        assertEquals(40, metrics.scorePercent)
        assertEquals(40, metrics.nearPercent)
        assertEquals(20, metrics.offPercent)
    }

    @Test
    fun `rounding never pushes the sum over 100`() {
        val samples = run(69, 5, 0.0) + run(69, 5, 15.0) + run(69, 1, 40.0) // 45.45 / 45.45 / 9.09
        val metrics = SessionAnalyzer.analyze(samples, config).metrics!!
        assertEquals(100, metrics.scorePercent + metrics.nearPercent + metrics.offPercent)
        assertEquals(listOf(45, 45, 10), listOf(metrics.scorePercent, metrics.nearPercent, metrics.offPercent))
    }

    @Test
    fun `tolerance of the session decides the score`() {
        val samples = run(69, 10, 10.0)
        assertEquals(0, SessionAnalyzer.analyze(samples, config).metrics!!.scorePercent)
        assertEquals(100, SessionAnalyzer.analyze(samples, config.copy(toleranceCents = 12.0)).metrics!!.scorePercent)
    }

    @Test
    fun `mean error ignores the sign — bias keeps it`() {
        val samples = run(69, 10, -6.0) + run(71, 10, 2.0)
        val metrics = SessionAnalyzer.analyze(samples, config).metrics!!
        assertEquals(4.0, metrics.maeCents, 1e-9)
        assertEquals(-2.0, metrics.biasCents, 1e-9)
    }

    @Test
    fun `problem notes are off on average — worst first — three at most`() {
        val samples = run(78, 10, -18.0) + run(null, 1) + run(73, 10, -9.0) + run(null, 1) +
            run(69, 10, 3.0) + run(null, 1) + run(62, 10, 11.0) + run(null, 1) + run(64, 10, 25.0) +
            run(null, 1) + run(78, 10, -22.0) // F#5 again: mean of segment means is -20
        val problems = SessionAnalyzer.analyze(samples, config).metrics!!.problemNotes
        assertEquals(listOf(64, 78, 62), problems.map { it.midi })
        assertEquals(-20.0, problems[1].meanCents, 1e-9)
    }

    @Test
    fun `a note that wobbles around the target is not a problem`() {
        val wobble = List(20) { SessionSample(69, if (it % 2 == 0) 15.0 else -15.0) }
        val metrics = SessionAnalyzer.analyze(wobble, config).metrics!!
        assertEquals(emptyList<ProblemNote>(), metrics.problemNotes)
        assertEquals(0, metrics.scorePercent)
    }

    @Test
    fun `per string score follows the first position heuristic`() {
        val samples = run(57, 10, 0.0) + run(null, 1) + run(64, 5, 0.0) + run(64, 5, 30.0) // A3 on G, E4 on D
        val perString = SessionAnalyzer.analyze(samples, config).metrics!!.perString
        assertEquals(100, perString[ViolinString.G3])
        assertEquals(50, perString[ViolinString.D4])
        assertNull(perString[ViolinString.A4])
        assertNull(perString[ViolinString.E5])
    }

    @Test
    fun `preview is the zones of the first eight notes`() {
        val samples = (0 until 10).flatMap { run(60 + it, 5, if (it == 1) 12.0 else if (it == 2) 40.0 else 0.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        val preview = SessionAnalyzer.previewZones(analysis.segments, config)
        assertEquals(8, preview.size)
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF, Zone.IN_TUNE), preview.take(4))
    }

    @Test
    fun `a session without a countable note has no metrics`() {
        assertNull(SessionAnalyzer.analyze(run(null, 100), config).metrics)
        assertNull(SessionAnalyzer.analyze(run(69, 3), config).metrics)
        assertNull(SessionAnalyzer.analyze(emptyList(), config).metrics)
    }
}
