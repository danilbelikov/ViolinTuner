package com.example.violintuner.core.data.session

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class, SamplesEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val FILE_NAME = "violin.db"
    }
}
