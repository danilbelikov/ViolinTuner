package com.violinjourney.app.ios

import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import kotlin.test.AfterTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The real storage of the iOS app — Room on its own SQLite, DataStore on a file — in a folder of the simulator. */
@OptIn(ExperimentalForeignApi::class)
class IosStorageTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun `a practice written to the database is read back`() = runTest {
        val database = IosStorage.database(directory)
        val practice = RoomPracticeRepository(database.practiceDao())
        practice.add(PracticeEntry(LocalDate(2026, 9, 24), startedAtEpochMs = 1_000, durationMs = 45 * 60_000, manual = false))
        val entries = practice.entries.first()
        assertEquals(1, entries.size)
        assertEquals(LocalDate(2026, 9, 24), entries.single().date)
        assertEquals(45 * 60_000L, entries.single().durationMs)
        database.close()
    }

    @Test
    fun `settings survive — a new store on the same file reads them`() = runTest {
        val firstLife = CoroutineScope(Dispatchers.Default + Job())
        DataStoreSettingsRepository(IosStorage.settings(directory, firstLife)).run {
            setA4(442)
            setTolerance(TolerancePreset.PRO)
        }
        firstLife.coroutineContext[Job]!!.cancelAndJoin() // the app is closed
        val reread = DataStoreSettingsRepository(IosStorage.settings(directory)).settings.first()
        assertEquals(442, reread.a4Hz)
        assertEquals(TolerancePreset.PRO, reread.tolerance)
    }
}
