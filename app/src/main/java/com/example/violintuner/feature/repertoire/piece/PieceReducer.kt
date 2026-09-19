package com.example.violintuner.feature.repertoire.piece

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.Piece
import com.example.violintuner.core.domain.repertoire.PieceStats
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.SheetPage
import com.example.violintuner.core.domain.session.RecordingProgress
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.feature.history.HistoryReducer
import java.time.LocalDate
import java.time.ZoneId

/** A piece and its pages → the piece screen (spec 3.15). Pure: file paths come from outside. */
object PieceReducer {
    fun stateOf(
        piece: Piece,
        pages: List<SheetPage>,
        importing: Int,
        statusMenuOpen: Boolean,
        config: RepertoireConfig,
        takes: List<TakeItem> = emptyList(),
        progress: TakeProgress? = null,
        thumbPathOf: (fileName: String) -> String?,
    ) = PieceState(
        loading = false,
        header = PieceHeader(piece.title, piece.composer, piece.key?.germanName, piece.tempoBpm, piece.status),
        pages = pagesOf(piece.id, pages).mapIndexed { index, page -> SheetTile(page.id, index + 1, thumbPathOf(page.thumbFileName)) },
        importing = importing,
        notes = piece.notes,
        takes = takes,
        progress = progress,
        statusMenuOpen = statusMenuOpen,
        notesCollapsedLines = config.notesCollapsedLines,
    )

    fun loading(config: RepertoireConfig) = PieceState(
        loading = true, header = null, pages = emptyList(), importing = 0, notes = "", takes = emptyList(), progress = null,
        statusMenuOpen = false,
        notesCollapsedLines = config.notesCollapsedLines,
    )

    /** The takes of the piece as cards, newest first, with the best one and the fresh one marked (spec 3.15). */
    fun takesOf(
        pieceId: Long,
        sessions: List<SessionSummary>,
        newTakeId: Long?,
        today: LocalDate,
        zone: ZoneId,
        config: IntonationConfig,
    ): List<TakeItem> {
        val takes = PieceStats.takesOf(pieceId, sessions)
        val bestId = PieceStats.best(takes)?.id
        return takes.map { TakeItem(HistoryReducer.cardOf(it, today, zone, config), best = it.id == bestId, isNew = it.id == newTakeId) }
    }

    fun progressOf(pieceId: Long, sessions: List<SessionSummary>, config: RepertoireConfig): TakeProgress? =
        PieceStats.progress(PieceStats.takesOf(pieceId, sessions), config)?.let { TakeProgress(it.lastScore, it.bestScore, it.scores) }

    /** What the recording row shows for one frame of the chain. */
    fun takeStateOf(shown: BlindShown, recording: RecordingProgress?, requested: Boolean, micPermission: Boolean?, bars: Int): TakeState =
        if (!requested) {
            TakeState.idle(micPermission, bars)
        } else {
            TakeState(
                recording = true,
                elapsedSeconds = (recording?.elapsedMs ?: 0L) / MS_PER_SECOND,
                levels = shown.levels,
                problem = shown.problem,
                micPermission = micPermission,
            )
        }

    private const val MS_PER_SECOND = 1_000L

    /** Pages of one piece in their order. Numbers on screen follow this order, not the stored positions, which have gaps after a removal. */
    fun pagesOf(pieceId: Long, pages: List<SheetPage>): List<SheetPage> =
        pages.filter { it.pieceId == pieceId }.sortedWith(compareBy<SheetPage> { it.position }.thenBy { it.id })
}
