package com.example.violintuner.core.data.repertoire

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One piece of the repertoire (spec 6). The key is three names or three nulls; enums are
 * stored by name, not by ordinal, so that reordering them in code cannot rewrite history.
 */
@Entity(tableName = "pieces")
data class PieceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val composer: String,
    val keyTonic: String?,
    val keyAccidental: String?,
    val keyMode: String?,
    val tempoBpm: Int?,
    val status: String,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    /** No foreign key: a take that is gone simply leaves a mark nobody matches (spec 5.15). */
    val bestTakeId: Long? = null,
)

/** One page of sheet music; goes with its piece through the cascade, its files are the repository's to remove. */
@Entity(
    tableName = "sheet_pages",
    foreignKeys = [
        ForeignKey(entity = PieceEntity::class, parentColumns = ["id"], childColumns = ["pieceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pieceId")],
)
data class SheetPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pieceId: Long,
    val position: Int,
    val fileName: String,
    val thumbFileName: String,
)
