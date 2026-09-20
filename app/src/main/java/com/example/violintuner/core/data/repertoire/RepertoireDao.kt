package com.example.violintuner.core.data.repertoire

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RepertoireDao {
    @Query("SELECT * FROM pieces ORDER BY id")
    abstract fun observePieces(): Flow<List<PieceEntity>>

    @Query("SELECT * FROM piece_groups ORDER BY id")
    abstract fun observeGroups(): Flow<List<PieceGroupEntity>>

    @Query("SELECT * FROM sheet_pages ORDER BY pieceId, position, id")
    abstract fun observePages(): Flow<List<SheetPageEntity>>

    @Query("SELECT * FROM pieces WHERE id = :id")
    abstract suspend fun piece(id: Long): PieceEntity?

    @Query("SELECT * FROM sheet_pages WHERE pieceId = :pieceId ORDER BY position, id")
    abstract suspend fun pagesOf(pieceId: Long): List<SheetPageEntity>

    @Query("SELECT * FROM sheet_pages WHERE id = :id")
    abstract suspend fun page(id: Long): SheetPageEntity?

    @Query("SELECT fileName FROM sheet_pages UNION ALL SELECT thumbFileName FROM sheet_pages")
    abstract suspend fun fileNames(): List<String>

    @Insert
    abstract suspend fun insertPiece(piece: PieceEntity): Long

    @Query(
        "UPDATE pieces SET title = :title, composer = :composer, keyTonic = :keyTonic, keyAccidental = :keyAccidental, " +
            "keyMode = :keyMode, tempoBpm = :tempoBpm, status = :status, notes = :notes, section = :section, groupId = :groupId, " +
            "scaleKind = :scaleKind, scaleOctaves = :scaleOctaves, updatedAtEpochMs = :now WHERE id = :id",
    )
    protected abstract suspend fun updatePieceRow(
        id: Long, title: String, composer: String, keyTonic: String?, keyAccidental: String?, keyMode: String?,
        tempoBpm: Int?, status: String, notes: String, section: String, groupId: Long?, scaleKind: String?, scaleOctaves: Int?, now: Long,
    )

    @Query("UPDATE pieces SET status = :status, updatedAtEpochMs = :now WHERE id = :id")
    protected abstract suspend fun setStatusRow(id: Long, status: String, now: Long)

    // The day the third step was reached (spec 5.16): set when it is reached, cleared when it is left, left alone otherwise.
    @Query(
        "UPDATE pieces SET learnedAtEpochMs = CASE WHEN status != :learned THEN NULL " +
            "WHEN learnedAtEpochMs IS NULL THEN :now ELSE learnedAtEpochMs END WHERE id = :id",
    )
    protected abstract suspend fun settleLearned(id: Long, learned: String, now: Long)

    @Transaction
    open suspend fun updatePiece(entity: PieceEntity, learned: String, now: Long) {
        updatePieceRow(
            entity.id, entity.title, entity.composer, entity.keyTonic, entity.keyAccidental, entity.keyMode, entity.tempoBpm,
            entity.status, entity.notes, entity.section, entity.groupId, entity.scaleKind, entity.scaleOctaves, now,
        )
        settleLearned(entity.id, learned, now)
    }

    @Transaction
    open suspend fun setStatus(id: Long, status: String, learned: String, now: Long) {
        setStatusRow(id, status, now)
        settleLearned(id, learned, now)
    }

    @Insert
    abstract suspend fun insertGroup(group: PieceGroupEntity): Long

    @Query("UPDATE piece_groups SET name = :name WHERE id = :id")
    abstract suspend fun renameGroup(id: Long, name: String)

    @Query("DELETE FROM piece_groups WHERE id = :id")
    protected abstract suspend fun deleteGroupRow(id: Long)

    @Query("UPDATE pieces SET groupId = NULL, section = :home WHERE groupId = :id")
    protected abstract suspend fun moveOutOfGroup(id: Long, home: String)

    /** Nothing is lost with a section: what it held moves to [home] in the same transaction. */
    @Transaction
    open suspend fun deleteGroup(id: Long, home: String) {
        moveOutOfGroup(id, home)
        deleteGroupRow(id)
    }

    @Query("UPDATE pieces SET bestTakeId = :sessionId WHERE id = :id")
    abstract suspend fun setBestTake(id: Long, sessionId: Long?)

    @Query("UPDATE pieces SET updatedAtEpochMs = :now WHERE id = :id")
    protected abstract suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM pieces WHERE id = :id")
    protected abstract suspend fun deletePieceRow(id: Long)

    // No foreign key from sessions to pieces (see MIGRATION_3_4): unlinking is done here, in
    // the same transaction as the removal, so a take never points at a piece that is gone.
    @Query("UPDATE sessions SET pieceId = NULL WHERE pieceId = :pieceId")
    protected abstract suspend fun unlinkSessions(pieceId: Long)

    @Insert
    protected abstract suspend fun insertPage(page: SheetPageEntity): Long

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM sheet_pages WHERE pieceId = :pieceId")
    protected abstract suspend fun nextPosition(pieceId: Long): Int

    @Query("DELETE FROM sheet_pages WHERE id = :id")
    protected abstract suspend fun deletePageRow(id: Long)

    /** The takes stay as plain sessions; the pages go with the piece through the cascade. */
    @Transaction
    open suspend fun deletePiece(id: Long) {
        unlinkSessions(id)
        deletePieceRow(id)
    }

    /** False when the piece is gone meanwhile: the caller then owns two files nobody points at. */
    @Transaction
    open suspend fun appendPage(pieceId: Long, fileName: String, thumbFileName: String, now: Long): Boolean {
        if (piece(pieceId) == null) return false
        insertPage(SheetPageEntity(pieceId = pieceId, position = nextPosition(pieceId), fileName = fileName, thumbFileName = thumbFileName))
        touch(pieceId, now)
        return true
    }

    @Transaction
    open suspend fun removePage(page: SheetPageEntity, now: Long) {
        deletePageRow(page.id)
        touch(page.pieceId, now)
    }
}
