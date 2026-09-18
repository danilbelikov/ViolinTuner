package com.example.violintuner.feature.repertoire

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.Piece
import com.example.violintuner.core.domain.repertoire.PieceStats
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.SheetPage
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.feature.history.HistoryReducer
import java.time.LocalDate
import java.time.ZoneId

/** Pieces, pages and sessions → the repertoire list (spec 3.15). Pure: "today", the zone and file paths come from outside. */
object RepertoireReducer {
    fun stateOf(
        pieces: List<Piece>,
        pages: List<SheetPage>,
        sessions: List<SessionSummary>,
        filter: PieceStatus?,
        today: LocalDate,
        zone: ZoneId,
        config: IntonationConfig,
        thumbPathOf: (fileName: String) -> String?,
    ): RepertoireState {
        val firstPages = pages.groupBy { it.pieceId }.mapValues { (_, own) -> own.minBy { it.position } }
        val cards = pieces
            .map { piece -> piece to PieceStats.takesOf(piece.id, sessions) }
            .sortedWith(
                compareByDescending<Pair<Piece, List<SessionSummary>>> { (piece, takes) -> PieceStats.lastActivity(piece, takes) }
                    .thenByDescending { (piece, _) -> piece.id },
            )
            .filter { (piece, _) -> filter == null || piece.status == filter }
            .map { (piece, takes) ->
                // The card of the latest take already knows the color of a score and the word for a day.
                val last = takes.firstOrNull()?.let { HistoryReducer.cardOf(it, today, zone, config) }
                PieceCard(
                    id = piece.id,
                    title = piece.title,
                    composer = piece.composer,
                    keyName = piece.key?.germanName,
                    tempoBpm = piece.tempoBpm,
                    status = piece.status,
                    lastScore = last?.scorePercent,
                    lastScoreZone = last?.scoreZone,
                    lastDay = last?.day,
                    takes = takes.size,
                    thumbPath = firstPages[piece.id]?.let { thumbPathOf(it.thumbFileName) },
                )
            }
        return RepertoireState(loading = false, totalCount = pieces.size, filter = filter, cards = cards)
    }

    fun loading(filter: PieceStatus?) = RepertoireState(loading = true, totalCount = 0, filter = filter, cards = emptyList())
}
