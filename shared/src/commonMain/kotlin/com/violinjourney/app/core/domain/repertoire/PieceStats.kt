package com.violinjourney.app.core.domain.repertoire

import com.violinjourney.app.core.domain.session.SessionSummary

/** What a piece's takes say about it (spec 3.15, 5.9). Pure functions of the sessions. */
object PieceStats {
    data class Progress(
        val lastScore: Int,
        /** The highest score among the takes — a number, not the player's «лучший» mark. */
        val maxScore: Int,
        /** Scores in the order the takes were recorded: the chart. */
        val scores: List<Int>,
    )

    /** Takes of [pieceId], newest first. */
    fun takesOf(pieceId: Long, sessions: List<SessionSummary>): List<SessionSummary> =
        sessions.filter { it.pieceId == pieceId }
            .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })

    /**
     * The take the player marked as the best (spec 3.21), or null: the mark is the player's, never
     * the score's, and a mark left behind by a deleted or unlinked take reads as no mark at all.
     */
    fun bestOf(piece: Piece, takes: List<SessionSummary>): SessionSummary? =
        piece.bestTakeId?.let { id -> takes.firstOrNull { it.id == id && it.pieceId == piece.id } }

    /** The order of the list on the piece screen: the marked take first, the rest newest first. */
    fun listed(piece: Piece, takes: List<SessionSummary>): List<SessionSummary> {
        val best = bestOf(piece, takes) ?: return takes
        return listOf(best) + takes.filter { it.id != best.id }
    }

    /** Null until there are enough takes to speak of progress. */
    fun progress(takes: List<SessionSummary>, config: RepertoireConfig): Progress? {
        if (takes.size < config.progressFromTakes) return null
        val inOrder = takes.sortedWith(compareBy<SessionSummary> { it.startedAtEpochMs }.thenBy { it.id })
        return Progress(
            lastScore = inOrder.last().scorePercent,
            maxScore = inOrder.maxOf { it.scorePercent },
            scores = inOrder.map { it.scorePercent },
        )
    }

    /** The latest of: the last take, the last edit (pages included), the creation. */
    fun lastActivity(piece: Piece, takes: List<SessionSummary>): Long =
        maxOf(piece.updatedAtEpochMs, piece.createdAtEpochMs, takes.maxOfOrNull { it.startedAtEpochMs } ?: 0L)
}
