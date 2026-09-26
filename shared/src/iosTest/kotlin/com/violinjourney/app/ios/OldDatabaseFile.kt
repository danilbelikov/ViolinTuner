package com.violinjourney.app.ios

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.DatabaseMigrations

/**
 * A database file of an older version, as the app of that version left it. Version 1 is laid out by hand — the SQL of
 * `app/schemas/…/1.json`, as the Android `DatabaseMigrationTest` lays it out — with the session «Гаммы» and its samples;
 * the later versions by the steps of [DatabaseMigrations] themselves, with a day of practice from version 2 on and the
 * piece «Менуэт» (the session is its take) from version 4 on. Room's own table carries the identity of that version,
 * and a file [madeOnAndroid] has Android's table of the locale too: what a copy from an older Android app brings.
 */
internal object OldDatabaseFile {
    fun layOut(path: String, version: Int, madeOnAndroid: Boolean) {
        require(version in 1 until AppDatabase.VERSION) { "not an older version: $version" }
        val connection = BundledSQLiteDriver().open(path)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`a4Hz` REAL NOT NULL, `toleranceCents` REAL NOT NULL, `nearCents` REAL NOT NULL, " +
                    "`scorePercent` INTEGER NOT NULL, `nearPercent` INTEGER NOT NULL, `offPercent` INTEGER NOT NULL, " +
                    "`maeCents` REAL NOT NULL, `biasCents` REAL NOT NULL, `previewZones` TEXT NOT NULL, `audioPath` TEXT)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startedAtEpochMs` ON `sessions` (`startedAtEpochMs`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_samples` (`sessionId` INTEGER NOT NULL, `bucketMs` INTEGER NOT NULL, " +
                    "`data` BLOB NOT NULL, PRIMARY KEY(`sessionId`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "INSERT INTO sessions (id, title, startedAtEpochMs, durationMs, a4Hz, toleranceCents, nearCents, " +
                    "scorePercent, nearPercent, offPercent, maeCents, biasCents, previewZones, audioPath) " +
                    "VALUES (1, 'Гаммы', 1700000000000, 120000, 440.0, 8.0, 20.0, 80, 15, 5, 6.5, -2.0, 'ININ', NULL)",
            )
            connection.execSQL("INSERT INTO session_samples (sessionId, bucketMs, data) VALUES (1, 50, X'000000')")
            DatabaseMigrations.ALL.filter { it.endVersion <= version }.sortedBy { it.startVersion }.forEach { step ->
                step.migrate(connection)
                when (step.endVersion) {
                    2 -> connection.execSQL(
                        "INSERT INTO practice_entries (id, date, startedAtEpochMs, durationMs, manual) " +
                            "VALUES (1, '2026-09-13', 1789300000000, 2700000, 0)",
                    )
                    4 -> {
                        connection.execSQL(
                            "INSERT INTO pieces (id, title, composer, status, notes, createdAtEpochMs, updatedAtEpochMs) " +
                                "VALUES (1, 'Менуэт', 'Бах', 'LEARNING', 'смычок', 1, 2)",
                        )
                        connection.execSQL("UPDATE sessions SET pieceId = 1 WHERE id = 1")
                    }
                }
            }
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${IDENTITY.getValue(version)}')")
            if (madeOnAndroid) {
                connection.execSQL("CREATE TABLE android_metadata (locale TEXT)")
                connection.execSQL("INSERT INTO android_metadata VALUES('en_US')")
            }
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
    }

    /** The identity Room kept for each version: `identityHash` of `app/schemas/com.violinjourney.app.core.data.AppDatabase/<version>.json`. */
    private val IDENTITY = mapOf(
        1 to "12baa5ae655c3f251aba828c0f221689",
        2 to "507d26be537443e5edea051ac776adbb",
        3 to "f660b1aa09c3d325382f074ee5261bbe",
        4 to "1e16a97217fc988e24a711909120eebb",
        5 to "41549c77de045909c294eaf071efd970",
        6 to "a45585a739493eb7099c7e8f16a8cd56",
        7 to "2807d51b91f369e5c2b315235601b9dd",
        8 to "97d4c64245f8b6f0239f155e0dd83871",
        9 to "e32b17650b8d5db92c9d214ce33605ec",
        10 to "48ca9a714b21502944c39f2db88be858",
        11 to "48ca9a714b21502944c39f2db88be858",
        12 to "bf1815174574701e70b5d61dc237e16e",
    )
}
