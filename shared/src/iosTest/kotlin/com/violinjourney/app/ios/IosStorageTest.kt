package com.violinjourney.app.ios

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupEntry
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupPaths
import com.violinjourney.app.core.backup.BackupReader
import com.violinjourney.app.core.backup.BackupWriter
import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.backup.IosBackupStore
import com.violinjourney.app.core.backup.IosRestoreSwap
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.events.RoomEventRepository
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindSave
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.DeviceOnly
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.io.writeBytes
import com.violinjourney.app.core.recording.video.FakeVideoThumbRuleStore
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import com.violinjourney.app.core.time.SystemWallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

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

    /**
     * The «restart» after a restore (spec 3.20): the graph's storage is let go — the watches of its scope ended and waited
     * for, then the database — and a new one opens the same files at once, with no «multiple DataStores» for the file.
     */
    @Test
    fun `a storage let go lets the next one open the same files at once`() = runTest {
        val first = IosDataStorage(directory)
        DataStoreSettingsRepository(first.dataStore).setA4(442)
        RoomPracticeRepository(first.database.practiceDao())
            .add(PracticeEntry(LocalDate(2026, 9, 27), startedAtEpochMs = 1_000, durationMs = 30 * 60_000, manual = false))
        // a watch of the graph that takes a moment to end, as the ones on the main thread do
        val watchEnded = CompletableDeferred<Unit>()
        first.scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(50)
                    watchEnded.complete(Unit)
                }
            }
        }
        first.close()
        assertTrue(watchEnded.isCompleted, "the storage is let go only once what its scope ran has ended")

        val second = IosDataStorage(directory)
        assertEquals(442, DataStoreSettingsRepository(second.dataStore).settings.first().a4Hz)
        assertEquals(1, RoomPracticeRepository(second.database.practiceDao()).entries.first().size)
        second.close()
    }

    /** A settings file that cannot be parsed starts over, with the statistics off, instead of ending every start of the app. */
    @Test
    fun `settings that cannot be read start over instead of ending the start`() = runTest {
        val broken = assertNotNull(PlatformFile("$directory/${DataLayout.SETTINGS_FILE}").openOutput())
        broken.writeBytes(byteArrayOf(0x0A, 0x7F)) // a length-delimited field cut short
        broken.close()
        val life = CoroutineScope(Dispatchers.Default + Job())
        val settings = DataStoreSettingsRepository(IosStorage.settings(directory, life)).settings.first()
        assertEquals(false, settings.onboardingDone)
        assertEquals(440, settings.a4Hz)
        assertEquals(false, settings.analyticsEnabled)
        life.coroutineContext[Job]!!.cancelAndJoin()
    }

    private fun storeOf(data: PlatformFile, database: AppDatabase, practice: RoomPracticeRepository) = IosBackupStore(
        data, database, RoomSessionRepository(database.sessionDao(), IntonationConfig(), NoAudioFiles, SystemWallClock, NoOpAnalytics(), FakeVideoThumbRuleStore()),
        RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics()),
        practice, RoomTrophyRepository(database.trophyDao()), eventsOf(database), ProgressConfig(), SystemWallClock, Dispatchers.Default,
    )

    private fun eventsOf(database: AppDatabase) = RoomEventRepository(database.eventDao(), EventsConfig(), SystemWallClock, NoOpAnalytics(), Dispatchers.Default)

    /**
     * A calendar alone is something to keep (review of stage 98б): an app with a lesson and nothing else is not «nothing to save», and a
     * restore over it asks «Заменить данные?»; a kind of one's own without an event counts too.
     */
    @Test
    fun `a calendar alone is not an empty app`() = runTest {
        val database = IosStorage.database(directory)
        val store = storeOf(PlatformFile(directory), database, RoomPracticeRepository(database.practiceDao()))
        assertTrue(store.contents().counts.isEmpty, "a new app")
        val events = eventsOf(database)
        events.saveKind(KindSave.NewOwn("Сольфеджио", color = 1, sign = KindSign.BOOK))
        val kindOnly = store.contents().counts
        assertEquals(1, kindOnly.ownKinds)
        assertFalse(kindOnly.isEmpty, "a kind of one's own")
        events.add(EventDraft(date = LocalDate(2026, 10, 5), startMinutes = 17 * 60, durationMinutes = 45), Repeat.NONE, until = null, today = LocalDate(2026, 10, 3))
        val counts = store.contents().counts
        assertEquals(1, counts.events)
        assertFalse(counts.isEmpty, "a lesson")
        database.close()
    }

    @Test
    fun `a copy takes the database whole and puts it back in place`() = runTest {
        val database = IosStorage.database(directory)
        val practice = RoomPracticeRepository(database.practiceDao())
        practice.add(PracticeEntry(LocalDate(2026, 9, 24), startedAtEpochMs = 1_000, durationMs = 30 * 60_000, manual = false))
        val data = PlatformFile(directory)
        val store = storeOf(data, database, practice)
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
        // out of the staging folder, the restored data go into the backup of the phone again (spec 5.14)
        for (name in DataLayout.MEDIA_DIRS) assertFalse(DeviceOnly.isMarked(data.child(name).path), name)
    }

    /**
     * An unpacked copy waiting for the restart and the marks of a restore are this phone's alone (spec 5.14), as the
     * rules of the system's backup keep them on Android: a backup of the phone taken meanwhile holds none of them.
     */
    @Test
    fun `what a restore leaves until the restart stays out of the backup of the phone`() = runTest {
        val database = IosStorage.database(directory)
        val data = PlatformFile(directory)
        val store = storeOf(data, database, RoomPracticeRepository(database.practiceDao()))
        val staging = store.newStaging()
        store.markStagingReady()
        store.markWipe()
        database.close()
        assertTrue(DeviceOnly.isMarked(staging.path), "the unpacked copy")
        assertTrue(DeviceOnly.isMarked(data.child(IosRestoreSwap.READY_MARK).path), "the mark of a restore")
        assertTrue(DeviceOnly.isMarked(data.child(IosRestoreSwap.WIPE_MARK).path), "the mark of a wipe")
        assertFalse(DeviceOnly.isMarked(data.child(AppDatabase.FILE_NAME).path), "the database is backed up")
    }

    @Test
    fun `a copy made by an older app is brought to the current version`() = runTest {
        val data = PlatformFile(directory)
        // what a copy from an older Android app carries: its database at version 12, as that app left it
        val older = data.child("older.db")
        OldDatabaseFile.layOut(older.path, version = 12, madeOnAndroid = true)
        val manifest = BackupManifest(
            formatVersion = BackupManifest.FORMAT_VERSION, appVersion = "1.0", databaseVersion = 12, createdAtEpochMs = 1_790_000_000_000,
            device = "Google Pixel 10a", parts = setOf(BackupPart.DATA), counts = BackupCounts(sessions = 1, takes = 1, pieces = 1, practiceDays = 1),
            bytes = mapOf(BackupPart.DATA to older.sizeBytes()),
        )
        val archive = data.child("copy.zip")
        val entry = BackupEntry("${BackupPaths.DATABASE}/${AppDatabase.FILE_NAME}", BackupPart.DATA, older.sizeBytes()) { older.openInput() }
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entry)) {}
        // an older copy is taken, not refused as too new
        assertEquals(12, BackupReader.manifest(archive.openInput()!!, knownDatabase = AppDatabase.VERSION).databaseVersion)
        // the iPhone's own data, which the copy replaces
        val database = IosStorage.database(directory)
        val practice = RoomPracticeRepository(database.practiceDao())
        practice.add(PracticeEntry(LocalDate(2026, 9, 25), startedAtEpochMs = 2_000, durationMs = 10 * 60_000, manual = false))
        val store = storeOf(data, database, practice)
        BackupReader.extract(archive.openInput()!!, store.newStaging(), 0) {}
        store.markStagingReady()
        database.close()
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        val reopened = IosStorage.database(directory)
        val session = reopened.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals(1L, session.pieceId)
        assertEquals(50L, reopened.sessionDao().samples(session.id)!!.bucketMs)
        assertEquals(listOf("Менуэт"), reopened.repertoireDao().observePieces().first().map { it.title })
        assertEquals(listOf("2026-09-13"), reopened.practiceDao().observeAll().first().map { it.date })
        assertEquals(emptyList(), reopened.backingDao().backings().first())
        reopened.close()
    }

    @Test
    fun `a mark that cannot be left is a failure and not a silent success`() = runTest {
        val database = IosStorage.database(directory)
        val data = PlatformFile(directory)
        val store = storeOf(data, database, RoomPracticeRepository(database.practiceDao()))
        // a folder where the mark goes: the file cannot be made
        data.child(IosRestoreSwap.READY_MARK).makeDirectories()
        data.child(IosRestoreSwap.WIPE_MARK).makeDirectories()
        store.newStaging()
        assertFailsWith<okio.IOException> { store.markStagingReady() }
        assertFailsWith<okio.IOException> { store.markWipe() }
        database.close()
    }

    @Test
    fun `an unpacked copy is opened before its mark and carries the date of its copy`() = runTest {
        val data = PlatformFile(directory)
        // what a copy from an older Android app carries: its database at version 12, and settings of its own
        val older = data.child("older.db")
        OldDatabaseFile.layOut(older.path, version = 12, madeOnAndroid = true)
        val copied = data.child("copied-settings").also { it.makeDirectories() }
        val life = CoroutineScope(Dispatchers.Default + Job())
        DataStoreSettingsRepository(IosStorage.settings(copied.path, life)).setOnboardingDone(true)
        life.coroutineContext[Job]!!.cancelAndJoin()
        val settings = copied.child(DataLayout.SETTINGS_FILE)
        val manifest = BackupManifest(
            formatVersion = BackupManifest.FORMAT_VERSION, appVersion = "1.0", databaseVersion = 12, createdAtEpochMs = 1_790_000_000_000,
            device = "Google Pixel 10a", parts = setOf(BackupPart.DATA), counts = BackupCounts(sessions = 1), bytes = mapOf(BackupPart.DATA to older.sizeBytes()),
        )
        val archive = data.child("copy.zip")
        BackupWriter.write(
            archive.openOutput()!!, manifest,
            listOf(
                BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, older.sizeBytes()) { older.openInput() },
                BackupEntry("${BackupPaths.SETTINGS}/${DataLayout.SETTINGS_FILE}", BackupPart.DATA, settings.sizeBytes()) { settings.openInput() },
            ),
        ) {}
        val database = IosStorage.database(directory)
        val store = storeOf(data, database, RoomPracticeRepository(database.practiceDao()))
        BackupReader.extract(archive.openInput()!!, store.newStaging(), 0) {}
        store.settleStaging(manifest.createdAtEpochMs)

        val staged = data.child(IosRestoreSwap.STAGING).child(BackupPaths.DATABASE).child(AppDatabase.FILE_NAME)
        assertEquals(AppDatabase.VERSION, userVersionOf(staged), "the migrations have run on the unpacked file")
        assertFalse(data.child(IosRestoreSwap.STAGING).child(BackupPaths.DATABASE).child(AppDatabase.FILE_NAME + "-wal").exists())
        val readLife = CoroutineScope(Dispatchers.Default + Job())
        val stamped = IosStorage.settings(data.child(IosRestoreSwap.STAGING).child(BackupPaths.SETTINGS).path, readLife).data.first()
        readLife.coroutineContext[Job]!!.cancelAndJoin()
        assertEquals(manifest.createdAtEpochMs, stamped[longPreferencesKey("backup_last_at")])
        assertEquals(true, stamped[booleanPreferencesKey("onboarding_done")])

        store.markStagingReady()
        database.close()
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        val reopened = IosStorage.database(directory)
        assertEquals("Гаммы", reopened.sessionDao().observeAll().first().single().title)
        reopened.close()
    }

    @Test
    fun `an unpacked database that does not open fails before its mark`() = runTest {
        val data = PlatformFile(directory)
        val database = IosStorage.database(directory)
        val store = storeOf(data, database, RoomPracticeRepository(database.practiceDao()))
        val folder = store.newStaging().child(BackupPaths.DATABASE).also { it.makeDirectories() }
        folder.child(AppDatabase.FILE_NAME).openOutput()!!.use { it.write(ByteArray(8_192) { 7 }, 0, 8_192) }
        assertFails { store.settleStaging(1_790_000_000_000) }
        database.close()
    }

    // spec 5.9: an edit is a change — «Сохранить» without one, or the status the piece already has, does not lift it up the list
    @Test
    fun `an edit that changes nothing and the status it already has leave the last activity`() = runTest {
        val database = IosStorage.database(directory)
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics())
        val minuet = PieceDraft(title = "Менуэт", composer = "Бах", tempoBpm = 96, status = PieceStatus.READING, notes = "Такты 9–12")
        val id = repertoire.add(minuet, nowEpochMs = 10)
        repertoire.update(id, minuet.copy(title = " Менуэт ", notes = "Такты 9–12  "), nowEpochMs = 20)
        assertEquals(10L, repertoire.piece(id)!!.updatedAtEpochMs, "the same fields, stray spaces aside")
        repertoire.setStatus(id, PieceStatus.READING, nowEpochMs = 30)
        assertEquals(10L, repertoire.piece(id)!!.updatedAtEpochMs, "the status it already has")
        repertoire.update(id, minuet.copy(tempoBpm = 100), nowEpochMs = 40)
        assertEquals(40L, repertoire.piece(id)!!.updatedAtEpochMs)
        repertoire.setStatus(id, PieceStatus.LEARNING, nowEpochMs = 50)
        assertEquals(50L, repertoire.piece(id)!!.updatedAtEpochMs)
        database.close()
    }

    /** `PRAGMA user_version` as SQLite keeps it: four bytes, big-endian, at offset 60 of the header. */
    private fun userVersionOf(file: PlatformFile): Int {
        val data = assertNotNull(NSData.dataWithContentsOfFile(file.path))
        val header = ByteArray(HEADER)
        header.usePinned { memcpy(it.addressOf(0), data.bytes, HEADER.toULong()) }
        return (60 until 64).fold(0) { value, i -> (value shl 8) or (header[i].toInt() and 0xFF) }
    }

    private companion object {
        const val HEADER = 100
    }
}
