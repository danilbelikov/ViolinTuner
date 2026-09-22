package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.data.repertoire.FakeSheetFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.FakeSoundRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.domain.sound.SoundSettings
import com.example.violintuner.core.recording.TakePipeline
import com.example.violintuner.core.recording.video.AnalysisSpeed
import com.example.violintuner.core.recording.video.FakeFileTakeAnalyzer
import com.example.violintuner.core.recording.video.FakeVideoFiles
import com.example.violintuner.core.recording.video.VideoImport
import com.example.violintuner.core.recording.video.VideoTakeImporter
import com.example.violintuner.core.settings.FakeSettingsRepository
import com.example.violintuner.core.settings.SettingsConfigSource
import com.example.violintuner.feature.history.Selection
import com.example.violintuner.feature.history.SelectionIntent
import com.example.violintuner.feature.repertoire.piece.PieceEffect
import com.example.violintuner.feature.repertoire.piece.PieceIntent
import com.example.violintuner.feature.repertoire.piece.PieceViewModel
import com.example.violintuner.feature.repertoire.piece.TakeProblem
import com.example.violintuner.feature.sound.SoundCaption
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.testTimeSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PieceViewModelTest {
    private val repertoire = FakeRepertoireRepository()
    private val files = FakeSheetFiles()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)
    private val sessions = FakeSessionRepository()
    private val sound = FakeSoundRepository()
    private val practice = FakeRunningPracticeStore()

    /** The fake source has no sound to write, so nothing here is ever asked for a file. */
    private object NoAudioFiles : SessionAudioFiles {
        override fun newFile(): File = error("the fake source records no sound")
        override fun existing(name: String): File? = null
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    /** Counts how often it is listened to: a piece's screen may hold the microphone only while a take runs. */
    private class CountingSource(private val delegate: PitchSource, override val requiresMicPermission: Boolean = false) : PitchSource {
        var collections = 0
        var active = 0
        override val audioTap = null
        override fun frames(config: IntonationConfig): Flow<PitchFrame> =
            delegate.frames(config).onStart { collections++; active++ }.onCompletion { active-- }
    }

    private var source: CountingSource? = null

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val videoFiles = FakeVideoFiles()
    private val videoAnalyzer = FakeFileTakeAnalyzer()
    private var videoImporter: VideoTakeImporter? = null

    // one importer for all the screens of a test, as it is one for the app
    private fun TestScope.importer() = videoImporter ?: VideoTakeImporter(
        videoFiles, videoAnalyzer, sessions, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), practice, RepertoireConfig(), IntonationConfig(),
        clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
    ).also { videoImporter = it }

    private object NoShareFiles : ShareFiles {
        override fun processed(audioName: String, settings: SoundSettings, fileName: String) = File("/cache/share/$fileName")
        override suspend fun original(audio: File, fileName: String): File = File("/cache/share/$fileName")
        override suspend fun sweep(nowEpochMs: Long) = Unit
    }

    private fun TestScope.screen(pieceId: Long, saved: SavedStateHandle = SavedStateHandle(mapOf(PieceViewModel.ARG_PIECE_ID to pieceId))):
        Pair<PieceViewModel, MutableList<PieceEffect>> {
        val pitch = source ?: CountingSource(FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource)).also { source = it }
        val takes = TakePipeline(pitch, sessions, NoAudioFiles, practice, PracticeConfig(), clock, StandardTestDispatcher(testScheduler))
        val viewModel = PieceViewModel(
            saved, repertoire, files, RepertoireConfig(), clock, takes,
            SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), sessions,
            videoFiles, importer(), NoShareFiles,
        )
        val effects = mutableListOf<PieceEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.takeState.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `the screen shows the piece and follows its edits`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт", composer = "Бах", notes = "Такты 9–12"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        assertEquals("Менуэт", viewModel.state.value.header!!.title)
        assertEquals("Такты 9–12", viewModel.state.value.notes)

        repertoire.update(id, PieceDraft(title = "Гавот"), nowEpochMs = 2)
        runCurrent()
        assertEquals("Гавот", viewModel.state.value.header!!.title)
        assertEquals("", viewModel.state.value.header!!.composer)
    }

    @Test
    fun `picked photos become pages in the order they were picked, numbered from one`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        val copying = CompletableDeferred<Unit>()
        files.hold = copying
        viewModel.onIntent(PieceIntent.PhotosPicked(listOf("content://1", "content://2")))
        runCurrent()
        assertEquals("placeholders stand in the strip while the photos are copied", 2, viewModel.state.value.importing)
        assertTrue(viewModel.state.value.pages.isEmpty())
        copying.complete(Unit)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(0, state.importing)
        assertEquals(listOf(1, 2), state.pages.map { it.number })
        assertEquals(listOf("/sheets/page-1-thumb.jpg", "/sheets/page-2-thumb.jpg"), state.pages.map { it.thumbPath })
        assertEquals("adding a page is activity", 9_000L, repertoire.piece(id)!!.updatedAtEpochMs)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a photo that does not open is said once, and the others still come in`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.PhotosPicked(listOf("content://1", "content://broken", "content://broken-too", "content://4")))
        runCurrent()
        assertEquals(2, viewModel.state.value.pages.size)
        assertEquals(listOf<PieceEffect>(PieceEffect.ShowPhotoFailed), effects)
    }

    @Test
    fun `a camera shot goes in like a picked photo and its temporary file goes away`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.CameraClicked)
        runCurrent()
        val shot = files.cameraFiles.single()
        assertEquals(listOf<PieceEffect>(PieceEffect.LaunchCamera(shot.path)), effects)

        viewModel.onIntent(PieceIntent.CameraFinished(saved = true))
        runCurrent()
        assertEquals(1, viewModel.state.value.pages.size)
        assertFalse(shot.exists())
    }

    @Test
    fun `backing out of the camera adds nothing and leaves no file, and the pending shot survives a process death`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val saved = SavedStateHandle(mapOf(PieceViewModel.ARG_PIECE_ID to id))
        val (before, _) = screen(id, saved)
        before.onIntent(PieceIntent.CameraClicked)
        val shot: File = files.cameraFiles.single()

        // The camera app pushed us out of memory; a new view model gets the same saved state.
        val (after, _) = screen(id, saved)
        after.onIntent(PieceIntent.CameraFinished(saved = false))
        runCurrent()
        assertTrue(after.state.value.pages.isEmpty())
        assertFalse(shot.exists())

        after.onIntent(PieceIntent.CameraFinished(saved = true))
        runCurrent()
        assertTrue("a result nobody asked for is ignored", after.state.value.pages.isEmpty())
    }

    @Test
    fun `the status changes from the chip menu`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        viewModel.onIntent(PieceIntent.StatusChipClicked)
        runCurrent()
        assertTrue(viewModel.state.value.statusMenuOpen)
        viewModel.onIntent(PieceIntent.StatusSelected(PieceStatus.IN_REPERTOIRE))
        runCurrent()
        assertFalse(viewModel.state.value.statusMenuOpen)
        assertEquals(PieceStatus.IN_REPERTOIRE, viewModel.state.value.header!!.status)
    }

    @Test
    fun `buttons lead where they say`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.EditClicked)
        viewModel.onIntent(PieceIntent.AddNotesClicked)
        viewModel.onIntent(PieceIntent.PageClicked(3))
        viewModel.onIntent(PieceIntent.BackClicked)
        runCurrent()
        assertEquals(
            listOf(PieceEffect.OpenForm(id, false), PieceEffect.OpenForm(id, true), PieceEffect.OpenStand(id, 3), PieceEffect.Close),
            effects,
        )
    }

    @Test
    fun `a piece deleted under the screen closes it once`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        repertoire.delete(id)
        runCurrent()
        repertoire.add(PieceDraft(title = "Другое"), nowEpochMs = 2)
        runCurrent()
        assertEquals(listOf<PieceEffect>(PieceEffect.Close), effects)
        assertTrue(viewModel.state.value.loading)
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the screen does not listen until a take is asked for, and lets the microphone go after it`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        advance(1_000)
        assertEquals(0, source!!.collections)
        assertFalse(viewModel.takeState.value.recording)

        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_000)
        assertEquals(1, source!!.active)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(500)
        assertEquals(0, source!!.active)
        assertFalse(viewModel.takeState.value.recording)
    }

    @Test
    fun `a take is blind - a timer and a row of loudness, and nothing about the notes`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_500)
        val take = viewModel.takeState.value
        assertTrue(take.recording)
        assertEquals(3L, take.elapsedSeconds)
        assertEquals(14, take.levels.size)
        assertTrue("the fake violin is heard", take.levels.last() > 0f)
        assertNull(take.problem)
    }

    @Test
    fun `a stopped take belongs to the piece, tops the list highlighted, and the session screen stays shut`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(4_000)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(200)

        assertEquals(id, sessions.saved.single().pieceId)
        val take = viewModel.state.value.takes.single()
        assertTrue(take.isNew)
        assertFalse("nobody marked it: the score marks nothing", take.best)
        assertTrue("the player stays with the music", effects.isEmpty())

        advance(2_000)
        assertFalse("the highlight settles", viewModel.state.value.takes.single().isNew)
    }

    @Test
    fun `takes stand newest first with none marked until the player says so, and progress needs two of them`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        assertNull(viewModel.state.value.progress)
        repeat(2) {
            viewModel.onIntent(PieceIntent.RecordClicked)
            advance(3_000)
            viewModel.onIntent(PieceIntent.RecordClicked)
            advance(500)
        }
        val state = viewModel.state.value
        assertEquals(2, state.takes.size)
        assertEquals(0, state.takes.count { it.best })
        assertEquals(2, state.progress!!.scores.size)
    }

    @Test
    fun `the take marked as the best stands first, the mark moves, a second tap clears it`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        repeat(3) { recordTake(viewModel) }
        advance(2_000)
        val (newest, middle, oldest) = viewModel.state.value.takes.map { it.card.id }

        viewModel.onIntent(PieceIntent.BestToggled(oldest))
        runCurrent()
        assertEquals(listOf(oldest, newest, middle), viewModel.state.value.takes.map { it.card.id })
        assertEquals(listOf(true, false, false), viewModel.state.value.takes.map { it.best })

        viewModel.onIntent(PieceIntent.BestToggled(middle))
        runCurrent()
        assertEquals(listOf(middle, newest, oldest), viewModel.state.value.takes.map { it.card.id })
        assertEquals(middle, repertoire.piece(id)!!.bestTakeId)

        // a new take goes under the pinned one
        recordTake(viewModel)
        assertEquals(middle, viewModel.state.value.takes.first().card.id)
        assertTrue(viewModel.state.value.takes[1].isNew)

        viewModel.onIntent(PieceIntent.BestToggled(middle))
        runCurrent()
        assertNull(repertoire.piece(id)!!.bestTakeId)
        assertEquals(0, viewModel.state.value.takes.count { it.best })
    }

    @Test
    fun `a take without notes says so and a take under two seconds goes without a word`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        source = CountingSource(FakePitchSource(FakeScenario.SILENCE, timeSource = testTimeSource))
        val (silent, effects) = screen(id)
        silent.onIntent(PieceIntent.RecordClicked)
        advance(4_000)
        silent.onIntent(PieceIntent.RecordClicked)
        advance(300)
        assertEquals(listOf<PieceEffect>(PieceEffect.ShowNoNotesRecorded), effects)
        assertTrue(sessions.saved.isEmpty())

        source = null
        val (quick, quickEffects) = screen(id)
        quick.onIntent(PieceIntent.RecordClicked)
        advance(800)
        quick.onIntent(PieceIntent.RecordClicked)
        advance(300)
        assertTrue(quickEffects.isEmpty() && sessions.saved.isEmpty())
        assertFalse(quick.takeState.value.recording)
    }

    @Test
    fun `noise shows as a small problem line and does not stop the take`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        source = CountingSource(FakePitchSource(FakeScenario.NOISE, timeSource = testTimeSource))
        val (viewModel, _) = screen(id)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_000)
        assertEquals(TakeProblem.TOO_NOISY, viewModel.takeState.value.problem)
        assertTrue(viewModel.takeState.value.recording)
    }

    @Test
    fun `without the permission a tap asks for it, and recording starts only once it is there`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        source = CountingSource(FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource), requiresMicPermission = true)
        val (viewModel, effects) = screen(id)
        assertNull(viewModel.takeState.value.micPermission)

        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(500)
        assertEquals(listOf<PieceEffect>(PieceEffect.RequestMicPermission), effects)
        assertEquals(0, source!!.collections)

        viewModel.onIntent(PieceIntent.MicPermissionChanged(granted = false))
        advance(100)
        assertEquals(false, viewModel.takeState.value.micPermission)

        viewModel.onIntent(PieceIntent.MicPermissionChanged(granted = true))
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(2_500)
        assertTrue(viewModel.takeState.value.recording)
    }

    @Test
    fun `a take opens its session`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.TakeClicked(42))
        runCurrent()
        assertEquals(listOf<PieceEffect>(PieceEffect.OpenSession(42)), effects)
    }

    private fun TestScope.recordTake(viewModel: PieceViewModel) {
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_000)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(500)
    }

    private fun PieceViewModel.select(intent: SelectionIntent) = onIntent(PieceIntent.Select(intent))

    @Test
    fun `picked takes are deleted together, the piece stays, and a deleted best take leaves no mark`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        repeat(3) { recordTake(viewModel) }
        advance(2_000)
        val (newest, middle, oldest) = viewModel.state.value.takes.map { it.card.id }

        viewModel.onIntent(PieceIntent.BestToggled(oldest))
        runCurrent()
        viewModel.select(SelectionIntent.CardLongPressed(newest))
        viewModel.onIntent(PieceIntent.TakeClicked(oldest))
        runCurrent()
        assertEquals(setOf(newest, oldest), viewModel.state.value.selection.ids)
        assertTrue("a tap inside the mode opens nothing", effects.isEmpty())

        viewModel.select(SelectionIntent.DeleteClicked)
        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(middle), state.takes.map { it.card.id })
        assertFalse(state.takes.single().best)
        assertNull("progress needs two takes", state.progress)
        assertEquals(Selection(), state.selection)
        assertNotNull(state.header)
    }

    @Test
    fun `select all takes every take of this piece and no one else's`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val other = repertoire.add(PieceDraft(title = "Гавот"), nowEpochMs = 2)
        val (otherScreen, _) = screen(other)
        recordTake(otherScreen)
        val (viewModel, _) = screen(id)
        repeat(2) { recordTake(viewModel) }

        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.select(SelectionIntent.SelectAllClicked)
        runCurrent()

        assertEquals(2, viewModel.state.value.selection.count)
        assertTrue(viewModel.state.value.allSelected)
        assertEquals(viewModel.state.value.takes.map { it.card.id }.toSet(), viewModel.state.value.selection.ids)
    }

    @Test
    fun `picking and recording do not mix`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        recordTake(viewModel)
        val take = viewModel.state.value.takes.single().card.id

        // no way into the mode while a take runs
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(1_000)
        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.select(SelectionIntent.CardLongPressed(take))
        runCurrent()
        assertFalse(viewModel.state.value.selection.active)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_000)

        // and no take while picking
        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(1_000)
        assertTrue(viewModel.state.value.selection.active)
        assertFalse(viewModel.takeState.value.recording)
        assertEquals(0, source!!.active)
    }

    @Test
    fun `a video becomes a take of this piece - on top, highlighted, the session screen shut`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        backgroundScope.launch { viewModel.videoImport.collect {} }

        viewModel.onIntent(PieceIntent.VideoShootClicked)
        runCurrent()
        val launch = effects.single() as PieceEffect.LaunchVideoCamera
        viewModel.onIntent(PieceIntent.VideoShotFinished(saved = true))
        advance(1_000)
        assertTrue((viewModel.videoImport.value as VideoImport.Working).visible)

        advance(1_500)
        assertEquals(VideoImport.Idle, viewModel.videoImport.value)
        val take = viewModel.state.value.takes.single()
        assertTrue(take.isNew)
        assertEquals(1, effects.size)
        assertTrue(launch.filePath.endsWith(".mp4"))
    }

    @Test
    fun `backing out of the camera adds no take`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        viewModel.onIntent(PieceIntent.VideoShootClicked)
        viewModel.onIntent(PieceIntent.VideoShotFinished(saved = false))
        advance(5_000)
        assertTrue(viewModel.state.value.takes.isEmpty())
        assertEquals(0, videoAnalyzer.calls)
    }

    @Test
    fun `no video take while a take is recorded, while takes are picked, or while another video is on its way`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        recordTake(viewModel)

        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(1_000)
        viewModel.onIntent(PieceIntent.VideoShootClicked)
        viewModel.onIntent(PieceIntent.VideoPicked("content://video/1"))
        viewModel.onIntent(PieceIntent.RecordClicked)
        advance(3_000)

        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.onIntent(PieceIntent.VideoShootClicked)
        viewModel.select(SelectionIntent.Closed)
        runCurrent()
        assertTrue(effects.none { it is PieceEffect.LaunchVideoCamera })
        assertEquals(0, videoAnalyzer.calls)

        viewModel.onIntent(PieceIntent.VideoPicked("content://video/1"))
        viewModel.onIntent(PieceIntent.VideoShootClicked)
        runCurrent()
        assertTrue("one at a time", effects.none { it is PieceEffect.LaunchVideoCamera })
    }

    @Test
    fun `a video on its way to another piece is none of this screen's business`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val other = repertoire.add(PieceDraft(title = "Гавот"), nowEpochMs = 2)
        val (viewModel, _) = screen(id)
        val (otherScreen, _) = screen(other)
        backgroundScope.launch { viewModel.videoImport.collect {} }
        backgroundScope.launch { otherScreen.videoImport.collect {} }

        otherScreen.onIntent(PieceIntent.VideoPicked("content://video/1"))
        advance(1_000)
        assertTrue(otherScreen.videoImport.value is VideoImport.Working)
        assertEquals(VideoImport.Idle, viewModel.videoImport.value)
    }

    @Test
    fun `a shot that did not become a take goes to the system sheet under a second name`() = runTest {
        videoAnalyzer.outcome = com.example.violintuner.core.recording.FileAnalysisResult.NoNotes
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.VideoShootClicked)
        viewModel.onIntent(PieceIntent.VideoShotFinished(saved = true))
        advance(5_000)
        viewModel.onIntent(PieceIntent.VideoImportSendClicked)
        runCurrent()
        assertEquals(PieceEffect.ShareVideo("/cache/share/video.mp4"), effects.last())
        viewModel.onIntent(PieceIntent.VideoImportDismissed)
        assertEquals(1, videoFiles.discarded.size)
    }
}
