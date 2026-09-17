package com.example.violintuner.core.data.session

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

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String?)

    /** Samples go with the session through the cascade. */
    @Query("DELETE FROM sessions WHERE id = :id")
    abstract suspend fun delete(id: Long)

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
}
