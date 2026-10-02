package com.violinjourney.app.ios

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.events.CalendarEventEntity
import com.violinjourney.app.core.data.practice.PracticeEntity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The whole chain of [com.violinjourney.app.core.data.DatabaseMigrations] on the SQLite iOS brings: a file of the first
 * version is opened by [IosStorage] as the app opens its own. Room checks the migrated tables against the entities on
 * opening, so a step that leaves a table other than the entities declare — a future one too — fails here, not on a
 * phone; what each step does to the rows is checked step by step on Android by `DatabaseMigrationTest`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDatabaseMigrationTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun `a database of the first version reaches the current one with its rows`() = runTest {
        val file = "$directory/${AppDatabase.FILE_NAME}"
        OldDatabaseFile.layOut(file, version = 1, madeOnAndroid = false)
        val database = IosStorage.database(directory)
        val session = database.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals(80, session.scorePercent)
        assertEquals(50L, database.sessionDao().samples(session.id)!!.bucketMs)
        // the step of rows 10 → 11: whoever had the rented room keeps its rug, bought for nothing, where it lay
        assertEquals(listOf("rug_plum" to 0), database.journeyDao().observeHomePurchases().first().map { it.id to it.price })
        assertEquals(listOf("rug" to "rug_plum"), database.journeyDao().observeHomeChoices().first().map { it.slot to it.itemId })
        assertTrue(database.backingDao().backings().first().isEmpty(), "no backing came from nowhere")
        assertNoEvents(database)
        assertEquals(null, session.eventId, "an old recording belongs to no event")
        database.practiceDao().insert(PracticeEntity(date = "2026-09-26", startedAtEpochMs = 1L, durationMs = 60_000, manual = false))
        assertEquals(listOf("2026-09-26"), database.practiceDao().observeAll().first().map { it.date })
        database.close()
        val connection = BundledSQLiteDriver().open(file)
        val statement = connection.prepare("PRAGMA user_version")
        val version = try {
            statement.step()
            statement.getLong(0)
        } finally {
            statement.close()
            connection.close()
        }
        assertEquals(AppDatabase.VERSION.toLong(), version)
    }

    /**
     * What every phone and every copy had before the events (spec 3.35): a database of version 13 — here as an Android copy
     * leaves it, with Room's identity of that version — opens at the current one with its rows, no events and a recording of
     * none, and takes an event at once.
     */
    @Test
    fun `a database of the version before the events opens with its rows and no events`() = runTest {
        val file = "$directory/${AppDatabase.FILE_NAME}"
        OldDatabaseFile.layOut(file, version = 13, madeOnAndroid = true)
        val database = IosStorage.database(directory)
        val session = database.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals(1L, session.pieceId)
        assertEquals(null, session.eventId)
        assertEquals(listOf("Менуэт"), database.repertoireDao().observePieces().first().map { it.title })
        assertEquals(listOf("2026-09-13"), database.practiceDao().observeAll().first().map { it.date })
        assertNoEvents(database)
        val dao = database.eventDao()
        val id = dao.insert(
            CalendarEventEntity(
                kind = "LESSON", kindId = null, date = "2026-10-05", startMinutes = 1020, durationMinutes = 45, title = "", place = "",
                notes = "", seriesId = null, detached = false, createdAtEpochMs = 1,
            ),
            series = null, pieceIds = listOf(1),
        )
        assertEquals(listOf(id), dao.observeEvents().first().map { it.id })
        assertEquals(listOf(1L), dao.observePrograms().first().map { it.pieceId }, "the piece of version 4 goes into a program")
        database.close()
    }

    private suspend fun assertNoEvents(database: AppDatabase) {
        val dao = database.eventDao()
        assertTrue(dao.observeEvents().first().isEmpty(), "no event came from nowhere")
        assertTrue(dao.observeKinds().first().isEmpty())
        assertTrue(dao.observeSeries().first().isEmpty())
        assertTrue(dao.observePrograms().first().isEmpty())
        assertTrue(dao.observeRecordEvents().first().isEmpty())
    }
}
