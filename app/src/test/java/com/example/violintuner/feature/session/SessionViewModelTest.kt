package com.example.violintuner.feature.session

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.session.Finger
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import com.example.violintuner.feature.session.player.PlayerState
import com.example.violintuner.feature.session.player.SessionPlayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val repository = FakeSessionRepository()
    private val config = IntonationConfig()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** F#5 flat by 18, A4 in tune, C#5 flat by 9: the handoff example in miniature. */
    private suspend fun saveSession(tolerance: Double = 8.0, audio: String? = null): Long {
        val sessionConfig = config.copy(toleranceCents = tolerance)
        val samples = List(20) { SessionSample(78, -18.0) } + listOf(null) +
            List(40) { SessionSample(69, 1.0) } + listOf(null) + List(20) { SessionSample(73, -9.0) }
        val analysis = SessionAnalyzer.analyze(samples, sessionConfig)
        return repository.save(
            NewSession(
                startedAtEpochMs = 1_789_000_000_000, durationMs = samples.size * 50L, config = sessionConfig,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, sessionConfig), audioPath = audio,
            ),
        )
    }

    private class FakePlayer : SessionPlayer {
        override val state = MutableStateFlow(PlayerState())
        var loaded: File? = null
        var released = 0
        override fun load(file: File) {
            loaded = file
            state.value = PlayerState(ready = true, durationMs = 4_100)
        }

        override fun play() = state.update { it.copy(playing = true) }
        override fun pause() = state.update { it.copy(playing = false) }
        override fun seekTo(positionMs: Long) = state.update { it.copy(positionMs = positionMs) }
        override fun release() {
            released++
        }
    }

    private class FakeAudioFiles(private val present: Set<String>) : SessionAudioFiles {
        override fun newFile(): File = error("not used")
        override fun existing(name: String): File? = File(name).takeIf { name in present }
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private val player = FakePlayer()
    private var audioFiles: SessionAudioFiles = FakeAudioFiles(present = setOf("take.m4a"))

    private fun TestScope.viewModel(id: Long): SessionViewModel {
        val viewModel = SessionViewModel(
            repository, config, audioFiles, { player }, SavedStateHandle(mapOf(SessionViewModel.ARG_SESSION_ID to id)),
        )
        runCurrent()
        return viewModel
    }

    private fun SessionViewModel.loaded() = state.value as SessionState.Loaded

    @Test
    fun `loads the session into screen content`() = runTest {
        val content = viewModel(saveSession()).loaded().content
        assertNull(content.title)
        assertEquals(4_100, content.durationMs)
        assertEquals(50, content.scorePercent)
        assertEquals(50, content.nearPercent)
        assertEquals(listOf("F#5", "C#5", "A4"), content.rollNotes.map { it.name })
        assertEquals(listOf(Zone.NEAR, Zone.IN_TUNE, Zone.NEAR), content.segments.map { it.zone })
        assertEquals(20, content.segments.first().contour.size)
        assertEquals(-6.25, content.biasCents, 1e-9)
        assertEquals(Zone.IN_TUNE, content.biasZone) // noticeable, but inside the tolerance of 8
        assertEquals(listOf("F#5" to ViolinString.E5, "C#5" to ViolinString.A4), content.problemNotes.map { it.note.name to it.string })
        assertEquals(67 to Zone.NEAR, content.perString[ViolinString.A4]) // A4 in tune, C#5 on the same string is not
        assertEquals(0 to Zone.OFF, content.perString[ViolinString.E5])
        assertNull(content.perString[ViolinString.G3])
        assertEquals(Finger.FIRST, content.segments.first().position.finger)
        assertTrue(!content.hasAudio)
    }

    @Test
    fun `colors follow the tolerance the session was recorded with`() = runTest {
        val content = viewModel(saveSession(tolerance = 12.0)).loaded().content
        assertEquals(12.0, content.toleranceCents, 0.0)
        assertEquals(listOf(Zone.NEAR, Zone.IN_TUNE, Zone.IN_TUNE), content.segments.map { it.zone })
        assertEquals(listOf("F#5"), content.problemNotes.map { it.note.name })
    }

    @Test
    fun `small bias is no bias`() = runTest {
        val samples = List(40) { SessionSample(69, if (it % 2 == 0) 3.0 else -2.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        val id = repository.save(NewSession(0, 2_000, config, samples, analysis.metrics!!, emptyList(), null))
        assertNull(viewModel(id).loaded().content.biasZone)
    }

    @Test
    fun `a note that swings far around its target is not steady, even with a perfect mean`() = runTest {
        val samples = List(40) { SessionSample(69, if (it % 2 == 0) 30.0 else -30.0) } + listOf(null) +
            List(40) { SessionSample(71, if (it % 2 == 0) 6.0 else -6.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        val id = repository.save(NewSession(0, 4_000, config, samples, analysis.metrics!!, emptyList(), null))
        val segments = viewModel(id).loaded().content.segments
        assertEquals(listOf(Zone.IN_TUNE, Zone.IN_TUNE), segments.map { it.zone })
        assertEquals(listOf(false, true), segments.map { it.steady })
    }

    @Test
    fun `unknown session is not found`() = runTest {
        assertEquals(SessionState.NotFound, viewModel(99).state.value)
    }

    @Test
    fun `tapping a note opens its details, a bad index does not`() = runTest {
        val viewModel = viewModel(saveSession())
        viewModel.onIntent(SessionIntent.SegmentClicked(1))
        assertEquals(1, viewModel.loaded().selectedSegment)
        viewModel.onIntent(SessionIntent.NoteSheetDismissed)
        assertNull(viewModel.loaded().selectedSegment)
        viewModel.onIntent(SessionIntent.SegmentClicked(7))
        assertNull(viewModel.loaded().selectedSegment)
    }

    @Test
    fun `rename stores the trimmed name and a blank one restores the default`() = runTest {
        val viewModel = viewModel(saveSession())
        viewModel.onIntent(SessionIntent.RenameClicked)
        assertEquals(SessionDialog.RENAME, viewModel.loaded().dialog)
        viewModel.onIntent(SessionIntent.RenameConfirmed("  Гаммы D-dur "))
        runCurrent()
        assertEquals("Гаммы D-dur", viewModel.loaded().content.title)
        assertNull(viewModel.loaded().dialog)

        viewModel.onIntent(SessionIntent.RenameConfirmed("   "))
        runCurrent()
        assertNull(viewModel.loaded().content.title)
    }

    @Test
    fun `delete asks first, then removes the session and closes`() = runTest {
        val viewModel = viewModel(saveSession())
        viewModel.onIntent(SessionIntent.DeleteClicked)
        assertEquals(SessionDialog.DELETE, viewModel.loaded().dialog)
        viewModel.onIntent(SessionIntent.DialogDismissed)
        assertNull(viewModel.loaded().dialog)
        assertEquals(1, repository.sessions.value.size)

        viewModel.onIntent(SessionIntent.DeleteConfirmed)
        runCurrent()
        assertTrue(repository.sessions.value.isEmpty())
        assertEquals(SessionEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `back closes`() = runTest {
        val viewModel = viewModel(saveSession())
        viewModel.onIntent(SessionIntent.BackClicked)
        assertEquals(SessionEffect.Close, viewModel.effects.first())
    }

    // ---- player (spec 3.10, item 3)

    @Test
    fun `a session without sound has no player`() = runTest {
        val viewModel = viewModel(saveSession())
        assertNull(viewModel.loaded().player)
        assertNull(player.loaded)
    }

    @Test
    fun `a session whose file is gone has no player either`() = runTest {
        audioFiles = FakeAudioFiles(present = emptySet())
        assertNull(viewModel(saveSession(audio = "take.m4a")).loaded().player)
    }

    @Test
    fun `a session with sound loads its file and plays, pauses and seeks`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        assertEquals("take.m4a", player.loaded?.name)
        assertEquals(PlayerState(ready = true, durationMs = 4_100), viewModel.loaded().player)

        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        runCurrent()
        assertTrue(viewModel.loaded().player!!.playing)

        viewModel.onIntent(SessionIntent.SeekRequested(2_000))
        runCurrent()
        assertEquals(2_000, viewModel.loaded().player!!.positionMs)

        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        runCurrent()
        assertTrue(!viewModel.loaded().player!!.playing)
    }

    @Test
    fun `leaving the screen stops the sound`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        viewModel.onIntent(SessionIntent.ScreenStopped)
        runCurrent()
        assertTrue(!viewModel.loaded().player!!.playing)
    }

    @Test
    fun `a player that fails disappears, the screen stays`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        player.state.value = PlayerState(failed = true)
        runCurrent()
        assertNull(viewModel.loaded().player)
        assertEquals(50, viewModel.loaded().content.scorePercent)
    }

    @Test
    fun `renaming keeps the player and what it was doing`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        viewModel.onIntent(SessionIntent.RenameConfirmed("Этюд"))
        runCurrent()
        assertEquals("Этюд", viewModel.loaded().content.title)
        assertTrue(viewModel.loaded().player!!.playing)
        assertEquals(0, player.released)
    }

    @Test
    fun `deleting releases the player before the file goes`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        viewModel.onIntent(SessionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(1, player.released)
    }

    @Test
    fun `score colors`() {
        assertEquals(Zone.IN_TUNE, SessionContentMapper.scoreZone(75, config))
        assertEquals(Zone.NEAR, SessionContentMapper.scoreZone(74, config))
        assertEquals(Zone.NEAR, SessionContentMapper.scoreZone(55, config))
        assertEquals(Zone.OFF, SessionContentMapper.scoreZone(54, config))
    }
}
