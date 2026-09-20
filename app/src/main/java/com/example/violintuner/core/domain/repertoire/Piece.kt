package com.example.violintuner.core.domain.repertoire

import com.example.violintuner.core.domain.repertoire.scale.ScaleSpec
import kotlinx.coroutines.flow.Flow

/** Where a piece stands with its player (spec 3.15). */
enum class PieceStatus { READING, LEARNING, IN_REPERTOIRE }

/** The four sections every repertoire has (spec 3.22); the player's own are [PieceGroup]s. */
enum class PieceSection { PIECES, SCALES, ETUDES, STROKES }

/** A section of the player's own making: one level, no nesting. */
data class PieceGroup(val id: Long, val name: String, val createdAtEpochMs: Long)

/** Where an element of the repertoire lives: in one of the four built-in sections or in a group of the player's. */
sealed interface SectionRef {
    data class BuiltIn(val section: PieceSection) : SectionRef

    data class Custom(val groupId: Long) : SectionRef
}

/**
 * One element of the repertoire: what is played, with everything attached to it. A piece, an
 * étude, a bow stroke and a scale are all this — a section is a folder, not another entity; a
 * scale only differs in that its notes are known ([scale]) and drawn by the app.
 */
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
    /** The take its player marked as the best (spec 3.21); may point at a take that is gone — read it through [PieceStats.bestOf]. */
    val bestTakeId: Long? = null,
    /** The built-in section; [groupId] wins over it while that group exists. */
    val section: PieceSection = PieceSection.PIECES,
    val groupId: Long? = null,
    /** There for an element of «Гаммы» only, and always there. */
    val scale: ScaleSpec? = null,
    /** When the third step of the status was reached; null below it (spec 5.16). */
    val learnedAtEpochMs: Long? = null,
) {
    val learned: Boolean get() = status == PieceStatus.IN_REPERTOIRE
}

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
    val section: PieceSection = PieceSection.PIECES,
    val groupId: Long? = null,
    val scale: ScaleSpec? = null,
)

object PieceRules {
    /** Null when the draft cannot be saved: a piece without a title is not a piece. */
    fun clean(draft: PieceDraft, config: RepertoireConfig): PieceDraft? {
        val title = cut(draft.title, config.maxTitleLength)
        if (title.isEmpty()) return null
        // A scale lives in «Гаммы» and nowhere else, and nothing else lives there (spec 3.22).
        if ((draft.scale != null) != (draft.section == PieceSection.SCALES && draft.groupId == null)) return null
        return draft.copy(
            title = title,
            composer = cut(draft.composer, config.maxComposerLength),
            tempoBpm = draft.tempoBpm?.coerceIn(config.minTempoBpm, config.maxTempoBpm),
            // Notes keep their inner line breaks: they are a teacher's pencil marks, not a label.
            notes = draft.notes.trim().take(config.maxNotesLength).trimEnd(),
        )
    }

    fun draftOf(piece: Piece) =
        PieceDraft(piece.title, piece.composer, piece.key, piece.tempoBpm, piece.status, piece.notes, piece.section, piece.groupId, piece.scale)

    /** One step of the tempo control; an empty tempo starts from a walking pace. */
    fun stepTempo(current: Int?, by: Int, config: RepertoireConfig): Int =
        ((current ?: DEFAULT_TEMPO_BPM - by) + by).coerceIn(config.minTempoBpm, config.maxTempoBpm)

    /** Null when the name cannot be saved: a section without a name is not a section. */
    fun cleanGroupName(name: String, config: RepertoireConfig): String? = cut(name, config.maxGroupNameLength).takeIf { it.isNotEmpty() }

    /** Where the piece lives now: its group while it exists, its built-in section otherwise. */
    fun sectionOf(piece: Piece, groups: List<PieceGroup>): SectionRef =
        piece.groupId?.takeIf { id -> groups.any { it.id == id } }?.let { SectionRef.Custom(it) } ?: SectionRef.BuiltIn(piece.section)

    private fun cut(text: String, max: Int): String = text.trim().take(max).trimEnd()

    private const val DEFAULT_TEMPO_BPM = 96
}

interface RepertoireRepository {
    /** Every piece, in no promised order: the list sorts by activity, which involves the takes. */
    val pieces: Flow<List<Piece>>

    /** Every page of every piece, by piece and position: a repertoire is tens of rows, not thousands. */
    val pages: Flow<List<SheetPage>>

    /** The player's own sections, in no promised order. */
    val groups: Flow<List<PieceGroup>>

    suspend fun piece(id: Long): Piece?

    suspend fun add(draft: PieceDraft, nowEpochMs: Long): Long

    suspend fun update(id: Long, draft: PieceDraft, nowEpochMs: Long)

    suspend fun setStatus(id: Long, status: PieceStatus, nowEpochMs: Long)

    /** Marks [sessionId] as the best take of the piece — one at most, so the former mark goes; null clears it. Not an edit: the activity does not move. */
    suspend fun setBestTake(id: Long, sessionId: Long?)

    suspend fun addGroup(name: String, nowEpochMs: Long): Long

    suspend fun renameGroup(id: Long, name: String)

    /** Nothing is lost with a section: what it held moves to «Произведения». */
    suspend fun deleteGroup(id: Long)

    /** Removes the piece with its pages and their files; its takes stay as plain sessions. */
    suspend fun delete(id: Long)

    /** Appends a page whose files are already in place. */
    suspend fun addPage(pieceId: Long, fileName: String, thumbFileName: String, nowEpochMs: Long)

    suspend fun deletePage(pageId: Long, nowEpochMs: Long)

    /** Housekeeping at start: photo files no page points at (an import cut short). */
    suspend fun deleteOrphanFiles()
}
