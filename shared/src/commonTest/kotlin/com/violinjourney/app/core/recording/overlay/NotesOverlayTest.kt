package com.violinjourney.app.core.recording.overlay

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionRibbon
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotesOverlayTest {
    private val intonation = IntonationConfig()
    private val config = NotesVideoConfig()

    /** [count] buckets of 50 ms of [midi] at [cents]; null midi is a pause. */
    private fun run(midi: Int?, count: Int, cents: Double = 0.0): List<SessionSample?> =
        List(count) { midi?.let { SessionSample(it, cents) } }

    private fun summary(
        id: Long = 1,
        pieceId: Long? = PIECE,
        startedAt: Long = 100_000,
        tolerance: Double = 8.0,
        score: Int = 80,
    ) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 60_000, a4Hz = 440.0, toleranceCents = tolerance,
        nearCents = 20.0, scorePercent = score, nearPercent = 0, offPercent = 0, maeCents = 0.0, biasCents = 0.0,
        previewZones = emptyList(), audioPath = "take.mp4", pieceId = pieceId, videoPath = "take.mp4",
    )

    private fun details(samples: List<SessionSample?>, summary: SessionSummary = summary()): SessionDetails =
        SessionDetails(summary, samples, SessionAnalyzer.analyze(samples, intonation.copy(toleranceCents = summary.toleranceCents)))

    private fun overlay(samples: List<SessionSample?>, summary: SessionSummary = summary(), recordings: List<SessionSummary> = emptyList()) =
        NotesOverlays.of(details(samples, summary), recordings, intonation, TITLE, HEADING, DATE, config)

    @Test
    fun `the notes are the segments of the analysis in the colour of their mean`() {
        val overlay = overlay(run(69, 10, 2.0) + run(71, 10, 15.0) + run(null, 2) + run(72, 10, -30.0) + run(74, 3, 0.0))
        // the last note is 150 ms: under the 200 ms of the analysis, so not on the lane either
        assertEquals(listOf(69, 71, 72), overlay.notes.map { it.midi })
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF), overlay.notes.map { it.zone })
        assertEquals(listOf(0L to 500L, 500L to 1_000L, 1_100L to 1_600L), overlay.notes.map { it.startMs to it.endMs })
        assertEquals("C5", overlay.notes[2].name)
    }

    @Test
    fun `the zones follow the tolerance of the recording — not the settings of now`() {
        val samples = run(69, 10, 10.0)
        assertEquals(Zone.NEAR, overlay(samples).notes.single().zone)
        assertEquals(Zone.IN_TUNE, overlay(samples, summary(tolerance = 12.0)).notes.single().zone)
    }

    @Test
    fun `the heights are the notes and a semitone of air — never fewer than eight semitones`() {
        assertEquals(66f to 74f, NotesOverlays.heights(listOf(69, 71), config))
        assertEquals(59f to 81f, NotesOverlays.heights(listOf(60, 80), config))
        assertEquals(65f to 73f, NotesOverlays.heights(listOf(69), config))
        // exactly eight is wide enough
        assertEquals(60f to 68f, NotesOverlays.heights(listOf(61, 67), config))
    }

    /** A at 0–500, a bow change of 200 ms, B at 700–1200, a pause of a second, C at 2200–2700. */
    private val phrases = run(69, 10) + run(null, 4) + run(71, 10) + run(null, 20) + run(72, 10)

    @Test
    fun `a change of bow keeps the note current — a longer pause does not`() {
        val overlay = overlay(phrases)
        assertNull(overlay.currentAt(-1))
        assertEquals(69, overlay.currentAt(0)?.midi)
        assertEquals(69, overlay.currentAt(650)?.midi)
        assertEquals(71, overlay.currentAt(700)?.midi)
        assertEquals(71, overlay.currentAt(1_450)?.midi)
        assertNull(overlay.currentAt(1_500))
        assertEquals(72, overlay.currentAt(2_200)?.midi)
        assertNull(overlay.currentAt(3_000))
    }

    @Test
    fun `the tag comes in at the start of a run and goes out after it — 150 ms each`() {
        val overlay = overlay(phrases)
        assertEquals(0f, overlay.tagAt(0)!!.alpha, EPSILON)
        assertEquals(0.5f, overlay.tagAt(75)!!.alpha, EPSILON)
        // the bow change is inside the run: the tag stays whole and names the note just played
        assertEquals(1f, overlay.tagAt(650)!!.alpha, EPSILON)
        assertEquals(69, overlay.tagAt(650)!!.note.midi)
        assertEquals(1f, overlay.tagAt(800)!!.alpha, EPSILON)
        assertEquals(1f, overlay.tagAt(1_500)!!.alpha, EPSILON)
        assertEquals(0.5f, overlay.tagAt(1_575)!!.alpha, EPSILON)
        assertNull(overlay.tagAt(1_650))
        // a run after the pause comes in again
        assertEquals(0f, overlay.tagAt(2_200)!!.alpha, EPSILON)
        assertEquals(72, overlay.tagAt(2_200)!!.note.midi)
    }

    @Test
    fun `a break of the hold itself keeps the run going — the tag does not blink`() {
        // A 0–500, a break of exactly 300 ms, B 800–1300: B is current at once, and the tag of the run stays whole
        val overlay = overlay(run(69, 10) + run(null, 6) + run(71, 10))
        assertEquals(1f, overlay.tagAt(799)!!.alpha, EPSILON)
        assertEquals(71, overlay.tagAt(800)!!.note.midi)
        assertEquals(1f, overlay.tagAt(800)!!.alpha, EPSILON)
    }

    @Test
    fun `a run that begins while the tag still fades goes on from its brightness`() {
        // A 0–500, a break of 350 ms, B 850–1350: A's tag fades from 800 and has a third gone at 850 — B takes it from there
        val overlay = overlay(run(69, 10) + run(null, 7) + run(71, 10))
        assertEquals(2f / 3, overlay.tagAt(849)!!.alpha, 0.01f)
        assertEquals(71, overlay.tagAt(850)!!.note.midi)
        assertEquals(2f / 3, overlay.tagAt(850)!!.alpha, 0.01f)
        assertEquals(1f, overlay.tagAt(900)!!.alpha, EPSILON)
    }

    @Test
    fun `the notes of a span are those that touch it`() {
        val overlay = overlay(phrases)
        assertEquals(listOf(69, 71), overlay.notesBetween(400, 800).map { it.midi })
        assertEquals(listOf(71), overlay.notesBetween(1_100, 2_200).map { it.midi })
        assertEquals(emptyList(), overlay.notesBetween(1_200, 2_200).map { it.midi })
        assertEquals(listOf(69, 71, 72), overlay.notesBetween(-5_000, 10_000).map { it.midi })
    }

    @Test
    fun `the best note sounded a second in all and was in tune at least half of it`() {
        val overlay = overlay(
            run(69, 30, 2.0) + // 1.5 s in tune
                run(null, 1) + run(71, 10, 0.0) + // 0.5 s in tune: too short
                run(null, 1) + run(72, 16, 0.0) + run(72, 24, 15.0), // 2 s, 40 % in tune: under half
        )
        assertEquals(69, overlay.bestMidi)
    }

    @Test
    fun `a tie of the best goes to the longer note — then to the lower one`() {
        assertEquals(74, overlay(run(69, 30, 0.0) + run(null, 1) + run(74, 40, 0.0)).bestMidi)
        assertEquals(69, overlay(run(74, 30, 0.0) + run(null, 1) + run(69, 30, 0.0)).bestMidi)
        // two segments of one note count together
        assertEquals(71, overlay(run(71, 12, 0.0) + run(null, 1) + run(69, 20, 0.0) + run(null, 1) + run(71, 12, 0.0)).bestMidi)
    }

    @Test
    fun `no note qualifies — no best note`() {
        assertNull(overlay(run(69, 30, 15.0)).bestMidi)
    }

    @Test
    fun `the best note is judged by the tolerance of the recording`() {
        // 10 cents is near by the ±8 of the settings of now, in tune by the ±12 the recording was made with
        assertEquals(69, overlay(run(69, 30, 10.0), summary(tolerance = 12.0)).bestMidi)
    }

    @Test
    fun `what drifts is the first problem note in the colour of its mean`() {
        val overlay = overlay(run(69, 10, 12.0) + run(71, 10, -30.0) + run(72, 10, 0.0))
        assertEquals(OverlayDrift(71, -30.0, Zone.OFF), overlay.drift)
        assertNull(overlay(run(69, 10, 3.0)).drift)
    }

    @Test
    fun `the previous take is the latest earlier one of the piece with the same tolerance`() {
        val samples = run(69, 10)
        val recordings = listOf(
            summary(id = 2, startedAt = 50_000, score = 70),
            summary(id = 3, startedAt = 90_000, score = 99, tolerance = 12.0), // other tolerance
            summary(id = 4, startedAt = 95_000, score = 60, pieceId = 8), // other piece
            summary(id = 5, startedAt = 120_000, score = 95), // later
            summary(id = 6, startedAt = 30_000, score = 50), // earlier, but not the latest
        )
        assertEquals(OverlayPrevious(70, 10), overlay(samples, summary(score = 80), recordings).previous)
        // worse or the same: no gain is said
        assertEquals(OverlayPrevious(70, null), overlay(samples, summary(score = 70), recordings).previous)
        assertEquals(OverlayPrevious(70, null), overlay(samples, summary(score = 60), recordings).previous)
        // a recording without a piece — a video of an event — has none
        assertNull(overlay(samples, summary(pieceId = null), recordings).previous)
        assertNull(overlay(samples, summary(), recordings.filter { it.id == 5L }).previous)
    }

    @Test
    fun `the summary keeps the score — the tolerance — the title and the ribbon of the recording`() {
        val samples = run(69, 10, 2.0) + run(69, 4, 10.0)
        val overlay = overlay(samples, summary(score = 71, tolerance = 12.0))
        assertEquals(71, overlay.scorePercent)
        assertEquals(12, overlay.toleranceCents)
        assertEquals(TITLE, overlay.title)
        assertEquals(HEADING, overlay.heading)
        assertEquals(DATE, overlay.date)
        // the ribbon by the ±12 of the recording: one piece in tune — by the ±8 of now it would be two
        assertEquals(SessionRibbon.of(samples, intonation.copy(toleranceCents = 12.0)), overlay.ribbon)
        assertEquals(1, overlay.ribbon.size)
    }

    @Test
    fun `the tag backs its colour with a shape — up for high — down for low — the dot in tune`() {
        val overlay = overlay(run(69, 10, 24.0) + run(71, 10, -14.0) + run(72, 10, 3.0) + run(74, 10, -3.0))
        assertEquals(listOf(OverlaySign.UP, OverlaySign.DOWN, OverlaySign.DOT, OverlaySign.DOT), overlay.notes.map { it.sign })
        assertEquals(listOf("+24", "−14", "+3", "−3"), overlay.notes.map { it.centsText })
    }

    @Test
    fun `the zone is of the number the tag says — the colour and the number never disagree at an edge`() {
        // ±8: a mean of +8.3 says «+8», which is in tune — and so is its colour; +8.6 says «+9», near
        assertEquals(Zone.IN_TUNE, NotesOverlays.zoneOf(8.3, intonation))
        assertEquals(Zone.NEAR, NotesOverlays.zoneOf(8.6, intonation))
        assertEquals(Zone.IN_TUNE, NotesOverlays.zoneOf(-8.4, intonation))
        // the edge of near: +20.4 says «+20», near; +20.6 says «+21», off
        assertEquals(Zone.NEAR, NotesOverlays.zoneOf(20.4, intonation))
        assertEquals(Zone.OFF, NotesOverlays.zoneOf(20.6, intonation))
        val note = overlay(run(69, 10, 8.3)).notes.single()
        assertEquals(Zone.IN_TUNE, note.zone)
        assertEquals(OverlaySign.DOT, note.sign)
        assertEquals("+8", note.centsText)
    }

    @Test
    fun `a note carries the mean of its segment — the number of its tag`() {
        val overlay = overlay(run(69, 6, 10.0) + run(69, 4, 15.0) + run(71, 10, -7.0))
        assertEquals(12.0, overlay.notes[0].meanCents, 1e-9)
        assertEquals(-7.0, overlay.notes[1].meanCents, 1e-9)
        assertEquals(Zone.NEAR, overlay.tagAt(300)!!.note.zone)
        assertEquals(12.0, overlay.tagAt(300)!!.note.meanCents, 1e-9)
    }

    @Test
    fun `the opening comes in from 300 ms — stays — and is gone at 4 s`() {
        val overlay = overlay(phrases)
        val end = 60_000L
        assertNull(overlay.openingAt(0, end))
        assertNull(overlay.openingAt(299, end))
        assertEquals(0f, overlay.openingAt(300, end)!!.alpha, EPSILON)
        assertEquals(1.5f, overlay.openingAt(300, end)!!.raiseU, EPSILON)
        // half the time in is three quarters of the way: slowing towards its place
        assertEquals(0.75f, overlay.openingAt(600, end)!!.alpha, EPSILON)
        assertEquals(1.5f * 0.25f, overlay.openingAt(600, end)!!.raiseU, EPSILON)
        assertEquals(OverlayOpening(1f, 0f), overlay.openingAt(900, end))
        assertEquals(OverlayOpening(1f, 0f), overlay.openingAt(1_500, end))
        assertEquals(OverlayOpening(1f, 0f), overlay.openingAt(3_399, end))
        // going out by 1 − p², in place
        assertEquals(0.75f, overlay.openingAt(3_700, end)!!.alpha, EPSILON)
        assertEquals(0f, overlay.openingAt(3_700, end)!!.raiseU, EPSILON)
        assertNull(overlay.openingAt(4_000, end))
        assertNull(overlay.openingAt(10_000, end))
    }

    @Test
    fun `a short video ends the opening a second before its own end — the stay is cut first`() {
        val overlay = overlay(phrases)
        // 3 s: over by 2 s, coming in and going out whole
        assertEquals(1f, overlay.openingAt(900, 3_000)!!.alpha, EPSILON)
        assertEquals(1f, overlay.openingAt(1_399, 3_000)!!.alpha, EPSILON)
        assertEquals(0.75f, overlay.openingAt(1_700, 3_000)!!.alpha, EPSILON)
        assertNull(overlay.openingAt(2_000, 3_000))
        // 5 s is not short: the whole opening
        assertEquals(1f, overlay.openingAt(3_399, 5_000)!!.alpha, EPSILON)
        assertNull(overlay.openingAt(4_000, 5_000))
    }

    @Test
    fun `a video of 2 s halves what is left between coming in and going out — under 2 s there is none`() {
        val overlay = overlay(phrases)
        // over by 1 s: 700 ms from 300, 350 in and 350 out
        assertEquals(0.75f, overlay.openingAt(475, 2_000)!!.alpha, EPSILON)
        assertEquals(1f, overlay.openingAt(650, 2_000)!!.alpha, EPSILON)
        assertEquals(0.75f, overlay.openingAt(825, 2_000)!!.alpha, EPSILON)
        assertNull(overlay.openingAt(1_000, 2_000))
        assertNull(overlay.openingAt(600, 1_999))
    }

    private companion object {
        const val PIECE = 7L
        const val TITLE = "Менуэт соль мажор · 3 октября"
        const val HEADING = "Менуэт соль мажор"
        const val DATE = "3 октября"
        const val EPSILON = 1e-4f
    }
}
