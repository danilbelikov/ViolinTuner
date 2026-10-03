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
        NotesOverlays.of(details(samples, summary), recordings, intonation, TITLE, config)

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
        // the ribbon by the ±12 of the recording: one piece in tune — by the ±8 of now it would be two
        assertEquals(SessionRibbon.of(samples, intonation.copy(toleranceCents = 12.0)), overlay.ribbon)
        assertEquals(1, overlay.ribbon.size)
    }

    private companion object {
        const val PIECE = 7L
        const val TITLE = "Менуэт соль мажор · 3 октября"
        const val EPSILON = 1e-4f
    }
}
