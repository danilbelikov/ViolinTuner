package com.example.violintuner.feature.repertoire.piece

import com.example.violintuner.core.domain.repertoire.Piece
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.SheetPage

/** A piece and its pages → the piece screen (spec 3.15). Pure: file paths come from outside. */
object PieceReducer {
    fun stateOf(
        piece: Piece,
        pages: List<SheetPage>,
        importing: Int,
        statusMenuOpen: Boolean,
        config: RepertoireConfig,
        thumbPathOf: (fileName: String) -> String?,
    ) = PieceState(
        loading = false,
        header = PieceHeader(piece.title, piece.composer, piece.key?.germanName, piece.tempoBpm, piece.status),
        pages = pagesOf(piece.id, pages).mapIndexed { index, page -> SheetTile(page.id, index + 1, thumbPathOf(page.thumbFileName)) },
        importing = importing,
        notes = piece.notes,
        statusMenuOpen = statusMenuOpen,
        notesCollapsedLines = config.notesCollapsedLines,
    )

    fun loading(config: RepertoireConfig) = PieceState(
        loading = true, header = null, pages = emptyList(), importing = 0, notes = "", statusMenuOpen = false,
        notesCollapsedLines = config.notesCollapsedLines,
    )

    /** Pages of one piece in their order. Numbers on screen follow this order, not the stored positions, which have gaps after a removal. */
    fun pagesOf(pieceId: Long, pages: List<SheetPage>): List<SheetPage> =
        pages.filter { it.pieceId == pieceId }.sortedWith(compareBy<SheetPage> { it.position }.thenBy { it.id })
}
