package com.example.violintuner.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    /** Version 2 adds the practice entries; the session tables are untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `practice_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`manual` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_entries_date` ON `practice_entries` (`date`)")
        }
    }

    /** Version 3 adds the trophies; sessions and practice entries are untouched. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trophies` (`hours` INTEGER NOT NULL, `awardedDate` TEXT NOT NULL, " +
                    "`shown` INTEGER NOT NULL, PRIMARY KEY(`hours`))",
            )
        }
    }

    /**
     * Version 4 adds the repertoire and lets a session belong to a piece. The link is a plain
     * nullable column with an index and no foreign key on purpose: a foreign key would mean
     * rebuilding the sessions table — the user's recordings — for nothing; unlinking on
     * removal is done by [com.example.violintuner.core.data.repertoire.RepertoireDao.deletePiece].
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pieces` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`composer` TEXT NOT NULL, `keyTonic` TEXT, `keyAccidental` TEXT, `keyMode` TEXT, `tempoBpm` INTEGER, " +
                    "`status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, " +
                    "`updatedAtEpochMs` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sheet_pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`pieceId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `fileName` TEXT NOT NULL, " +
                    "`thumbFileName` TEXT NOT NULL, FOREIGN KEY(`pieceId`) REFERENCES `pieces`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sheet_pages_pieceId` ON `sheet_pages` (`pieceId`)")
            db.execSQL("ALTER TABLE `sessions` ADD COLUMN `pieceId` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_pieceId` ON `sessions` (`pieceId`)")
        }
    }

    /**
     * Sound processing (spec 3.17): settings per recording — the row with owner 0 is the default for
     * all of them — and the user's presets. Two new tables; nothing that exists is touched.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sound_settings` (`ownerId` INTEGER NOT NULL, $SOUND_COLUMNS, PRIMARY KEY(`ownerId`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sound_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAtEpochMs` INTEGER NOT NULL, $SOUND_COLUMNS)",
            )
        }
    }

    /**
     * Video takes (spec 3.19): a session may point at a video file. One nullable column — the
     * sessions of the user are not rebuilt; old ones simply have no picture.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `sessions` ADD COLUMN `videoPath` TEXT")
        }
    }

    /** The columns of `SoundColumns`, as Room declares them: both sound tables embed the same set. Internal for the migration test, which lays out a version 5 file by hand. */
    internal const val SOUND_COLUMNS =
        "`eqEnabled` INTEGER NOT NULL, `lowCutEnabled` INTEGER NOT NULL, `lowCutHz` REAL NOT NULL, `lowHz` REAL NOT NULL, " +
            "`lowGainDb` REAL NOT NULL, `bodyHz` REAL NOT NULL, `bodyGainDb` REAL NOT NULL, `bodyQ` REAL NOT NULL, " +
            "`presenceHz` REAL NOT NULL, `presenceGainDb` REAL NOT NULL, `presenceQ` REAL NOT NULL, `airHz` REAL NOT NULL, " +
            "`airGainDb` REAL NOT NULL, `compEnabled` INTEGER NOT NULL, `compThresholdDb` REAL NOT NULL, `compRatio` REAL NOT NULL, " +
            "`compAttackMs` REAL NOT NULL, `compReleaseMs` REAL NOT NULL, `compMakeupDb` REAL NOT NULL, `compAmount` REAL, " +
            "`reverbEnabled` INTEGER NOT NULL, `reverbSpace` TEXT NOT NULL, `reverbDecaySec` REAL NOT NULL, " +
            "`reverbPreDelayMs` REAL NOT NULL, `reverbBrightness` REAL NOT NULL, `reverbMix` REAL NOT NULL, " +
            "`outputEnabled` INTEGER NOT NULL, `outputGainDb` REAL NOT NULL"

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
