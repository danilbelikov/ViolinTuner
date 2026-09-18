package com.example.violintuner.core.data.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrophyDao {
    @Query("SELECT * FROM trophies ORDER BY hours")
    fun observeAll(): Flow<List<TrophyEntity>>

    /** A mark that already has its trophy keeps it, date and all. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(trophy: TrophyEntity)

    @Query("UPDATE trophies SET shown = 1 WHERE hours = :hours")
    suspend fun markShown(hours: Int)
}
