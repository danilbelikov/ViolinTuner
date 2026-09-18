package com.example.violintuner.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.violintuner.core.data.practice.PracticeDao
import com.example.violintuner.core.data.practice.PracticeEntity
import com.example.violintuner.core.data.session.SamplesEntity
import com.example.violintuner.core.data.session.SessionDao
import com.example.violintuner.core.data.session.SessionEntity

/**
 * The one database of the app: sessions (recordings with analysis) and practice entries (time).
 * Every version's schema is exported to `app/schemas` and committed; a new version needs a
 * migration in [DatabaseMigrations] and a test that the old rows survive it.
 */
@Database(
    entities = [SessionEntity::class, SamplesEntity::class, PracticeEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        const val FILE_NAME = "violin.db"
    }
}
