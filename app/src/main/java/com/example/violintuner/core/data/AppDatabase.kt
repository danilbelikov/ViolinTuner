package com.example.violintuner.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.violintuner.core.data.practice.PracticeDao
import com.example.violintuner.core.data.practice.PracticeEntity
import com.example.violintuner.core.data.progress.TrophyDao
import com.example.violintuner.core.data.progress.TrophyEntity
import com.example.violintuner.core.data.repertoire.PieceEntity
import com.example.violintuner.core.data.repertoire.RepertoireDao
import com.example.violintuner.core.data.repertoire.SheetPageEntity
import com.example.violintuner.core.data.session.SamplesEntity
import com.example.violintuner.core.data.session.SessionDao
import com.example.violintuner.core.data.session.SessionEntity
import com.example.violintuner.core.data.sound.SoundDao
import com.example.violintuner.core.data.sound.SoundPresetEntity
import com.example.violintuner.core.data.sound.SoundSettingsEntity

/**
 * The one database of the app: sessions (recordings with analysis), practice entries (time),
 * the trophies given for that time, the repertoire (pieces with their sheet pages), and how
 * recordings are made to sound (settings of sound processing and the user's presets).
 * Every version's schema is exported to `app/schemas` and committed; a new version needs a
 * migration in [DatabaseMigrations] and a test that the old rows survive it.
 */
@Database(
    entities = [
        SessionEntity::class, SamplesEntity::class, PracticeEntity::class, TrophyEntity::class,
        PieceEntity::class, SheetPageEntity::class, SoundSettingsEntity::class, SoundPresetEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun practiceDao(): PracticeDao
    abstract fun trophyDao(): TrophyDao
    abstract fun repertoireDao(): RepertoireDao
    abstract fun soundDao(): SoundDao

    companion object {
        const val FILE_NAME = "violin.db"
    }
}
