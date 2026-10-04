package com.violinjourney.app.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupReader
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
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
import com.violinjourney.app.core.recording.audio.FakePickedSounds
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
import kotlinx.coroutines.test.runCurrent
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
        FakeVideoFiles(), FakeFileTakeAnalyzer(), FakeSessionRepository(), FakeSessionWaveforms(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), FakeRunningPracticeStore(),
        PracticeConfig(), RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    )

    private fun TestScope.audioImporter() = AudioTakeImporter(
        FakePickedSounds(), FakeFileTakeAnalyzer(), FakeSessionRepository(), FakeSessionWaveforms(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()),
        RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
        StandardTestDispatcher(testScheduler), analytics = NoOpAnalytics(),
    )

    private fun TestScope.backupScreen(manager: BackupManager, savedState: SavedStateHandle = SavedStateHandle()) =
        BackupViewModel(manager, store, BackupConfig(), watch, importer(), audioImporter(), savedState)

    /** The block «Данные» over the same manager and the same date of the last copy; it reads while something watches it. */
    private fun TestScope.dataBlock(manager: BackupManager) =
        DataBlockViewModel(manager, prefs, FakeSessionRepository(), store, BackupConfig(), clock).also { block ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block.state.collect {} }
        }

    private fun TestScope.restoreScreen(manager: BackupManager, uri: String?) =
        RestoreViewModel(manager, store, watch, importer(), audioImporter(), SavedStateHandle(listOfNotNull(uri?.let { RestoreViewModel.ARG_URI to it }).toMap()))

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
    fun `a copy waits while a sound from a file is on its way in, as for a video`() = runTest {
        // plan D37: the copy of the data takes the files of the recordings, and a file being copied in is not one yet
        val sounds = audioImporter()
        val screen = BackupViewModel(manager(), store, BackupConfig(), watch, importer(), sounds, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { screen.state.collect {} }
        runCurrent()
        assertFalse(screen.state.value.busy)
        sounds.picked(TakeOwner.Event(1), "content://audio/1")
        runCurrent()
        assertTrue("the copy waits for the sound", screen.state.value.busy)
        advanceUntilIdle()
        assertFalse("and goes on once it is in", screen.state.value.busy)
    }

    @Test
    fun `a sound that failed does not hold the copy back - its copy is gone already`() = runTest {
        // review of stage 98a: a failure only the screen of its event shows would hold the copy for good once that screen is left
        val picked = FakePickedSounds().apply { probe = null }
        val sounds = AudioTakeImporter(
            picked, FakeFileTakeAnalyzer(), FakeSessionRepository(), FakeSessionWaveforms(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()),
            RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
            StandardTestDispatcher(testScheduler), analytics = NoOpAnalytics(),
        )
        val screen = BackupViewModel(manager(), store, BackupConfig(), watch, importer(), sounds, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { screen.state.collect {} }
        sounds.picked(TakeOwner.Event(1), "content://audio/1")
        advanceUntilIdle()
        assertTrue(sounds.state.value is MediaImport.Failed)
        assertFalse("a failure is no work", screen.state.value.busy)
        val restore = RestoreViewModel(manager(), store, watch, importer(), sounds, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restore.state.collect {} }
        runCurrent()
        assertFalse(restore.state.value.busy)
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

    // A screen of a copy opened anew — by «Копия сохраняется», by the notification, after a failure in the background — takes the choice
    // from the job itself (spec 3.36.8): the screen that made it is gone, and with it the switches it had.

    @Test
    fun `a screen opened again on a failed copy retries the same parts`() = runTest {
        val manager = manager()
        val first = backupScreen(manager)
        advanceUntilIdle()
        first.onIntent(BackupIntent.PartToggled(BackupPart.VIDEO))
        // the snapshot of the database finds the phone full
        store.prepareFails = RuntimeException("database or disk is full (code 13 SQLITE_FULL)")
        first.onIntent(BackupIntent.PlacePicked("content://downloads/1", "копия.zip"))
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.SaveFailed)
        store.prepareFails = null

        val again = backupScreen(manager)
        val effects = collected(again.effects)
        advanceUntilIdle()
        val withoutVideo = BackupPart.entries.toSet() - BackupPart.VIDEO
        assertEquals("the switches are those of the copy that failed", withoutVideo, again.state.value.parts)
        again.onIntent(BackupIntent.RetryClicked)
        assertEquals(listOf<BackupEffect>(BackupEffect.PickPlace), effects)
        again.onIntent(BackupIntent.PlacePicked("content://downloads/2", "копия.zip"))
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        // «Ещё раз» repeated that copy: without the video, as it was chosen
        val retried = BackupReader.manifest(documents.openInput("content://downloads/2")!!, knownDatabase = 6)
        assertEquals(withoutVideo, retried.parts)
        assertTrue("no video in the archive", documents.written.getValue("content://downloads/2").size() < 5_000)
    }

    @Test
    fun `a screen opened again on a running copy shows its choice`() = runTest {
        val manager = manager()
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/1", setOf(BackupPart.DATA, BackupPart.SHEETS), "копия.zip")
        val again = backupScreen(manager)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saving)
        assertEquals(setOf(BackupPart.DATA, BackupPart.SHEETS), again.state.value.parts)
        // the switches are shut while it goes: what the job made stays what the screen says
        again.onIntent(BackupIntent.PartToggled(BackupPart.VIDEO))
        assertEquals(setOf(BackupPart.DATA, BackupPart.SHEETS), again.state.value.parts)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
    }

    // A press belongs to the face it was made on (the lessons of stages 119 and 120; review of stage 122): a screen on its way out takes
    // no more presses, and «Ещё раз» acts once, on the failure it was pressed on.

    @Test
    fun `the screen of a copy takes no press once it is closing`() = runTest {
        val manager = manager()
        val screen = backupScreen(manager)
        val effects = collected(screen.effects)
        advanceUntilIdle()
        screen.onIntent(BackupIntent.PlacePicked("content://downloads/1", "копия.zip"))
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        // two taps of «Готово» before the next frame; then what the fading screen would have under the finger, were it to show the live
        // state — «Отправить…» of the choice under «Готово», «Сохранить в…» under «Ещё раз»
        screen.onIntent(BackupIntent.DoneClicked)
        screen.onIntent(BackupIntent.DoneClicked)
        screen.onIntent(BackupIntent.ShareClicked("копия.zip"))
        screen.onIntent(BackupIntent.SaveClicked)
        advanceUntilIdle()
        assertEquals("one close: the screen under it stays", listOf<BackupEffect>(BackupEffect.Close), effects)
        assertTrue("the face is held while the screen fades", screen.state.value.closing)
        assertEquals("no copy behind a screen that is going", BackupJob.Idle, manager.job.value)
    }

    @Test
    fun `again acts once on the failure it was pressed on`() = runTest {
        val manager = manager()
        val screen = backupScreen(manager)
        val effects = collected(screen.effects)
        advanceUntilIdle()
        store.prepareFails = RuntimeException("database or disk is full (code 13 SQLITE_FULL)")
        screen.onIntent(BackupIntent.PlacePicked("content://downloads/1", "копия.zip"))
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.SaveFailed)
        // the second tap comes before the next frame: the failure is read already, and one «Сохранить как…» is enough
        screen.onIntent(BackupIntent.RetryClicked)
        screen.onIntent(BackupIntent.RetryClicked)
        assertEquals(listOf<BackupEffect>(BackupEffect.PickPlace), effects)
        assertEquals(BackupJob.Idle, manager.job.value)
    }

    // The answer of «Сохранить как…» comes to a screen made anew under the picker — the activity or the process gone while the place was
    // picked — as soon as it is composed, before what is in the app is counted (review of stage 122): it waits for the counting, and the
    // copy is the one that was chosen. Dropped, it left the empty file the system had made and no copy.

    @Test
    fun `a place picked for a screen made anew under the picker saves the copy chosen once the parts are counted`() = runTest {
        val manager = manager()
        val saved = SavedStateHandle()
        val first = backupScreen(manager, saved)
        advanceUntilIdle()
        first.onIntent(BackupIntent.PartToggled(BackupPart.VIDEO))
        // «Сохранить в…»; while the place is picked the screen goes, and a new one is made from what the old one saved
        val counting = CompletableDeferred<Unit>()
        store.contentsGate = counting
        val again = backupScreen(manager, SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        val withoutVideo = BackupPart.entries.toSet() - BackupPart.VIDEO
        assertEquals("the choice outlives the screen", withoutVideo, again.state.value.parts)
        again.onIntent(BackupIntent.PlacePicked("content://downloads/1", "копия.zip"))
        advanceUntilIdle()
        assertNull("the parts are still being counted", again.state.value.contents)
        assertEquals("nothing starts before they are", BackupJob.Idle, manager.job.value)
        counting.complete(Unit)
        advanceUntilIdle()
        assertTrue("the place picked is written once they are", manager.job.value is BackupJob.Saved)
        val written = BackupReader.manifest(documents.openInput("content://downloads/1")!!, knownDatabase = 6)
        assertEquals("without the video, as it was chosen", withoutVideo, written.parts)
        assertTrue("no video in the archive", documents.written.getValue("content://downloads/1").size() < 5_000)
    }

    // «Сначала сохранить текущие данные» opens the copy once (the lead's fix of stage 122, verified): a second tap that reaches it before
    // the copy is on screen finds this screen stopped by the navigation already — an effect sent then would wait for it, and open the
    // copy again after «назад».

    @Test
    fun `the safety net opens the copy once until the passport is shown again`() = runTest {
        val manager = manager()
        savedCopy(manager)
        val screen = restoreScreen(manager, "content://downloads/1")
        val effects = collected(screen.effects)
        advanceUntilIdle()
        assertTrue(screen.state.value.stage is RestoreStage.Ready)
        screen.onIntent(RestoreIntent.SaveFirstClicked)
        screen.onIntent(RestoreIntent.SaveFirstClicked)
        assertEquals("one copy for a double tap", listOf<RestoreEffect>(RestoreEffect.OpenBackup), effects)
        // back from the copy: the passport is in front again, and the safety net answers
        screen.onIntent(RestoreIntent.ScreenShown)
        screen.onIntent(RestoreIntent.SaveFirstClicked)
        assertEquals(listOf<RestoreEffect>(RestoreEffect.OpenBackup, RestoreEffect.OpenBackup), effects)
    }

    /**
     * An app with only a calendar (review of stage 98б): the copy offers to save it — not «Пока нечего сохранять» — and a restore over it
     * asks «Заменить данные?» first instead of replacing it silently (spec 3.20, item 2).
     */
    @Test
    fun `a calendar alone is data - the copy saves it and a restore over it asks first`() = runTest {
        val calendar = FakeBackupStore(folder.newFolder(), now, BackupCounts(events = 3))
        val manager = BackupManager(
            calendar, documents, prefs, {}, BackupConfig(), clock, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
            analytics = NoOpAnalytics(),
        )
        val copy = BackupViewModel(manager, calendar, BackupConfig(), watch, importer(), audioImporter(), SavedStateHandle())
        val picks = collected(copy.effects)
        advanceUntilIdle()
        assertFalse("not «nothing to save»", copy.state.value.nothingToSave)
        copy.onIntent(BackupIntent.SaveClicked)
        assertEquals(listOf<BackupEffect>(BackupEffect.PickPlace), picks)
        manager.saveTo("content://downloads/1", BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        manager.dismiss()

        val restore = RestoreViewModel(manager, calendar, watch, importer(), audioImporter(), SavedStateHandle(mapOf(RestoreViewModel.ARG_URI to "content://downloads/1")))
        advanceUntilIdle()
        assertFalse((restore.state.value.stage as RestoreStage.Ready).current.counts.isEmpty)
        restore.onIntent(RestoreIntent.RestoreClicked)
        advanceUntilIdle()
        assertEquals("asked first", RestoreDialog.REPLACE, restore.state.value.dialog)
        assertEquals("nothing replaced yet", BackupJob.Idle, manager.job.value)
    }

    @Test
    fun `the screen of a restore takes no press once it is closing`() = runTest {
        // An empty app — the main way in, from «У меня есть копия данных»: «Восстановить» there asks nothing
        val empty = FakeBackupStore(folder.newFolder(), now, BackupCounts())
        val manager = BackupManager(
            empty, documents, prefs, {}, BackupConfig(), clock, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
            analytics = NoOpAnalytics(),
        )
        manager.saveTo("content://downloads/1", BackupPart.entries.toSet(), "копия.zip")
        advanceUntilIdle()
        manager.dismiss()
        val screen = RestoreViewModel(manager, empty, watch, importer(), audioImporter(), SavedStateHandle(mapOf(RestoreViewModel.ARG_URI to "content://downloads/1")))
        val effects = collected(screen.effects)
        advanceUntilIdle()
        assertTrue((screen.state.value.stage as RestoreStage.Ready).current.counts.isEmpty)
        // the file is cut short after its passport was read: the restore fails with the data in place
        val whole = documents.written.getValue("content://downloads/1").toByteArray()
        documents.written["content://downloads/1"] = ByteArrayOutputStream().also { it.write(whole, 0, whole.size / 2) }
        screen.onIntent(RestoreIntent.RestoreClicked)
        advanceUntilIdle()
        assertTrue((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        documents.written["content://downloads/1"] = ByteArrayOutputStream().also { it.write(whole) }
        // «Закрыть», and a second tap where the passport of the live state would put «Восстановить»
        screen.onIntent(RestoreIntent.CloseClicked)
        screen.onIntent(RestoreIntent.RestoreClicked)
        screen.onIntent(RestoreIntent.CloseClicked)
        advanceUntilIdle()
        assertEquals("no restore behind a screen that is going", BackupJob.Idle, manager.job.value)
        assertEquals(listOf<RestoreEffect>(RestoreEffect.Close), effects)
        assertTrue("the face is held while the screen fades", screen.state.value.closing)
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
