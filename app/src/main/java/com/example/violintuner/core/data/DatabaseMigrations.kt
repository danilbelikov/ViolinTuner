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

    /**
     * The best take chosen by hand (spec 3.21): a piece may point at one of its takes. One nullable
     * column, no foreign key — a mark whose take is gone reads as no mark.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `bestTakeId` INTEGER")
        }
    }

    /**
     * Sections of the repertoire, scales and the day a piece was learnt (spec 3.22). Columns are
     * added, nothing is rebuilt: every piece there is lands in «Произведения», and those already
     * «В репертуаре» take the day of their last edit as the day they were learnt (spec 5.16).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `section` TEXT NOT NULL DEFAULT 'PIECES'")
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `groupId` INTEGER")
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `scaleKind` TEXT")
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `scaleOctaves` INTEGER")
            db.execSQL("ALTER TABLE `pieces` ADD COLUMN `learnedAtEpochMs` INTEGER")
            db.execSQL("UPDATE `pieces` SET `learnedAtEpochMs` = `updatedAtEpochMs` WHERE `status` = 'IN_REPERTOIRE'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `piece_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAtEpochMs` INTEGER NOT NULL)",
            )
        }
    }

    /** The journey (spec 3.23): what practices earned, the stops reached and what was bought there. Three new tables; nothing that exists is touched. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_earnings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                    "`notesPlayed` INTEGER NOT NULL, `notesInTune` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `takts` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_arrivals` (`stopId` TEXT NOT NULL, `arrivedAtEpochMs` INTEGER NOT NULL, " +
                    "`price` INTEGER NOT NULL, PRIMARY KEY(`stopId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_extras` (`stopId` TEXT NOT NULL, `extra` TEXT NOT NULL, `price` INTEGER NOT NULL, " +
                    "`boughtAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`stopId`, `extra`))",
            )
        }
    }

    /** The home (spec 3.24): what was bought for it and what stands where. Nothing is seeded: the rented room and what it came with are not rows. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_purchases` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `price` INTEGER NOT NULL, " +
                    "`boughtAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `home_choices` (`slot` TEXT NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`slot`))")
        }
    }

    /**
     * No change of the schema: the plum rug left the rented room for the shop (spec 3.25), and nothing is taken away —
     * whoever had the room before keeps the rug as bought for nothing, lying where it lay unless they chose otherwise.
     * A fresh install is created at this version without this migration, and so without the rug.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO `home_purchases` (`id`, `kind`, `price`, `boughtAtEpochMs`) VALUES ('rug_plum', 'ITEM', 0, 0)")
            db.execSQL("INSERT OR IGNORE INTO `home_choices` (`slot`, `itemId`) VALUES ('rug', 'rug_plum')")
        }
    }

    /**
     * Blocks — «подходы» (spec 3.28): time a practice gave to one element of the repertoire, and what an
     * earning paid for them. A new table and a column with a default: nothing that exists is rebuilt, older
     * earnings paid for no element.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `piece_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pieceId` INTEGER NOT NULL, " +
                    "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `goalMs` INTEGER NOT NULL, " +
                    "`done` INTEGER NOT NULL, `paid` INTEGER NOT NULL, FOREIGN KEY(`pieceId`) REFERENCES `pieces`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_piece_blocks_pieceId` ON `piece_blocks` (`pieceId`)")
            db.execSQL("ALTER TABLE `journey_earnings` ADD COLUMN `piecesPaid` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Backings — «минусовки» (spec 3.32): accompaniment files, the one a piece has and the one each take was
     * made under. Three new tables, nothing that exists is touched: no piece has a backing yet.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `backings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `sampleRate` INTEGER NOT NULL, `channels` INTEGER NOT NULL, " +
                    "`sizeBytes` INTEGER NOT NULL, `addedAtEpochMs` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `piece_backings` (`pieceId` INTEGER NOT NULL, `backingId` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`pieceId`), FOREIGN KEY(`pieceId`) REFERENCES `pieces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_piece_backings_backingId` ON `piece_backings` (`backingId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `take_backings` (`sessionId` INTEGER NOT NULL, `backingId` INTEGER NOT NULL, `offsetMs` INTEGER NOT NULL, " +
                    "`recordedOffsetMs` INTEGER NOT NULL, `gainDb` REAL NOT NULL, `playedMs` INTEGER NOT NULL, `output` TEXT NOT NULL, `deviceName` TEXT, `latencyMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sessionId`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_take_backings_backingId` ON `take_backings` (`backingId`)")
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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
}
