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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
