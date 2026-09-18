package com.example.violintuner.core.data.practice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PracticeDao {
    @Query("SELECT * FROM practice_entries ORDER BY date, startedAtEpochMs, id")
    abstract fun observeAll(): Flow<List<PracticeEntity>>

    @Insert
    abstract suspend fun insert(entry: PracticeEntity): Long

    @Query("DELETE FROM practice_entries WHERE date = :date")
    protected abstract suspend fun deleteDay(date: String)

    /** "Change time": the day's entries become the one given, or nothing when it is null. */
    @Transaction
    open suspend fun replaceDay(date: String, replacement: PracticeEntity?) {
        deleteDay(date)
        if (replacement != null) insert(replacement)
    }
}
