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
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val watch = RecordingWatch()
    private val clock = FixedWallClock(now, TimeZone.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.manager() = BackupManager(
        store, documents, FakeBackupPrefs(), {}, BackupConfig(), clock, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    )

    private fun TestScope.importer() = VideoTakeImporter(
        FakeVideoFiles(), FakeFileTakeAnalyzer(), FakeSessionRepository(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), FakeRunningPracticeStore(),
        PracticeConfig(), RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    )

    private fun TestScope.backupScreen(manager: BackupManager) = BackupViewModel(manager, store, BackupConfig(), watch, importer())

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
}
