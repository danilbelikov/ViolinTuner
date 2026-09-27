package com.violinjourney.app.core.data.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC, id DESC")
    abstract fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM session_samples WHERE sessionId = :id")
    abstract suspend fun samples(id: Long): SamplesEntity?

    @Query("SELECT audioPath FROM sessions WHERE audioPath IS NOT NULL")
    abstract suspend fun audioPaths(): List<String>

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String?)

    @Query("SELECT audioPath, videoPath FROM sessions WHERE id IN (:ids)")
    protected abstract suspend fun filesOf(ids: List<Long>): List<SessionFiles>

    /** Samples go with the session through the cascade. */
    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    protected abstract suspend fun deleteSessions(ids: List<Long>)

    /** Sound settings of their own: that table has no foreign key (its default row belongs to no session). */
    @Query("DELETE FROM sound_settings WHERE ownerId IN (:ids)")
    protected abstract suspend fun deleteOwnSound(ids: List<Long>)

    /**
     * All the rows of these sessions or none (spec 5.12); answers with the files of the rows that really were there, one
     * entry a row, for the caller to remove afterwards — an id already gone answers nothing. Chunked: SQLite limits the
     * variables of a query.
     */
    @Transaction
    open suspend fun delete(ids: Collection<Long>): List<SessionFiles> = ids.distinct().chunked(DELETE_CHUNK).flatMap { chunk ->
        val files = filesOf(chunk)
        deleteSessions(chunk)
        deleteOwnSound(chunk)
        files
    }

    @Insert
    protected abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert
    protected abstract suspend fun insertSamples(samples: SamplesEntity)

    /** Both rows or neither: a session without samples cannot be opened. */
    @Transaction
    open suspend fun insert(session: SessionEntity, bucketMs: Long, samples: ByteArray): Long {
        val id = insertSession(session)
        insertSamples(SamplesEntity(sessionId = id, bucketMs = bucketMs, data = samples))
        return id
    }

    private companion object {
        const val DELETE_CHUNK = 500
    }
}
