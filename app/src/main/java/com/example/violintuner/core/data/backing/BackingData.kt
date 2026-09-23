package com.example.violintuner.core.data.backing

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.violintuner.core.data.repertoire.PieceEntity
import com.example.violintuner.core.data.session.SessionEntity
import com.example.violintuner.core.domain.backing.Backing
import com.example.violintuner.core.domain.backing.BackingFiles
import com.example.violintuner.core.domain.backing.BackingOutput
import com.example.violintuner.core.domain.backing.BackingRepository
import com.example.violintuner.core.domain.backing.PieceBacking
import com.example.violintuner.core.domain.backing.TakeBacking
import com.example.violintuner.core.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** One accompaniment file (spec 3.32). No foreign keys point here: a backing goes when nothing refers to it any more. */
@Entity(tableName = "backings")
data class BackingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val title: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val sizeBytes: Long,
    val addedAtEpochMs: Long,
)

/** The backing of a piece and the chip «С минусовкой»; goes with the piece. */
@Entity(
    tableName = "piece_backings",
    foreignKeys = [ForeignKey(entity = PieceEntity::class, parentColumns = ["id"], childColumns = ["pieceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("backingId")],
)
data class PieceBackingEntity(
    @PrimaryKey val pieceId: Long,
    val backingId: Long,
    val enabled: Boolean,
)

/** The backing a take was made under, and how it is mixed; goes with the take. */
@Entity(
    tableName = "take_backings",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("backingId")],
)
data class TakeBackingEntity(
    @PrimaryKey val sessionId: Long,
    val backingId: Long,
    val offsetMs: Int,
    val recordedOffsetMs: Int,
    val gainDb: Float,
    val playedMs: Long,
    /** [BackingOutput] by name. */
    val output: String,
    val deviceName: String?,
)

@Dao
interface BackingDao {
    @Query("SELECT * FROM backings ORDER BY id")
    fun backings(): Flow<List<BackingEntity>>

    @Query("SELECT * FROM piece_backings")
    fun pieceBackings(): Flow<List<PieceBackingEntity>>

    @Query("SELECT * FROM take_backings")
    fun takeBackings(): Flow<List<TakeBackingEntity>>

    @Query("SELECT * FROM backings WHERE id = :id")
    suspend fun backing(id: Long): BackingEntity?

    @Insert
    suspend fun insert(backing: BackingEntity): Long

    @Upsert
    suspend fun upsertPiece(row: PieceBackingEntity)

    @Query("DELETE FROM piece_backings WHERE pieceId = :pieceId")
    suspend fun deletePiece(pieceId: Long)

    @Query("UPDATE piece_backings SET enabled = :enabled WHERE pieceId = :pieceId")
    suspend fun setEnabled(pieceId: Long, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTake(row: TakeBackingEntity)

    @Query("UPDATE take_backings SET offsetMs = :offsetMs, gainDb = :gainDb WHERE sessionId = :sessionId")
    suspend fun setTakeMix(sessionId: Long, offsetMs: Int, gainDb: Float)

    @Query(
        "SELECT * FROM backings WHERE id NOT IN (SELECT backingId FROM piece_backings) " +
            "AND id NOT IN (SELECT backingId FROM take_backings)",
    )
    suspend fun unused(): List<BackingEntity>

    @Query("DELETE FROM backings WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT fileName FROM backings")
    suspend fun fileNames(): List<String>

    @Transaction
    suspend fun deleteUnused(): List<String> {
        val gone = unused()
        if (gone.isNotEmpty()) delete(gone.map { it.id })
        return gone.map { it.fileName }
    }
}

class RoomBackingRepository @Inject constructor(
    private val dao: BackingDao,
    private val files: BackingFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackingRepository {
    override val backings: Flow<List<Backing>> = dao.backings().map { rows -> rows.map { it.toDomain() } }

    override val pieceBackings: Flow<List<PieceBacking>> = dao.pieceBackings().map { rows -> rows.map { PieceBacking(it.pieceId, it.backingId, it.enabled) } }

    override val takeBackings: Flow<List<TakeBacking>> = dao.takeBackings().map { rows -> rows.map { it.toDomain() } }

    override suspend fun backing(id: Long): Backing? = dao.backing(id)?.toDomain()

    override suspend fun add(backing: Backing): Long = dao.insert(
        BackingEntity(0, backing.fileName, backing.title, backing.durationMs, backing.sampleRate, backing.channels, backing.sizeBytes, backing.addedAtEpochMs),
    )

    override suspend fun setForPiece(pieceId: Long, backingId: Long?) {
        if (backingId == null) dao.deletePiece(pieceId) else dao.upsertPiece(PieceBackingEntity(pieceId, backingId, enabled = true))
    }

    override suspend fun setEnabled(pieceId: Long, enabled: Boolean) = dao.setEnabled(pieceId, enabled)

    override suspend fun saveTake(take: TakeBacking) = dao.insertTake(
        TakeBackingEntity(take.sessionId, take.backingId, take.offsetMs, take.recordedOffsetMs, take.gainDb, take.playedMs, take.output.name, take.deviceName),
    )

    override suspend fun setTakeMix(sessionId: Long, offsetMs: Int, gainDb: Float) = dao.setTakeMix(sessionId, offsetMs, gainDb)

    override suspend fun deleteUnused(): Set<String> {
        val gone = dao.deleteUnused()
        val kept = dao.fileNames().toSet()
        withContext(io) {
            gone.forEach(files::delete)
            files.deleteOrphans(kept)
        }
        return kept
    }

    private fun BackingEntity.toDomain() = Backing(id, fileName, title, durationMs, sampleRate, channels, sizeBytes, addedAtEpochMs)

    private fun TakeBackingEntity.toDomain() = TakeBacking(
        sessionId = sessionId,
        backingId = backingId,
        offsetMs = offsetMs,
        recordedOffsetMs = recordedOffsetMs,
        gainDb = gainDb,
        playedMs = playedMs,
        output = BackingOutput.entries.firstOrNull { it.name == output } ?: BackingOutput.WIRED,
        deviceName = deviceName,
    )
}
