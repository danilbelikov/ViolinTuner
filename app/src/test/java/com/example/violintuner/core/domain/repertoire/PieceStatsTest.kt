package com.example.violintuner.core.domain.repertoire

import com.example.violintuner.core.domain.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieceStatsTest {
    private val config = RepertoireConfig()

    private fun take(id: Long, startedAt: Long, score: Int, pieceId: Long? = 1) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 120_000, a4Hz = 440.0, toleranceCents = 8.0,
        nearCents = 20.0, scorePercent = score, nearPercent = 0, offPercent = 0, maeCents = 0.0, biasCents = 0.0,
        previewZones = emptyList(), audioPath = null, pieceId = pieceId,
    )

    private val piece = Piece(1, "Менуэт", "", null, null, PieceStatus.LEARNING, "", createdAtEpochMs = 100, updatedAtEpochMs = 500)

    @Test
    fun `takes are the sessions of the piece, newest first`() {
        val sessions = listOf(take(1, 1_000, 58), take(2, 3_000, 71, pieceId = 2), take(3, 2_000, 64), take(4, 9_000, 90, pieceId = null))
        assertEquals(listOf(3L, 1L), PieceStats.takesOf(1, sessions).map { it.id })
    }

    @Test
    fun `the best take has the highest score, and of equals the later one`() {
        assertNull(PieceStats.best(emptyList()))
        val takes = listOf(take(1, 1_000, 82), take(2, 2_000, 78), take(3, 3_000, 82))
        assertEquals(3L, PieceStats.best(takes)!!.id)
    }

    @Test
    fun `progress needs two takes and runs in the order of recording`() {
        assertNull(PieceStats.progress(listOf(take(1, 1_000, 58)), config))
        val takes = listOf(take(3, 3_000, 71), take(1, 1_000, 58), take(2, 2_000, 88))
        val progress = PieceStats.progress(takes, config)!!
        assertEquals(listOf(58, 88, 71), progress.scores)
        assertEquals(71, progress.lastScore)
        assertEquals(88, progress.bestScore)
    }

    @Test
    fun `the last activity is the latest of a take, an edit and the creation`() {
        assertEquals(500L, PieceStats.lastActivity(piece, emptyList()))
        assertEquals(9_000L, PieceStats.lastActivity(piece, listOf(take(1, 9_000, 60), take(2, 300, 60))))
        assertEquals(500L, PieceStats.lastActivity(piece, listOf(take(1, 300, 60))))
        assertEquals(100L, PieceStats.lastActivity(piece.copy(updatedAtEpochMs = 0), emptyList()))
    }
}
