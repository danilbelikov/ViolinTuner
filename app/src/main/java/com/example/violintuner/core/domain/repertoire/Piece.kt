package com.example.violintuner.core.domain.repertoire

import kotlinx.coroutines.flow.Flow

/** Where a piece stands with its player (spec 3.15). */
enum class PieceStatus { READING, LEARNING, IN_REPERTOIRE }

/** One piece of the repertoire: what is played, with everything attached to it. */
data class Piece(
    val id: Long = 0,
    val title: String,
    /** Empty when not given, like the notes. */
    val composer: String,
    val key: MusicalKey?,
    val tempoBpm: Int?,
    val status: PieceStatus,
    val notes: String,
    val createdAtEpochMs: Long,
    /** Edits of the fields and of the sheet pages alike. */
    val updatedAtEpochMs: Long,
)

/** One photographed page of sheet music. The files are named, not located: the storage knows the folder. */
data class SheetPage(
    val id: Long = 0,
    val pieceId: Long,
    /** Order within the piece, from zero; pages stand in the order they were added. */
    val position: Int,
    val fileName: String,
    val thumbFileName: String,
)

/** What the form holds: raw text as typed. [PieceRules.clean] turns it into something storable. */
data class PieceDraft(
    val title: String = "",
    val composer: String = "",
    val key: MusicalKey? = null,
    val tempoBpm: Int? = null,
    val status: PieceStatus = PieceStatus.READING,
    val notes: String = "",
)

object PieceRules {
    /** Null when the draft cannot be saved: a piece without a title is not a piece. */
    fun clean(draft: PieceDraft, config: RepertoireConfig): PieceDraft? {
        val title = cut(draft.title, config.maxTitleLength)
        if (title.isEmpty()) return null
        return draft.copy(
            title = title,
            composer = cut(draft.composer, config.maxComposerLength),
            tempoBpm = draft.tempoBpm?.coerceIn(config.minTempoBpm, config.maxTempoBpm),
            // Notes keep their inner line breaks: they are a teacher's pencil marks, not a label.
            notes = draft.notes.trim().take(config.maxNotesLength).trimEnd(),
        )
    }

    fun draftOf(piece: Piece) = PieceDraft(piece.title, piece.composer, piece.key, piece.tempoBpm, piece.status, piece.notes)

    /** One step of the tempo control; an empty tempo starts from a walking pace. */
    fun stepTempo(current: Int?, by: Int, config: RepertoireConfig): Int =
        ((current ?: DEFAULT_TEMPO_BPM - by) + by).coerceIn(config.minTempoBpm, config.maxTempoBpm)

    private fun cut(text: String, max: Int): String = text.trim().take(max).trimEnd()

    private const val DEFAULT_TEMPO_BPM = 96
}

interface RepertoireRepository {
    /** Every piece, in no promised order: the list sorts by activity, which involves the takes. */
    val pieces: Flow<List<Piece>>

    /** Every page of every piece, by piece and position: a repertoire is tens of rows, not thousands. */
    val pages: Flow<List<SheetPage>>

    suspend fun piece(id: Long): Piece?

    suspend fun add(draft: PieceDraft, nowEpochMs: Long): Long

    suspend fun update(id: Long, draft: PieceDraft, nowEpochMs: Long)

    suspend fun setStatus(id: Long, status: PieceStatus, nowEpochMs: Long)

    /** Removes the piece with its pages and their files; its takes stay as plain sessions. */
    suspend fun delete(id: Long)

    /** Appends a page whose files are already in place. */
    suspend fun addPage(pieceId: Long, fileName: String, thumbFileName: String, nowEpochMs: Long)

    suspend fun deletePage(pageId: Long, nowEpochMs: Long)

    /** Housekeeping at start: photo files no page points at (an import cut short). */
    suspend fun deleteOrphanFiles()
}
