package com.example.violintuner.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a database file laid out exactly as an old version (statements from
 * `app/schemas/…/1.json` and `2.json`) through Room with the migrations. Room validates the migrated
 * schema against the current entities when it opens such a file, so a migration that leaves
 * a table different from what the entities declare fails here, not on the user's phone.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = context.getDatabasePath("migration-test.db")
    private var database: AppDatabase? = null

    @Before
    fun cleanUp() {
        file.parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(file)
    }

    /** Version 1 with one session; [version2] adds what version 2 had on top: practice entries with one row. */
    private fun createOldFile(version2: Boolean) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`a4Hz` REAL NOT NULL, `toleranceCents` REAL NOT NULL, `nearCents` REAL NOT NULL, " +
                    "`scorePercent` INTEGER NOT NULL, `nearPercent` INTEGER NOT NULL, `offPercent` INTEGER NOT NULL, " +
                    "`maeCents` REAL NOT NULL, `biasCents` REAL NOT NULL, `previewZones` TEXT NOT NULL, `audioPath` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startedAtEpochMs` ON `sessions` (`startedAtEpochMs`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_samples` (`sessionId` INTEGER NOT NULL, `bucketMs` INTEGER NOT NULL, " +
                    "`data` BLOB NOT NULL, PRIMARY KEY(`sessionId`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO sessions (id, title, startedAtEpochMs, durationMs, a4Hz, toleranceCents, nearCents, " +
                    "scorePercent, nearPercent, offPercent, maeCents, biasCents, previewZones, audioPath) " +
                    "VALUES (1, 'Гаммы', 1700000000000, 120000, 440.0, 8.0, 20.0, 80, 15, 5, 6.5, -2.0, 'ININ', NULL)",
            )
            db.execSQL("INSERT INTO session_samples (sessionId, bucketMs, data) VALUES (1, 50, X'000000')")
            if (version2) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `practice_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`manual` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_entries_date` ON `practice_entries` (`date`)")
                db.execSQL(
                    "INSERT INTO practice_entries (id, date, startedAtEpochMs, durationMs, manual) " +
                        "VALUES (1, '2026-09-13', 1789300000000, 2700000, 0)",
                )
            }
            db.execSQL("PRAGMA user_version = ${if (version2) 2 else 1}")
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
            .also { database = it }

    @After
    fun tearDown() {
        database?.close()
        SQLiteDatabase.deleteDatabase(file)
    }

    @Test
    fun sessionsSurviveTheMigrationAndPracticeEntriesWork() = runBlocking {
        createOldFile(version2 = false)
        val db = openMigrated()

        val sessions = db.sessionDao().observeAll().first()
        assertEquals(1, sessions.size)
        assertEquals("Гаммы", sessions[0].title)
        assertEquals(80, sessions[0].scorePercent)
        assertEquals(50L, db.sessionDao().samples(1)!!.bucketMs)

        db.practiceDao().insert(
            com.example.violintuner.core.data.practice.PracticeEntity(
                date = "2026-09-17", startedAtEpochMs = 1L, durationMs = 60_000, manual = false,
            ),
        )
        assertEquals("2026-09-17", db.practiceDao().observeAll().first().single().date)
    }

    @Test
    fun sessionsAndPracticeSurviveTheMigrationToTrophies() = runBlocking {
        createOldFile(version2 = true)
        val db = openMigrated()

        assertEquals("Гаммы", db.sessionDao().observeAll().first().single().title)
        val practice = db.practiceDao().observeAll().first().single()
        assertEquals("2026-09-13", practice.date)
        assertEquals(2_700_000L, practice.durationMs)

        db.trophyDao().insertIfAbsent(
            com.example.violintuner.core.data.progress.TrophyEntity(hours = 1, awardedDate = "2026-09-18", shown = false),
        )
        assertEquals(1, db.trophyDao().observeAll().first().single().hours)
    }
}
