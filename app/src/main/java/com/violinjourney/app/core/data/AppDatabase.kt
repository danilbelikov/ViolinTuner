package com.violinjourney.app.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.violinjourney.app.core.data.backing.BackingDao
import com.violinjourney.app.core.data.backing.BackingEntity
import com.violinjourney.app.core.data.backing.PieceBackingEntity
import com.violinjourney.app.core.data.backing.TakeBackingEntity
import com.violinjourney.app.core.data.practice.PieceBlockDao
import com.violinjourney.app.core.data.practice.PieceBlockEntity
import com.violinjourney.app.core.data.practice.PracticeDao
import com.violinjourney.app.core.data.practice.PracticeEntity
import com.violinjourney.app.core.data.progress.TrophyDao
import com.violinjourney.app.core.data.progress.TrophyEntity
import com.violinjourney.app.core.data.journey.ArrivalEntity
import com.violinjourney.app.core.data.journey.EarningEntity
import com.violinjourney.app.core.data.journey.ExtraEntity
import com.violinjourney.app.core.data.journey.HomeChoiceEntity
import com.violinjourney.app.core.data.journey.HomePurchaseEntity
import com.violinjourney.app.core.data.journey.JourneyDao
import com.violinjourney.app.core.data.repertoire.PieceEntity
import com.violinjourney.app.core.data.repertoire.PieceGroupEntity
import com.violinjourney.app.core.data.repertoire.RepertoireDao
import com.violinjourney.app.core.data.repertoire.SheetPageEntity
import com.violinjourney.app.core.data.session.SamplesEntity
import com.violinjourney.app.core.data.session.SessionDao
import com.violinjourney.app.core.data.session.SessionEntity
import com.violinjourney.app.core.data.sound.SoundDao
import com.violinjourney.app.core.data.sound.SoundPresetEntity
import com.violinjourney.app.core.data.sound.SoundSettingsEntity

/**
 * The one database of the app: sessions (recordings with analysis), practice entries (time),
 * the trophies given for that time, the repertoire (pieces with their sheet pages), and how
 * recordings are made to sound (settings of sound processing and the user's presets); the journey
 * and the home; the blocks of practices — time given to elements of the repertoire; the backings of pieces and takes.
 * Every version's schema is exported to `app/schemas` and committed; a new version needs a
 * migration in [DatabaseMigrations] and a test that the old rows survive it.
 */
@Database(
    entities = [
        SessionEntity::class, SamplesEntity::class, PracticeEntity::class, TrophyEntity::class,
        PieceEntity::class, PieceGroupEntity::class, SheetPageEntity::class, SoundSettingsEntity::class, SoundPresetEntity::class,
        EarningEntity::class, ArrivalEntity::class, ExtraEntity::class,
        HomePurchaseEntity::class,
        HomeChoiceEntity::class,
        PieceBlockEntity::class,
        BackingEntity::class, PieceBackingEntity::class, TakeBackingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun practiceDao(): PracticeDao
    abstract fun trophyDao(): TrophyDao
    abstract fun repertoireDao(): RepertoireDao
    abstract fun soundDao(): SoundDao
    abstract fun journeyDao(): JourneyDao
    abstract fun pieceBlockDao(): PieceBlockDao
    abstract fun backingDao(): BackingDao

    companion object {
        const val FILE_NAME = "violin.db"

        /** The schema version. Raising it needs a migration from the previous one in [DatabaseMigrations.ALL] — `DatabaseMigrationChainTest` fails the build without it. */
        const val VERSION = 13
    }
}
