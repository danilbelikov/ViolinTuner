package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.audio.recording.SessionAudioFiles
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
import com.example.violintuner.core.recording.TakePipeline
import com.example.violintuner.core.settings.FakeSettingsRepository
import com.example.violintuner.core.settings.SettingsConfigSource
import com.example.violintuner.feature.repertoire.piece.PieceEffect
import com.example.violintuner.feature.repertoire.piece.PieceIntent
import com.example.violintuner.feature.repertoire.piece.PieceViewModel
import com.example.violintuner.feature.repertoire.piece.TakeProblem
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

    private fun TestScope.screen(pieceId: Long, saved: SavedStateHandle = SavedStateHandle(mapOf(PieceViewModel.ARG_PIECE_ID to pieceId))):
        Pair<PieceViewModel, MutableList<PieceEffect>> {
        val pitch = source ?: CountingSource(FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource)).also { source = it }
        val takes = TakePipeline(pitch, sessions, NoAudioFiles, practice, PracticeConfig(), clock, StandardTestDispatcher(testScheduler))
        val viewModel = PieceViewModel(
            saved, repertoire, files, RepertoireConfig(), clock, takes,
            SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), sessions, IntonationConfig(),
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
        assertTrue(take.isNew && take.best)
        assertTrue("the player stays with the music", effects.isEmpty())

        advance(2_000)
        assertFalse("the highlight settles", viewModel.state.value.takes.single().isNew)
    }

    @Test
    fun `takes stand newest first with the best one marked, and progress needs two of them`() = runTest {
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
        assertEquals(1, state.takes.count { it.best })
        assertEquals(2, state.progress!!.scores.size)
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
}
