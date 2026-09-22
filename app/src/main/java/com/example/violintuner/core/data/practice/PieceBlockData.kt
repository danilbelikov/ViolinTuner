package com.example.violintuner.core.data.practice

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.violintuner.core.data.repertoire.PieceEntity
import com.example.violintuner.core.domain.practice.PieceBlockRepository
import com.example.violintuner.core.domain.practice.SavedBlock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A block of a saved practice (spec 3.28, 5.21): time given to one element of the repertoire. A new
 * table, so it can afford a foreign key: an element deleted takes its blocks with it.
 */
@Entity(
    tableName = "piece_blocks",
    foreignKeys = [
        ForeignKey(entity = PieceEntity::class, parentColumns = ["id"], childColumns = ["pieceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pieceId")],
)
data class PieceBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pieceId: Long,
    /** The local date of the practice, as a string — like the practice entries. */
    val date: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val goalMs: Long,
    val done: Boolean,
    val paid: Boolean,
)

@Dao
abstract class PieceBlockDao {
    @Query("SELECT * FROM piece_blocks ORDER BY startedAtEpochMs, id")
    abstract fun observeAll(): Flow<List<PieceBlockEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM pieces WHERE id = :pieceId)")
    protected abstract suspend fun pieceExists(pieceId: Long): Boolean

    @Insert
    protected abstract suspend fun insert(block: PieceBlockEntity): Long

    /** Stores what still has its element, in one transaction; returns the rows stored, with their ids. */
    @Transaction
    open suspend fun insertWhereThePieceIs(blocks: List<PieceBlockEntity>): List<PieceBlockEntity> =
        blocks.filter { pieceExists(it.pieceId) }.map { it.copy(id = insert(it)) }
}

class RoomPieceBlockRepository @Inject constructor(private val dao: PieceBlockDao) : PieceBlockRepository {
    override val blocks: Flow<List<SavedBlock>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun add(blocks: List<SavedBlock>): List<SavedBlock> =
        if (blocks.isEmpty()) emptyList() else dao.insertWhereThePieceIs(blocks.map(::toEntity)).map(::toDomain)

    private fun toDomain(row: PieceBlockEntity) =
        SavedBlock(row.pieceId, LocalDate.parse(row.date), row.startedAtEpochMs, row.durationMs, row.goalMs, row.done, row.paid, row.id)

    private fun toEntity(block: SavedBlock) =
        PieceBlockEntity(block.id, block.pieceId, block.date.toString(), block.startedAtEpochMs, block.durationMs, block.goalMs, block.done, block.paid)
}
