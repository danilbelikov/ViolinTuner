package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStats
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SheetPage
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.session.RecordDays
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
                PieceCard(
                    id = piece.id,
                    title = piece.title,
                    composer = piece.composer,
                    keyName = piece.key?.germanName,
                    tempoBpm = piece.tempoBpm,
                    status = piece.status,
                    lastDate = takes.firstOrNull()?.let { RecordDays.dateOf(it, zone) },
                    lastDateOtherYear = takes.firstOrNull()?.let { RecordDays.dateOf(it, zone).year != today.year } ?: false,
                    takes = takes.size,
                    hasBest = PieceStats.bestOf(piece, takes) != null,
                    scale = piece.scale,
                    stroke = piece.groupId == null && piece.section == PieceSection.STROKES,
                    exercise = piece.groupId == null && piece.section != PieceSection.PIECES,
                    thumbPath = firstPages[piece.id]?.let { thumbPathOf(it.thumbFileName) },
                )
            }
        return RepertoireState(loading = false, totalCount = pieces.size, filter = filter, cards = cards)
    }

    fun loading(filter: PieceStatus?) = RepertoireState(loading = true, totalCount = 0, filter = filter, cards = emptyList())
}
