package com.violinjourney.app.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.FakeBackupDocuments
import com.violinjourney.app.core.backup.FakeBackupPrefs
import com.violinjourney.app.core.backup.FakeBackupStore
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.video.AnalysisSpeed
import com.violinjourney.app.core.recording.video.FakeFileTakeAnalyzer
import com.violinjourney.app.core.recording.video.FakeVideoFiles
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.time.FixedWallClock
import java.io.ByteArrayOutputStream
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The screens of a copy over the real manager on fakes: what the person presses, and what the manager does with it (spec 3.20). */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelsTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = Instant.parse("2026-09-20T11:32:00Z")
    private val counts = BackupCounts(sessions = 23, pieces = 5, practiceDays = 41, level = 4, withSound = 19, videos = 6)
    private val store by lazy { FakeBackupStore(folder.root, now, counts) }
    private val documents = FakeBackupDocuments()
    private val prefs = FakeBackupPrefs()
    private val watch = RecordingWatch()
    private val clock = FixedWallClock(now, TimeZone.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.manager() = BackupManager(
        store, documents, prefs, {}, BackupConfig(), clock, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    )

    private fun TestScope.importer() = VideoTakeImporter(
        FakeVideoFiles(), FakeFileTakeAnalyzer(), FakeSessionRepository(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), FakeRunningPracticeStore(),
        PracticeConfig(), RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    )

    private fun TestScope.backupScreen(manager: BackupManager) = BackupViewModel(manager, store, BackupConfig(), watch, importer())

    /** The block «Данные» over the same manager and the same date of the last copy; it reads while something watches it. */
    private fun TestScope.dataBlock(manager: BackupManager) =
        DataBlockViewModel(manager, prefs, FakeSessionRepository(), store, BackupConfig(), clock).also { block ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        }

    private fun TestScope.restoreScreen(manager: BackupManager, uri: String?) =
        RestoreViewModel(manager, store, watch, importer(), SavedStateHandle(listOfNotNull(uri?.let { RestoreViewModel.ARG_URI to it }).toMap()))

    private fun <T> TestScope.collected(effects: Flow<T>): List<T> = mutableListOf<T>().also { got ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { effects.collect { got += it } }
    }

    private suspend fun TestScope.savedCopy(manager: BackupManager, uri: String = "content://downloads/1"): BackupCandidate.Copy {
        manager.saveTo(uri, BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        manager.dismiss()
        return manager.inspect(uri) as BackupCandidate.Copy
    }

    @Test
    fun `the stop dialog goes as soon as the restore can no longer be stopped`() = runTest {
        // the screen sees every state the moment the manager sets it
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val manager = manager()
        val copy = savedCopy(manager)
        val screen = restoreScreen(manager, copy.uri)
        var dialogWhileMediaGo: RestoreDialog? = RestoreDialog.REPLACE
        // «Остановить» pressed while the copy is checked; the confirmation is still open when the media start to go
        store.onSettle = { screen.onIntent(RestoreIntent.CancelClicked) }
        store.onDeleteMedia = { dialogWhileMediaGo = screen.state.value.dialog }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertNull("«Остановить?» is gone once there is nothing left to stop", dialogWhileMediaGo)
        assertTrue(manager.job.value is BackupJob.Restored)
    }

    @Test
    fun `the system back from the saved copy leaves the way to a restore open`() = runTest {
        val manager = manager()
        val backup = backupScreen(manager)
        val closes = collected(backup.effects)
        advanceUntilIdle()
        backup.onIntent(BackupIntent.PlacePicked("content://downloads/1", "копия.zip"))
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        // «Сначала сохранить текущие данные» → the copy → the system «Назад», not «Готово»
        backup.onIntent(BackupIntent.BackClicked)
        assertEquals(BackupJob.Idle, manager.job.value)
        assertEquals(listOf(BackupEffect.Close), closes)

        val restore = restoreScreen(manager, "content://downloads/1")
        advanceUntilIdle()
        assertTrue(restore.state.value.stage is RestoreStage.Ready)
        restore.onIntent(RestoreIntent.RestoreClicked)
        assertEquals(RestoreDialog.REPLACE, restore.state.value.dialog)
        restore.onIntent(RestoreIntent.DialogConfirmed)
        assertTrue(manager.job.value is BackupJob.Restoring)
    }

    @Test
    fun `a restore does not start silently under a copy on its way`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/2", BackupPart.entries.toSet(), "копия.zip")
        val restore = restoreScreen(manager, copy.uri)
        advanceUntilIdle()
        assertTrue(restore.state.value.savingCopy)
        restore.onIntent(RestoreIntent.RestoreClicked)
        restore.onIntent(RestoreIntent.UnsafeClicked)
        assertNull(restore.state.value.dialog)
        assertTrue(manager.job.value is BackupJob.Saving)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(restore.state.value.savingCopy)
    }

    @Test
    fun `a share archive swept away is not handed to the sheet again`() = runTest {
        val manager = manager()
        val first = backupScreen(manager)
        val sent = collected(first.effects)
        advanceUntilIdle()
        first.onIntent(BackupIntent.ShareClicked("копия.zip"))
        advanceUntilIdle()
        val file = (manager.job.value as BackupJob.Saved).shareFile!!
        assertEquals(listOf(BackupEffect.ShareFile(file.path)), sent)
        // the screen comes back over the same outcome after the sweep of `cache/share/`
        file.delete()
        val again = backupScreen(manager)
        val sentAgain = collected(again.effects)
        advanceUntilIdle()
        assertTrue(sentAgain.none { it is BackupEffect.ShareFile })
    }

    @Test
    fun `a screen opened to watch a restore retries the file that failed`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.RestoreFailed)
        // from the notification: no file, no passport of its own
        val watching = restoreScreen(manager, uri = null)
        val closes = collected(watching.effects)
        advanceUntilIdle()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes) }
        watching.onIntent(RestoreIntent.RetryClicked)
        assertTrue(manager.job.value is BackupJob.Restoring)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Restored)
        assertTrue(closes.none { it == RestoreEffect.Close })
    }

    @Test
    fun `a screen opened to watch a restore closes once when there is none`() = runTest {
        val manager = manager()
        val watching = restoreScreen(manager, uri = null)
        val closes = collected(watching.effects)
        advanceUntilIdle()
        assertEquals(listOf(RestoreEffect.Close), closes)

        val copy = savedCopy(manager)
        documents.written[copy.uri] = ByteArrayOutputStream()
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        val other = restoreScreen(manager, uri = null)
        val otherCloses = collected(other.effects)
        advanceUntilIdle()
        assertTrue(otherCloses.isEmpty())
        other.onIntent(RestoreIntent.CloseClicked)
        advanceUntilIdle()
        assertEquals(listOf(RestoreEffect.Close), otherCloses)
    }

    @Test
    fun `the way without a net is weighed against the media it would free`() {
        val megabyte = 1L shl 20
        val gigabyte = 1L shl 30
        val margin = BackupConfig().freeSpaceMarginBytes
        // a copy of 2 GB of videos and 40 MB of data
        val manifest = store.manifest(BackupPart.entries.toSet()).copy(bytes = mapOf(BackupPart.DATA to 40 * megabyte, BackupPart.VIDEO to 2 * gigabyte))
        fun ready(free: Long, media: Long): RestoreStage.Ready {
            val missing = (manifest.totalBytes + margin - free).coerceAtLeast(0)
            return RestoreStage.Ready(BackupCandidate.Copy("u", null, null, manifest, missing), BackupContents(counts, mapOf(BackupPart.DATA to 5_000_000L, BackupPart.VIDEO to media)))
        }
        val missing = ready(free = gigabyte, media = 0).copy.missingBytes
        assertEquals(missing - gigabyte / 2, ready(free = gigabyte, media = gigabyte / 2).missingEvenUnsafeBytes)
        assertEquals(0L, ready(free = gigabyte, media = 3 * gigabyte).missingEvenUnsafeBytes)
        // an empty app has nothing to free: what is missing stays missing
        assertEquals(missing, ready(free = gigabyte, media = 0).missingEvenUnsafeBytes)
        // the data of the copy are unpacked before the media go: however much the media free, the data need their room now
        assertEquals(40 * megabyte + margin - 60 * megabyte, ready(free = 60 * megabyte, media = 3 * gigabyte).missingEvenUnsafeBytes)
        // with room for the whole copy there is no second way to weigh
        assertEquals(0L, ready(free = 3 * gigabyte, media = 0).missingEvenUnsafeBytes)
    }

    @Test
    fun `the way without a net is offered only when the media make room`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val margin = BackupConfig().freeSpaceMarginBytes
        // room for the data of the copy, not for the whole of it
        store.free = margin + 200_000
        store.mediaBytes = 1_000
        val short = restoreScreen(manager, copy.uri)
        advanceUntilIdle()
        assertTrue((short.state.value.stage as RestoreStage.Ready).missingEvenUnsafeBytes > 0)
        short.onIntent(RestoreIntent.UnsafeClicked)
        assertNull(short.state.value.dialog)

        store.mediaBytes = 1L shl 30
        val roomy = restoreScreen(manager, copy.uri)
        advanceUntilIdle()
        roomy.onIntent(RestoreIntent.UnsafeClicked)
        assertEquals(RestoreDialog.UNSAFE, roomy.state.value.dialog)

        // the media would make room for everything but the data of the copy, which are unpacked while they are still there
        store.free = margin
        val noRoomForData = restoreScreen(manager, copy.uri)
        advanceUntilIdle()
        assertTrue((noRoomForData.state.value.stage as RestoreStage.Ready).missingEvenUnsafeBytes > 0)
        noRoomForData.onIntent(RestoreIntent.UnsafeClicked)
        assertNull(noRoomForData.state.value.dialog)
    }

    // «Данные» of «Настройки» (spec 3.36.8): the date not read is not «ещё не сохраняли», and one job at a time is seen before a touch.

    @Test
    fun `the date not read yet is not never-saved`() = runTest {
        val manager = manager()
        val block = DataBlockViewModel(manager, prefs, FakeSessionRepository(), store, BackupConfig(), clock)
        // nothing has been read: not «ещё не сохраняли» — the caption keeps its line empty
        assertFalse(block.state.value.dateRead)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        advanceUntilIdle()
        assertTrue(block.state.value.dateRead)
        assertNull("read, and there is none: «ещё не сохраняли»", block.state.value.lastBackupAtEpochMs)
        // a copy saved — the same row now says its date
        savedCopy(manager)
        advanceUntilIdle()
        assertEquals(now.toEpochMilliseconds(), block.state.value.lastBackupAtEpochMs)
    }

    @Test
    fun `a copy on its way turns the row into its progress and puts the restore to sleep`() = runTest {
        val manager = manager()
        val block = dataBlock(manager)
        advanceUntilIdle()
        assertNull(block.state.value.running)
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/2", BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        val during = block.state.value
        assertEquals(DataRunning(restore = false, percent = 0, phase = JobPhase.Data), during.running)
        assertTrue("«Восстановить из копии» waits for the copy", during.restoreWaits)
        assertFalse(during.saveWaits)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        assertNull("the copy is saved: nothing runs, nothing waits", block.state.value.running)
        assertFalse(block.state.value.restoreWaits)
    }

    @Test
    fun `a restore on its way puts the copy to sleep and says its step`() = runTest {
        // the block sees every state the moment the manager sets it, as the screen of the restore does
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val manager = manager()
        val copy = savedCopy(manager)
        val block = dataBlock(manager)
        var during: DataBlockState? = null
        store.onSettle = { during = block.state.value }
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        val seen = checkNotNull(during) { "the restore settled its copy" }
        assertEquals(true, seen.running?.restore)
        assertEquals(JobPhase.Restore(RestorePhase.EXTRACTING), seen.running?.phase)
        assertTrue("«Сохранить копию» waits for the restore", seen.saveWaits)
        assertFalse(seen.restoreWaits)
        assertTrue(manager.job.value is BackupJob.Restored)
        // unpacked and marked, the restore waits for its restart: still on its way — the copy still waits, the row leads to the screen
        // that restarts the app
        val restored = block.state.value
        assertEquals(DataRunning(restore = true, percent = 100, phase = JobPhase.Restore(RestorePhase.FINISHING)), restored.running)
        assertTrue("«Сохранить копию» waits for the restart", restored.saveWaits)
    }

    // The rows of «Данные» stand from the first frame as they are (spec 3.36.8 «Загрузка»: «Ничего не мигает»).

    /** Recordings not read in the test's time: Room, with the file of every recording with sound to look at, on a busy phone. */
    private class SilentSessions : SessionRepository by FakeSessionRepository() {
        override val sessions: Flow<List<SessionSummary>> = MutableSharedFlow()
    }

    @Test
    fun `a block opened while a copy is on its way has it in its first value before the slow half is read`() = runTest {
        val manager = manager()
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/2", BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saving)
        // «назад» from the copy, «назад» from Live, the gear: a new block, and Room has not answered
        val block = DataBlockViewModel(manager, prefs, SilentSessions(), store, BackupConfig(), clock)
        val first = block.state.value
        assertEquals(DataRunning(restore = false, percent = 0, phase = JobPhase.Data), first.running)
        assertTrue("«Восстановить из копии» asleep from the first frame", first.restoreWaits)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        advanceUntilIdle()
        assertFalse("Room still silent: the date is not read", block.state.value.dateRead)
        assertEquals("the copy still shown while Room is silent", first.running, block.state.value.running)
        // the copy ends while Room is still silent: the rows follow the job without waiting for it
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        assertNull(block.state.value.running)
        assertFalse(block.state.value.restoreWaits)
    }

    @Test
    fun `a block returned to after a while starts from the job as it is now`() = runTest {
        val manager = manager()
        val block = DataBlockViewModel(manager, prefs, FakeSessionRepository(), store, BackupConfig(), clock)
        val settings = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        advanceUntilIdle()
        assertNull(block.state.value.running)
        // the screen of a copy over «Настройки»: nothing watches the block for longer than a sharing that stops would wait
        settings.cancel()
        advanceTimeBy(UNWATCHED_MS)
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/2", BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        // back: the first frame reads the block before anything collects it again
        assertEquals(DataRunning(restore = false, percent = 0, phase = JobPhase.Data), block.state.value.running)
        assertTrue(block.state.value.restoreWaits)
        // and the other way round: the copy saved while nothing watched — no copy on its way, and its date
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(block.state.value.running)
        assertEquals(now.toEpochMilliseconds(), block.state.value.lastBackupAtEpochMs)
    }

    // An old copy (spec 3.20: «копии больше 30 дней и с тех пор появились записи») says how many recordings came after it.

    /** A recording started at [startedAt]. */
    private fun recording(id: Long, startedAt: Long) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 0, offPercent = 20, maeCents = 0.0, biasCents = 0.0, previewZones = emptyList(), audioPath = null,
    )

    private fun daysBeforeNow(days: Long): Long = now.toEpochMilliseconds() - days * DAY_MS

    /** The block over [recordings] and a copy made at [copyAt], at [clock], read. */
    private fun TestScope.blockOver(copyAt: Long, recordings: List<SessionSummary>, at: FixedWallClock = clock): DataBlockState {
        prefs.lastBackupAtEpochMs.value = copyAt
        val sessions = FakeSessionRepository().also { it.sessions.value = recordings }
        val block = DataBlockViewModel(manager(), prefs, sessions, store, BackupConfig(), at)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        advanceUntilIdle()
        return block.state.value
    }

    @Test
    fun `an old copy counts the recordings started after it`() = runTest {
        val copyAt = daysBeforeNow(31)
        // one before the copy and one of its very moment are in it; two came after
        val recordings = listOf(recording(1, daysBeforeNow(40)), recording(2, copyAt), recording(3, daysBeforeNow(10)), recording(4, daysBeforeNow(1)))
        assertEquals(2, blockOver(copyAt, recordings).newSinceStale)
    }

    @Test
    fun `a copy of thirty days is not old yet and of thirty one is`() = runTest {
        // «больше 30 дней»: whole days — thirty to the millisecond are not more
        val copyAt = daysBeforeNow(30)
        val recordings = listOf(recording(1, daysBeforeNow(1)))
        assertEquals(0, blockOver(copyAt, recordings).newSinceStale)
        val dayLater = FixedWallClock(Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + DAY_MS), TimeZone.UTC)
        assertEquals(1, blockOver(copyAt, recordings, at = dayLater).newSinceStale)
    }

    @Test
    fun `an old copy with nothing after it says nothing more`() = runTest {
        val copyAt = daysBeforeNow(45)
        assertEquals(0, blockOver(copyAt, listOf(recording(1, daysBeforeNow(50)))).newSinceStale)
        assertEquals(0, blockOver(copyAt, emptyList()).newSinceStale)
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L

        /** Longer than a sharing that stops when nothing watches waits (5 s): what the block had then must not be what it shows. */
        const val UNWATCHED_MS = 10_000L
    }
}
