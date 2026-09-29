package com.violinjourney.app.feature.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.session.StringFinger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the recording of R5 takes from a stored recording (spec 3.36.5): the sheet a row of «Что уходит» opens — the segment of the
 * note with the largest mean, the longer of two equal ones, the first of two alike; how many times a note sounded; whether it is a
 * video; the border of «рядом» it was recorded with.
 */
class SessionContentMapperTest {
    private val config = IntonationConfig()

    private fun segment(midi: Int, startMs: Long, endMs: Long, meanCents: Double) = RollSegment(
        note = Note(midi), startMs = startMs, endMs = endMs, meanCents = meanCents, minCents = meanCents, maxCents = meanCents,
        zone = Zone.OFF, steady = true, contour = emptyList(), position = StringFinger.of(midi),
    )

    @Test
    fun `the worst place of a note is its segment with the largest mean whatever its sign`() {
        val segments = listOf(
            segment(78, 0, 1_000, -12.0),
            segment(69, 1_000, 2_000, 40.0),
            segment(78, 2_000, 3_000, 25.0),
            segment(78, 3_000, 4_000, -24.0),
        )
        assertEquals(2, SessionContentMapper.worstSegmentOf(Note(78), segments))
        assertEquals(1, SessionContentMapper.worstSegmentOf(Note(69), segments), "another note is not counted in")
    }

    @Test
    fun `of two places alike the longer is the worse and of two of one length the first`() {
        val longer = listOf(segment(78, 0, 1_000, -25.0), segment(78, 1_000, 2_500, 25.0), segment(78, 2_500, 3_000, -25.0))
        assertEquals(1, SessionContentMapper.worstSegmentOf(Note(78), longer))
        val alike = listOf(segment(78, 0, 1_000, -25.0), segment(69, 1_000, 2_000, -30.0), segment(78, 2_000, 3_000, -25.0))
        assertEquals(0, SessionContentMapper.worstSegmentOf(Note(78), alike))
    }

    @Test
    fun `a note that did not sound has no place`() {
        assertNull(SessionContentMapper.worstSegmentOf(Note(80), listOf(segment(78, 0, 1_000, -25.0))))
        assertNull(SessionContentMapper.worstSegmentOf(Note(78), emptyList()))
    }

    private fun details(samples: List<SessionSample?>, videoPath: String? = null, nearCents: Double = config.nearCents): SessionDetails {
        val analysis = SessionAnalyzer.analyze(samples, config.copy(nearCents = nearCents))
        val metrics = analysis.metrics!!
        val summary = SessionSummary(
            id = 1, title = null, startedAtEpochMs = 0, durationMs = samples.size * config.sessionBucketMs, a4Hz = 440.0,
            toleranceCents = config.toleranceCents, nearCents = nearCents, scorePercent = metrics.scorePercent,
            nearPercent = metrics.nearPercent, offPercent = metrics.offPercent, maeCents = metrics.maeCents, biasCents = metrics.biasCents,
            previewZones = emptyList(), audioPath = "take.m4a", videoPath = videoPath,
        )
        return SessionDetails(summary, samples, analysis)
    }

    /** F#5, A4, F#5, F#5 — each a second long, a gap between them. */
    private val drifting = List(20) { SessionSample(78, -12.0) } + listOf(null) + List(20) { SessionSample(69, 1.0) } + listOf(null) +
        List(20) { SessionSample(78, -25.0) } + listOf(null) + List(20) { SessionSample(78, -18.0) }

    @Test
    fun `each segment knows how many times its note sounded in the recording`() {
        val content = SessionContentMapper.contentOf(details(drifting), config)
        assertEquals(listOf("F#5", "A4", "F#5", "F#5"), content.segments.map { it.note.name })
        assertEquals(listOf(3, 1, 3, 3), content.segments.map { it.sameNoteCount })
    }

    @Test
    fun `a video take is one by the file it was shot into`() {
        assertTrue(SessionContentMapper.contentOf(details(drifting, videoPath = "take.mp4"), config).hasVideo)
        assertFalse(SessionContentMapper.contentOf(details(drifting), config).hasVideo)
    }

    private fun swing(half: Double, steady: Boolean) = segment(78, 0, 1_000, 0.0).copy(minCents = -half, maxCents = half, steady = steady)

    @Test
    fun `the swing of a wandering note is said over the border the advice names`() {
        // 20.3 is over 20 — «Размах больше 20 ц»: not «±20» beside it
        assertEquals(21, SessionContentMapper.halfRangeShown(swing(20.3, steady = false), nearCents = 20))
        // a wide swing is said as it is
        assertEquals(25, SessionContentMapper.halfRangeShown(swing(25.2, steady = false), nearCents = 20))
    }

    @Test
    fun `the swing of a steady note keeps its rounding`() {
        assertEquals(20, SessionContentMapper.halfRangeShown(swing(19.8, steady = true), nearCents = 20))
        assertEquals(6, SessionContentMapper.halfRangeShown(swing(6.2, steady = true), nearCents = 20))
    }

    @Test
    fun `the border of «рядом» is the one the recording was made with`() {
        assertEquals(20, SessionContentMapper.contentOf(details(drifting), config).nearCents)
        assertEquals(24, SessionContentMapper.contentOf(details(drifting, nearCents = 24.0), config).nearCents)
    }
}
