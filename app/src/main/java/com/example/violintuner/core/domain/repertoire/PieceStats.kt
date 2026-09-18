package com.example.violintuner.core.domain.repertoire

import com.example.violintuner.core.domain.session.SessionSummary

/** What a piece's takes say about it (spec 3.15, 5.9). Pure functions of the sessions. */
object PieceStats {
    data class Progress(
        val lastScore: Int,
        val bestScore: Int,
        /** Scores in the order the takes were recorded: the chart. */
        val scores: List<Int>,
    )

    /** Takes of [pieceId], newest first — the order of the list on the piece screen. */
    fun takesOf(pieceId: Long, sessions: List<SessionSummary>): List<SessionSummary> =
        sessions.filter { it.pieceId == pieceId }
            .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })

    /** The take with the highest score; of equals, the later one. Null without takes. */
    fun best(takes: List<SessionSummary>): SessionSummary? =
        takes.maxWithOrNull(compareBy<SessionSummary> { it.scorePercent }.thenBy { it.startedAtEpochMs }.thenBy { it.id })

    /** Null until there are enough takes to speak of progress. */
    fun progress(takes: List<SessionSummary>, config: RepertoireConfig): Progress? {
        if (takes.size < config.progressFromTakes) return null
        val inOrder = takes.sortedWith(compareBy<SessionSummary> { it.startedAtEpochMs }.thenBy { it.id })
        return Progress(
            lastScore = inOrder.last().scorePercent,
            bestScore = inOrder.maxOf { it.scorePercent },
            scores = inOrder.map { it.scorePercent },
        )
    }

    /** The latest of: the last take, the last edit (pages included), the creation. */
    fun lastActivity(piece: Piece, takes: List<SessionSummary>): Long =
        maxOf(piece.updatedAtEpochMs, piece.createdAtEpochMs, takes.maxOfOrNull { it.startedAtEpochMs } ?: 0L)
}
