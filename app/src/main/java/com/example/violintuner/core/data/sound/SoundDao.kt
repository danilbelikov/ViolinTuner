package com.example.violintuner.core.data.sound

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {
    @Query("SELECT * FROM sound_settings")
    fun observeSettings(): Flow<List<SoundSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(settings: SoundSettingsEntity)

    @Query("DELETE FROM sound_settings WHERE ownerId = :sessionId")
    suspend fun deleteOwn(sessionId: Long)

    @Query("SELECT * FROM sound_presets ORDER BY createdAtEpochMs, id")
    fun observePresets(): Flow<List<SoundPresetEntity>>

    @Insert
    suspend fun insertPreset(preset: SoundPresetEntity): Long

    @Query("DELETE FROM sound_presets WHERE id = :id")
    suspend fun deletePreset(id: Long)
}
