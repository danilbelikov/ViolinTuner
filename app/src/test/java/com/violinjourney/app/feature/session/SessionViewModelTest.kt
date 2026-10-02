package com.violinjourney.app.feature.session

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.audio.playback.SessionPlayer
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.playback.VideoPicture
import com.violinjourney.app.core.audio.playback.VideoState
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.backing.FakeBackingRepository
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.Finger
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.FakeSoundRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.feature.sound.SoundCaption
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private suspend fun saveSession(tolerance: Double = 8.0, audio: String? = null, pieceId: Long? = null, eventId: Long? = null): Long {
        val sessionConfig = config.copy(toleranceCents = tolerance)
        val samples = List(20) { SessionSample(78, -18.0) } + listOf(null) +
            List(40) { SessionSample(69, 1.0) } + listOf(null) + List(20) { SessionSample(73, -9.0) }
        val analysis = SessionAnalyzer.analyze(samples, sessionConfig)
        return repository.save(
            NewSession(
                startedAtEpochMs = 1_789_000_000_000, durationMs = samples.size * 50L, config = sessionConfig,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, sessionConfig), audioPath = audio,
                pieceId = pieceId, eventId = eventId,
            ),
        )
    }

    private class FakePlayer : SessionPlayer {
        override val state = MutableStateFlow(PlayerState())
        override val meters = MutableStateFlow<SoundMeters?>(null)
        var loaded: File? = null
        var released = 0
        val sounds = mutableListOf<SoundSettings>()

        /** False — a player still opening its file: it never says it is ready. */
        var readyOnLoad = true
        override fun setSound(settings: SoundSettings) {
            sounds += settings
            state.update { it.copy(processed = !SoundRules.isNeutral(settings)) }
        }

        override fun setOriginal(original: Boolean) = state.update { it.copy(original = original) }
        override fun load(file: File) {
            loaded = file
            if (readyOnLoad) state.value = PlayerState(ready = true, durationMs = 4_100)
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

    private class FakePicture(val file: File) : VideoPicture {
        override val state = MutableStateFlow(VideoState())
        val followed = mutableListOf<Pair<Long, Boolean>>()
        var released = 0
        var surfaces = 0
        override fun setSurface(next: android.view.Surface?) {
            surfaces++
        }

        override fun follow(positionMs: Long, playing: Boolean) {
            followed += positionMs to playing
        }

        override fun release() {
            released++
        }
    }

    private val pictures = mutableListOf<FakePicture>()
    private val player = FakePlayer()
    private val repertoire = FakeRepertoireRepository()
    private val sound = FakeSoundRepository()
    private val backings = FakeBackingRepository()
    private val waves = FakeSessionWaveforms()
    private val events = FakeEventRepository()
    private var audioFiles: SessionAudioFiles = FakeAudioFiles(present = setOf("take.m4a"))

    private fun TestScope.viewModel(id: Long): SessionViewModel {
        val viewModel = newViewModel(id)
        runCurrent()
        return viewModel
    }

    /** A model that has not read its recording yet: what it shows first can be watched from its first state. */
    private fun TestScope.newViewModel(id: Long): SessionViewModel = SessionViewModel(
        repository, config, audioFiles, { player }, repertoire, sound, SoundConfig(), { file -> FakePicture(file).also { pictures += it } },
        SavedStateHandle(mapOf(SessionViewModel.ARG_SESSION_ID to id)),
        compute = StandardTestDispatcher(testScheduler), backings = backings, backingPcm = null, waveforms = waves, events = events,
    )

    private fun SessionViewModel.loaded() = state.value as SessionState.Loaded

    @Test
    fun `a recording of an event is named by it, follows its renaming, and keeps the name it wore when the event goes`() = runTest {
        val event = SessionEvent(3, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null)
        events.recordEvents.value = mapOf(3L to event)
        val id = saveSession(eventId = 3)
        val viewModel = viewModel(id)
        assertEquals(event, viewModel.loaded().content.event)
        assertNull(viewModel.loaded().content.title)

        val renamed = event.copy(title = "Концерт в ДК")
        events.recordEvents.value = mapOf(3L to renamed)
        runCurrent()
        assertEquals("renamed, the event renames the head", renamed, viewModel.loaded().content.event)

        // deleted: the name it wore is written into the recording and the link goes, in one transaction (spec 3.35)
        repository.sessions.update { list -> list.map { if (it.id == id) it.copy(title = "Концерт в ДК · 24 октября", eventId = null) else it } }
        events.recordEvents.value = emptyMap()
        runCurrent()
        assertNull(viewModel.loaded().content.event)
        assertEquals("Концерт в ДК · 24 октября", viewModel.loaded().content.title)
    }

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
    fun `a take carries the title of its piece, and loses it with the piece`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт соль мажор"), nowEpochMs = 1)
        val take = saveSession(pieceId = pieceId)
        assertEquals("Менуэт соль мажор", viewModel(take).loaded().content.pieceTitle)
        assertNull(viewModel(saveSession()).loaded().content.pieceTitle)

        repertoire.delete(pieceId)
        assertNull(viewModel(take).loaded().content.pieceTitle)
    }

    @Test
    fun `the star marks a take as the best of its piece, says so, and a second tap clears the mark in silence`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val earlier = saveSession(pieceId = pieceId)
        val take = saveSession(pieceId = pieceId)
        repertoire.setBestTake(pieceId, earlier)
        val viewModel = viewModel(take)
        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        assertEquals(false, viewModel.loaded().content.best)

        viewModel.onIntent(SessionIntent.BestClicked)
        runCurrent()
        assertEquals(take, repertoire.piece(pieceId)!!.bestTakeId)
        assertEquals(true, viewModel.loaded().content.best)
        assertEquals(listOf<SessionEffect>(SessionEffect.ShowBestMarked(moved = true)), effects)

        viewModel.onIntent(SessionIntent.BestClicked)
        runCurrent()
        assertNull(repertoire.piece(pieceId)!!.bestTakeId)
        assertEquals(false, viewModel.loaded().content.best)
        assertEquals(1, effects.size)
    }

    @Test
    fun `a free recording has no star to tap`() = runTest {
        val viewModel = viewModel(saveSession())
        assertNull(viewModel.loaded().content.pieceId)
        viewModel.onIntent(SessionIntent.BestClicked)
        runCurrent()
        assertEquals(false, viewModel.loaded().content.best)
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
    fun `the position is a flow of its own and the state keeps it to the second`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        runCurrent()
        player.state.update { it.copy(positionMs = 2_345) }
        runCurrent()
        val states = mutableListOf<SessionState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        assertEquals(2_000, viewModel.loaded().player!!.positionMs)
        assertEquals(2_345, viewModel.position.value)

        // a chunk later, still in the same second: the cursor moves, the screen does not
        player.state.update { it.copy(positionMs = 2_700) }
        runCurrent()
        assertEquals(2_700, viewModel.position.value)
        assertEquals(1, states.size)

        player.state.update { it.copy(positionMs = 3_010) }
        runCurrent()
        assertEquals(3_000, viewModel.loaded().player!!.positionMs)
        assertEquals(2, states.size)
    }

    @Test
    fun `a session whose file is gone has no player either and reads as a recording without sound`() = runTest {
        audioFiles = FakeAudioFiles(present = emptySet())
        val loaded = viewModel(saveSession(audio = "take.m4a")).loaded()
        assertNull(loaded.player)
        assertNull(player.loaded)
        // a copy restored without «Звук записей» (spec 3.20): the quiet line of a recording without sound, not an empty place
        assertFalse(loaded.content.hasAudio)
        assertFalse(loaded.soundFailed)
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
        assertFalse(viewModel.loaded().soundFailed)
        player.state.value = PlayerState(failed = true)
        runCurrent()
        assertNull(viewModel.loaded().player)
        assertEquals(50, viewModel.loaded().content.scorePercent)
        // the file is there, it only does not play here: the screen says so where the player was (spec 3.17)
        assertTrue(viewModel.loaded().soundFailed)
        assertTrue(viewModel.loaded().content.hasAudio)
        // and there is nothing to play at the bottom: no panel, one column lying (spec 3.36.5)
        assertFalse("a sound that does not play has no player at the bottom", viewModel.loaded().playable)
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
    fun `a second press of delete while the first is at work deletes and closes once`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(SessionIntent.DeleteClicked)
        viewModel.onIntent(SessionIntent.DeleteConfirmed)
        viewModel.onIntent(SessionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(1, player.released)
        assertEquals(listOf<SessionEffect>(SessionEffect.Close), effects)
        assertTrue(repository.sessions.value.isEmpty())
    }

    @Test
    fun `a second tap on the star while the first mark is written does nothing`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val take = saveSession(pieceId = pieceId)
        val viewModel = viewModel(take)
        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(SessionIntent.BestClicked)
        viewModel.onIntent(SessionIntent.BestClicked)
        runCurrent()
        assertEquals(take, repertoire.piece(pieceId)!!.bestTakeId)
        assertEquals(true, viewModel.loaded().content.best)
        assertEquals(listOf<SessionEffect>(SessionEffect.ShowBestMarked(moved = false)), effects)
    }

    @Test
    fun `a rename and a star change the header without analysing the recording again`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val viewModel = viewModel(saveSession(audio = "take.m4a", pieceId = pieceId))
        val content = viewModel.loaded().content
        assertEquals(1, repository.detailsReads)

        viewModel.onIntent(SessionIntent.RenameConfirmed("Этюд"))
        runCurrent()
        viewModel.onIntent(SessionIntent.BestClicked)
        runCurrent()
        assertEquals("unpacked and analysed once, when the screen opened", 1, repository.detailsReads)
        assertEquals("Этюд", viewModel.loaded().content.title)
        assertTrue(viewModel.loaded().content.best)
        assertEquals("the analysis is the one already shown", content.segments, viewModel.loaded().content.segments)
    }

    @Test
    fun `a recording plays the way its settings make it sound, and follows them while it is open`() = runTest {
        val hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, SoundConfig())
        val warm = SoundPresets.settingsOf(BuiltInPreset.WARM, SoundConfig())
        sound.setDefault(hall)
        val id = saveSession(audio = "take.m4a")
        val viewModel = viewModel(id)
        assertEquals(listOf(hall), player.sounds)
        assertTrue(viewModel.loaded().player!!.processed)

        sound.setOwn(id, warm)
        runCurrent()
        assertEquals(warm, player.sounds.last())
        sound.clearOwn(id)
        runCurrent()
        assertEquals("back to what everyone has", hall, player.sounds.last())
    }

    @Test
    fun `with nothing set there is nothing to compare, and A B only changes what is heard`() = runTest {
        val id = saveSession(audio = "take.m4a")
        val viewModel = viewModel(id)
        assertFalse(viewModel.loaded().player!!.processed)

        sound.setOwn(id, SoundPresets.settingsOf(BuiltInPreset.ROOM, SoundConfig()))
        runCurrent()
        viewModel.onIntent(SessionIntent.OriginalSelected(original = true))
        runCurrent()
        assertTrue(viewModel.loaded().player!!.original)
        assertEquals("the settings are left alone", 1, sound.own.value.size)
        viewModel.onIntent(SessionIntent.OriginalSelected(original = false))
        runCurrent()
        assertFalse(viewModel.loaded().player!!.original)
    }

    @Test
    fun `the row under the player says whose sound it is and which, and leads to the sound screen`() = runTest {
        sound.setDefault(SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, SoundConfig()))
        val id = saveSession(audio = "take.m4a")
        val viewModel = viewModel(id)
        assertEquals(SoundRow(SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), own = false, processed = true), viewModel.loaded().sound)

        val own = SoundPresets.settingsOf(BuiltInPreset.WARM, SoundConfig())
        sound.setOwn(id, own.copy(output = own.output.copy(enabled = true, gainDb = 1.0)))
        runCurrent()
        assertEquals(SoundRow(SoundCaption.Custom, own = true, processed = true), viewModel.loaded().sound)

        sound.setOwn(id, SoundPresets.settingsOf(BuiltInPreset.OFF, SoundConfig()))
        runCurrent()
        assertEquals("settings that do nothing: no A and B, «выключен»", false, viewModel.loaded().sound?.processed)

        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(SessionIntent.SoundClicked)
        runCurrent()
        assertEquals(listOf<SessionEffect>(SessionEffect.OpenSound(id)), effects)
        assertNull("a silent session has neither player nor row", viewModel(saveSession()).loaded().sound)
    }

    // ---- the recording of R5 (spec 3.36.5)

    @Test
    fun `the waveform of the player is reckoned from the file of the sound and a recording without sound has none`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        assertEquals(List(SessionWaveforms.BARS) { 0.5f }, viewModel.loaded().waveform)
        assertEquals(1, waves.asked)

        assertNull(viewModel(saveSession()).loaded().waveform)
        assertEquals("nothing to reckon without a file", 1, waves.asked)
    }

    /** F#5 three times: −12 for a second, then −25 for a second, then −25 for a second and a half; A4 once, between them. */
    private suspend fun saveDrifting(): Long {
        val samples = List(20) { SessionSample(78, -12.0) } + listOf(null) + List(20) { SessionSample(69, 1.0) } + listOf(null) +
            List(20) { SessionSample(78, -25.0) } + listOf(null) + List(30) { SessionSample(78, -25.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return repository.save(NewSession(0, samples.size * 50L, config, samples, analysis.metrics!!, emptyList(), null))
    }

    @Test
    fun `a row of «Что уходит» opens the sheet where its note drifts the most`() = runTest {
        val viewModel = viewModel(saveDrifting())
        val content = viewModel.loaded().content
        assertEquals(listOf("F#5"), content.problemNotes.map { it.note.name })
        assertEquals(listOf(3, 1, 3, 3), content.segments.map { it.sameNoteCount })

        viewModel.onIntent(SessionIntent.ProblemNoteClicked(content.problemNotes.single().note))
        assertEquals("the largest mean, and of two equal ones the longer", 3, viewModel.loaded().selectedSegment)

        viewModel.onIntent(SessionIntent.NoteSheetDismissed)
        viewModel.onIntent(SessionIntent.ProblemNoteClicked(Note(80)))
        assertNull("a note that did not sound opens nothing", viewModel.loaded().selectedSegment)
    }

    @Test
    fun `«К произведению» leads to the piece of a take and a free recording or a take of a deleted piece has none`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val takeId = saveSession(pieceId = pieceId)
        val take = viewModel(takeId)
        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch { take.effects.collect { effects += it } }
        take.onIntent(SessionIntent.OpenPieceClicked)
        runCurrent()
        assertEquals(listOf<SessionEffect>(SessionEffect.OpenPiece(pieceId)), effects)

        val free = viewModel(saveSession())
        backgroundScope.launch { free.effects.collect { effects += it } }
        free.onIntent(SessionIntent.OpenPieceClicked)
        runCurrent()

        repertoire.delete(pieceId)
        val orphan = viewModel(takeId)
        assertNull(orphan.loaded().content.pieceId)
        backgroundScope.launch { orphan.effects.collect { effects += it } }
        orphan.onIntent(SessionIntent.OpenPieceClicked)
        runCurrent()
        assertEquals("only the take of a piece that is there", 1, effects.size)
    }

    @Test
    fun `a held A plays the original only while it is held and a tap on A keeps it`() = runTest {
        val id = saveSession(audio = "take.m4a")
        val viewModel = viewModel(id)
        sound.setOwn(id, SoundPresets.settingsOf(BuiltInPreset.ROOM, SoundConfig()))
        runCurrent()

        viewModel.onIntent(SessionIntent.OriginalSelected(original = true, held = true))
        runCurrent()
        assertTrue("held: A", viewModel.loaded().player!!.original)
        viewModel.onIntent(SessionIntent.OriginalSelected(original = false, held = true))
        runCurrent()
        assertFalse("let go: B again", viewModel.loaded().player!!.original)

        viewModel.onIntent(SessionIntent.OriginalSelected(original = true))
        viewModel.onIntent(SessionIntent.OriginalSelected(original = false, held = true))
        runCurrent()
        assertTrue("a tap's press ends too, and A stays", viewModel.loaded().player!!.original)
    }

    /**
     * The rows of the player stand from the first frame (spec 3.36.5): the first state the screen gets already knows the backing's row
     * and A/B — not a state a moment later, when the flow of the settings or the player speaks.
     */
    @Test
    fun `a take under a backing and settings that do something are known before the player is ready`() = runTest {
        sound.setDefault(SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, SoundConfig()))
        val id = saveSession(audio = "take.m4a")
        backings.saveTake(TakeBacking(id, backingId = 1, offsetMs = 0, recordedOffsetMs = 0, gainDb = -6f, playedMs = 0, output = BackingOutput.WIRED, deviceName = null))
        player.readyOnLoad = false
        val viewModel = newViewModel(id)
        val states = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect { states += it } }
        runCurrent()
        val first = states.filterIsInstance<SessionState.Loaded>().first()
        assertNull("the player is still opening its file", first.player)
        assertTrue("the backing's row and «только скрипка» from the first frame", first.content.underBacking)
        assertEquals("A/B from the first frame", true, first.sound?.processed)
        assertTrue("the panel stands from the first frame", first.playable)

        assertFalse(viewModel(saveSession(audio = "take.m4a")).loaded().content.underBacking)
    }

    @Test
    fun `a video take says it is one before its picture is looked into`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        assertTrue(viewModel(saveVideoTake()).loaded().content.hasVideo)
        assertFalse(viewModel(saveSession()).loaded().content.hasVideo)
    }

    @Test
    fun `score colors`() {
        assertEquals(Zone.IN_TUNE, SessionContentMapper.scoreZone(75, config))
        assertEquals(Zone.NEAR, SessionContentMapper.scoreZone(74, config))
        assertEquals(Zone.NEAR, SessionContentMapper.scoreZone(55, config))
        assertEquals(Zone.OFF, SessionContentMapper.scoreZone(54, config))
    }

    private suspend fun saveVideoTake(): Long {
        val id = saveSession(audio = "take.mp4")
        repository.sessions.update { list -> list.map { if (it.id == id) it.copy(videoPath = "take.mp4") else it } }
        return id
    }

    @Test
    fun `a recording that is sound only has no picture`() = runTest {
        val viewModel = viewModel(saveSession(audio = "take.m4a"))
        assertNull(viewModel.loaded().video)
        assertTrue(pictures.isEmpty())
    }

    @Test
    fun `the picture of a video take follows the sound`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        val viewModel = viewModel(saveVideoTake())
        val picture = pictures.single()
        assertEquals("take.mp4", picture.file.name)
        assertEquals(VideoUi(), viewModel.loaded().video)

        picture.state.value = VideoState(width = 1920, height = 1080, showing = true)
        viewModel.onIntent(SessionIntent.PlayPauseClicked)
        viewModel.onIntent(SessionIntent.SeekRequested(2_000))
        runCurrent()
        assertEquals(VideoUi(width = 1920, height = 1080, showing = true), viewModel.loaded().video)
        assertEquals(2_000L to true, picture.followed.last())

        viewModel.onIntent(SessionIntent.ScreenStopped)
        runCurrent()
        assertEquals(2_000L to false, picture.followed.last())
    }

    @Test
    fun `watch this place starts a second before the note and closes its sheet`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        val viewModel = viewModel(saveVideoTake())
        val second = viewModel.loaded().content.segments[1]
        viewModel.onIntent(SessionIntent.SegmentClicked(1))
        viewModel.onIntent(SessionIntent.PlaySegmentClicked(1))
        runCurrent()
        assertEquals(second.startMs - 1_000, player.state.value.positionMs)
        assertTrue(player.state.value.playing)
        assertNull(viewModel.loaded().selectedSegment)

        // the first note starts at zero: there is no second before it
        viewModel.onIntent(SessionIntent.PlaySegmentClicked(0))
        assertEquals(0L, player.state.value.positionMs)
    }

    @Test
    fun `a recording without sound has no place to play`() = runTest {
        val viewModel = viewModel(saveSession(audio = null))
        viewModel.onIntent(SessionIntent.SegmentClicked(1))
        viewModel.onIntent(SessionIntent.PlaySegmentClicked(1))
        assertEquals(1, viewModel.loaded().selectedSegment)
    }

    @Test
    fun `a video that is gone leaves the analysis, and nothing to fill the screen with`() = runTest {
        audioFiles = FakeAudioFiles(present = emptySet())
        val viewModel = viewModel(saveVideoTake())
        assertEquals(VideoUi(lost = true), viewModel.loaded().video)
        assertNull(viewModel.loaded().player)
        // its sound went with it — the same file; the row of the lost video says so, not the line of a silent recording
        assertFalse(viewModel.loaded().content.hasAudio)
        assertTrue(pictures.isEmpty())
        viewModel.onIntent(SessionIntent.FullscreenChanged(true))
        assertFalse(viewModel.loaded().fullscreen)
    }

    @Test
    fun `a picture this phone cannot decode leaves the sound where it was`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        val viewModel = viewModel(saveVideoTake())
        pictures.single().state.value = VideoState(failed = true)
        runCurrent()
        assertTrue(viewModel.loaded().video!!.undecodable)
        assertNotNull(viewModel.loaded().player)
    }

    /**
     * While the backing of a video take is made, the picture waits for the sound and has no «на весь экран» (spec 3.36.5, 5.25): the
     * full screen would come without a player and say nothing of why. Made — it opens.
     */
    @Test
    fun `the full screen does not open while the backing is made`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        player.readyOnLoad = false
        val viewModel = viewModel(saveVideoTake())
        player.state.value = PlayerState(preparingBacking = true)
        runCurrent()
        assertTrue(viewModel.loaded().preparingBacking)
        viewModel.onIntent(SessionIntent.FullscreenChanged(true))
        assertFalse(viewModel.loaded().fullscreen)

        player.state.value = PlayerState(ready = true, durationMs = 4_100, hasBacking = true)
        runCurrent()
        viewModel.onIntent(SessionIntent.FullscreenChanged(true))
        assertTrue(viewModel.loaded().fullscreen)
    }

    @Test
    fun `fullscreen is a state of the screen, and the picture goes before the file does`() = runTest {
        audioFiles = FakeAudioFiles(present = setOf("take.mp4"))
        val viewModel = viewModel(saveVideoTake())
        viewModel.onIntent(SessionIntent.FullscreenChanged(true))
        assertTrue(viewModel.loaded().fullscreen)
        viewModel.onIntent(SessionIntent.FullscreenChanged(false))
        assertFalse(viewModel.loaded().fullscreen)

        viewModel.onIntent(SessionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(1, pictures.single().released)
        assertEquals(1, player.released)
    }
}
