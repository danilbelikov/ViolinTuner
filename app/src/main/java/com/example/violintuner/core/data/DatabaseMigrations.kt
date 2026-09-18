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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
