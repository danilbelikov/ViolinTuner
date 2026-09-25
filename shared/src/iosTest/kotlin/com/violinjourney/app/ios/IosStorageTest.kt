package com.violinjourney.app.ios

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupReader
import com.violinjourney.app.core.backup.BackupWriter
import com.violinjourney.app.core.backup.IosBackupStore
import com.violinjourney.app.core.backup.IosRestoreSwap
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import com.violinjourney.app.core.time.SystemWallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
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

    @Test
    fun `a copy takes the database whole and puts it back in place`() = runTest {
        val database = IosStorage.database(directory)
        val practice = RoomPracticeRepository(database.practiceDao())
        practice.add(PracticeEntry(LocalDate(2026, 9, 24), startedAtEpochMs = 1_000, durationMs = 30 * 60_000, manual = false))
        val data = PlatformFile(directory)
        val store = IosBackupStore(
            data, database, RoomSessionRepository(database.sessionDao(), IntonationConfig(), NoAudioFiles, SystemWallClock, NoOpAnalytics()),
            RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics()),
            practice, RoomTrophyRepository(database.trophyDao()), ProgressConfig(), SystemWallClock, Dispatchers.Default,
        )
        val prepared = store.prepare(setOf(BackupPart.DATA))
        assertEquals(1, prepared.manifest.counts.practiceDays)
        val archive = data.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, prepared.manifest, prepared.entries) {}
        store.cleanUp()
        // a day more after the copy: bringing the copy back has to take it away again
        practice.add(PracticeEntry(LocalDate(2026, 9, 25), startedAtEpochMs = 2_000, durationMs = 10 * 60_000, manual = false))
        BackupReader.extract(archive.openInput()!!, store.newStaging(), 0) {}
        store.markStagingReady()
        database.close()
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        val reopened = IosStorage.database(directory)
        assertEquals(listOf(LocalDate(2026, 9, 24)), RoomPracticeRepository(reopened.practiceDao()).entries.first().map { it.date })
        reopened.close()
    }

    private object NoAudioFiles : SessionAudioFiles {
        override fun newFile() = PlatformFile("/dev/null")
        override fun existing(name: String): PlatformFile? = null
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private object NoSheetFiles : SheetFiles {
        override suspend fun import(sourceUri: String): SheetFiles.Stored? = null
        override fun existing(name: String): PlatformFile? = null
        override suspend fun delete(names: Collection<String>) = Unit
        override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
        override fun newCameraFile() = PlatformFile("/dev/null")
    }
}
