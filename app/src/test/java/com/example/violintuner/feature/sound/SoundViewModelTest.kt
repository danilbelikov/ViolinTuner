package com.example.violintuner.feature.sound

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.audio.playback.FakeSessionPlayer
import com.example.violintuner.core.audio.playback.FakeSessionWaveforms
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.EqBand
import com.example.violintuner.core.domain.sound.FakeSoundRepository
import com.example.violintuner.core.domain.sound.ReverbSpace
import com.example.violintuner.core.domain.sound.SoundBlock
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundParam
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.domain.sound.SoundRules
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundViewModelTest {
    private val config = SoundConfig()
    private val sound = FakeSoundRepository(config)
    private val sessions = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val player = FakeSessionPlayer()
    private val waveforms = FakeSessionWaveforms()
    private val hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
    private val warm = SoundPresets.settingsOf(BuiltInPreset.WARM, config)

    private object AudioFiles : SessionAudioFiles {
        override fun newFile(): File = error("not used")
        override fun existing(name: String): File? = File(name).takeIf { !name.startsWith("gone") }
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun recording(audio: String?, startedAt: Long = 1_000, pieceId: Long? = null): Long {
        val intonation = IntonationConfig()
        val samples = List(40) { SessionSample(69, 1.0) }
        val analysis = SessionAnalyzer.analyze(samples, intonation)
        return sessions.save(
            NewSession(
                startedAtEpochMs = startedAt, durationMs = 2_000, config = intonation, samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, intonation), audioPath = audio, pieceId = pieceId,
            ),
        )
    }

    private fun TestScope.screen(sessionId: Long?): Pair<SoundViewModel, MutableList<SoundEffect>> {
        val viewModel = SoundViewModel(
            SavedStateHandle(mapOf(SoundViewModel.ARG_SESSION_ID to (sessionId ?: SoundViewModel.EVERYONE))),
            sound, sessions, repertoire, AudioFiles, { player }, waveforms, config,
        )
        val effects = mutableListOf<SoundEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `a recording opens sounding like everyone, with its sound loaded and its waveform asked for`() = runTest {
        sound.setDefault(hall)
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val id = recording("take.m4a", pieceId = pieceId)
        val (viewModel, _) = screen(id)
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(SoundMode.RECORDING, state.mode)
        assertFalse(state.own)
        assertEquals(hall, state.settings)
        assertEquals(SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), state.caption)
        assertEquals("Менуэт", state.recording!!.pieceTitle)
        assertFalse(state.canReset)
        assertEquals(listOf(File("take.m4a")), player.loaded)
        assertEquals(hall, player.sounds.last())
        assertEquals(120, state.waveform!!.size)
    }

    @Test
    fun `the first touch gives the recording settings of its own - heard at once, stored a moment later`() = runTest {
        sound.setDefault(hall)
        val id = recording("take.m4a")
        val (viewModel, _) = screen(id)
        viewModel.onIntent(SoundIntent.ParamChanged(SoundParam.REVERB_MIX, 0.4))
        val state = viewModel.state.value
        assertTrue(state.own && state.savedHint && state.canReset && state.custom)
        assertEquals(SoundCaption.Custom, state.caption)
        assertEquals(0.4, player.sounds.last().reverb.mix, 0.0)
        assertTrue("not a row per frame of a dragged slider", sound.own.value.isEmpty())

        advanceTimeBy(500)
        assertEquals(0.4, sound.own.value.getValue(id).reverb.mix, 0.0)
        assertEquals("the default is nobody's to change from here", hall, sound.default.value)
        advanceTimeBy(3_000)
        assertFalse(viewModel.state.value.savedHint)
    }

    @Test
    fun `leaving the screen does not wait for the delay`() = runTest {
        val id = recording("take.m4a")
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(SoundIntent.PresetSelected(PresetRef.BuiltIn(BuiltInPreset.WARM)))
        viewModel.onIntent(SoundIntent.BackClicked)
        runCurrent()
        assertEquals(warm, sound.own.value.getValue(id))
        assertEquals(listOf<SoundEffect>(SoundEffect.Close), effects)
    }

    @Test
    fun `back to «как у всех» asks first, then forgets the own settings and sounds like the default again`() = runTest {
        sound.setDefault(hall)
        val id = recording("take.m4a")
        sound.setOwn(id, warm)
        val (viewModel, _) = screen(id)
        assertTrue(viewModel.state.value.own)

        viewModel.onIntent(SoundIntent.ModeSelected(own = false))
        assertEquals(SoundDialog.BackToEveryone, viewModel.state.value.dialog)
        viewModel.onIntent(SoundIntent.DialogDismissed)
        assertTrue("a dismissed question changes nothing", viewModel.state.value.own)

        viewModel.onIntent(SoundIntent.ResetClicked)
        viewModel.onIntent(SoundIntent.DialogConfirmed)
        runCurrent()
        assertFalse(viewModel.state.value.own)
        assertEquals(hall, viewModel.state.value.settings)
        assertEquals(hall, player.sounds.last())
        assertTrue(sound.own.value.isEmpty())
    }

    @Test
    fun `«Свои» chosen by hand keeps the same sound for this recording`() = runTest {
        sound.setDefault(hall)
        val id = recording("take.m4a")
        val (viewModel, _) = screen(id)
        viewModel.onIntent(SoundIntent.ModeSelected(own = true))
        advanceTimeBy(500)
        assertEquals(hall, sound.own.value.getValue(id))
        assertFalse("nothing was changed, nothing to boast of", viewModel.state.value.savedHint)
    }

    @Test
    fun `switches, the low cut, the space and the curve's points all reach the sound`() = runTest {
        val id = recording("take.m4a")
        val (viewModel, _) = screen(id)
        viewModel.onIntent(SoundIntent.BlockSwitched(SoundBlock.REVERB, on = true))
        viewModel.onIntent(SoundIntent.SpaceSelected(ReverbSpace.CATHEDRAL))
        viewModel.onIntent(SoundIntent.LowCutSwitched(on = true))
        viewModel.onIntent(SoundIntent.BandDragged(EqBand.AIR, hz = 10_000.0, gainDb = 99.0))
        val settings = viewModel.state.value.settings
        assertTrue(settings.reverb.enabled)
        assertEquals(4.0, settings.reverb.decaySec, 0.0)
        assertTrue(settings.eq.lowCut.enabled)
        assertTrue("dragging a point switches the equalizer on", settings.eq.enabled)
        assertEquals(12.0, settings.eq.air.gainDb, 0.0)
        assertEquals(EqBand.AIR, viewModel.state.value.band)
        assertEquals(settings, player.sounds.last())
    }

    @Test
    fun `plus, minus and a double tap move a number by its step and back to its default`() = runTest {
        val (viewModel, _) = screen(recording("take.m4a"))
        viewModel.onIntent(SoundIntent.ParamStepped(SoundParam.OUTPUT_GAIN, up = true))
        viewModel.onIntent(SoundIntent.ParamStepped(SoundParam.OUTPUT_GAIN, up = true))
        assertEquals(1.0, viewModel.state.value.settings.output.gainDb, 0.0)
        viewModel.onIntent(SoundIntent.ParamReset(SoundParam.OUTPUT_GAIN))
        assertEquals(0.0, viewModel.state.value.settings.output.gainDb, 0.0)
        // «Сколько» stands at «своё» once a detail was touched; a step then starts from the middle of its track
        viewModel.onIntent(SoundIntent.ParamChanged(SoundParam.COMP_RATIO, 9.0))
        assertNull(viewModel.state.value.settings.compressor.amount)
        viewModel.onIntent(SoundIntent.ParamStepped(SoundParam.COMP_AMOUNT, up = true))
        assertEquals(0.51, viewModel.state.value.settings.compressor.amount!!, 1e-9)
    }

    @Test
    fun `a preset is saved under a name, shows as chosen, and goes on a long press`() = runTest {
        val (viewModel, _) = screen(recording("take.m4a"))
        viewModel.onIntent(SoundIntent.SavePresetClicked)
        assertNull("a preset as it is needs no saving", viewModel.state.value.dialog)

        viewModel.onIntent(SoundIntent.PresetSelected(PresetRef.BuiltIn(BuiltInPreset.ROOM)))
        viewModel.onIntent(SoundIntent.ParamChanged(SoundParam.REVERB_MIX, 0.3))
        viewModel.onIntent(SoundIntent.SavePresetClicked)
        assertEquals(SoundDialog.SavePreset, viewModel.state.value.dialog)
        viewModel.onIntent(SoundIntent.PresetNameConfirmed("  Мой зал "))
        runCurrent()
        val chip = viewModel.state.value.chips.last()
        assertEquals("Мой зал", chip.userName)
        assertTrue(chip.selected)
        assertFalse(viewModel.state.value.custom)
        assertEquals(SoundCaption.User("Мой зал"), viewModel.state.value.caption)

        viewModel.onIntent(SoundIntent.PresetLongPressed(PresetRef.BuiltIn(BuiltInPreset.ROOM)))
        assertNull("built-in presets stay", viewModel.state.value.dialog)
        viewModel.onIntent(SoundIntent.PresetLongPressed(chip.ref))
        viewModel.onIntent(SoundIntent.DialogConfirmed)
        runCurrent()
        assertEquals(BuiltInPreset.entries.size, viewModel.state.value.chips.size)
        assertTrue("the sound stays, it is just nobody's preset now", viewModel.state.value.custom)
    }

    @Test
    fun `A B changes what is heard and nothing else - a held A lets go back to B`() = runTest {
        val (viewModel, _) = screen(recording("take.m4a"))
        viewModel.onIntent(SoundIntent.OriginalSelected(original = true, held = true))
        assertTrue(player.state.value.original)
        viewModel.onIntent(SoundIntent.OriginalSelected(original = false, held = true))
        assertFalse(player.state.value.original)

        viewModel.onIntent(SoundIntent.OriginalSelected(original = true))
        viewModel.onIntent(SoundIntent.OriginalSelected(original = false, held = true))
        assertTrue("a release that follows no hold leaves A alone", player.state.value.original)
        assertTrue(sound.own.value.isEmpty())
    }

    @Test
    fun `the screen of everyone sets the default, is heard on the newest recording with sound, and counts whom it touches`() = runTest {
        recording("old.m4a", startedAt = 1_000)
        val own = recording("own.m4a", startedAt = 2_000)
        recording(null, startedAt = 3_000)
        recording("gone.m4a", startedAt = 4_000)
        sound.setOwn(own, warm)

        val (viewModel, _) = screen(null)
        val state = viewModel.state.value
        assertEquals(SoundMode.EVERYONE, state.mode)
        assertEquals("own.m4a is the newest that can be played", listOf(File("own.m4a")), player.loaded)
        assertEquals(listOf(own, 1L), state.recordings.map { it.sessionId })
        assertEquals("old.m4a and gone.m4a follow the default; the silent one has no sound to process", 2, state.affected)

        viewModel.onIntent(SoundIntent.PresetSelected(PresetRef.BuiltIn(BuiltInPreset.CHAMBER_HALL)))
        advanceTimeBy(500)
        assertEquals(hall, sound.default.value)
        assertEquals("nobody's own settings are touched", mapOf(own to warm), sound.own.value)
        assertFalse(viewModel.state.value.savedHint)

        viewModel.onIntent(SoundIntent.ListenOnClicked)
        assertEquals(SoundDialog.PickRecording, viewModel.state.value.dialog)
        viewModel.onIntent(SoundIntent.RecordingPicked(1))
        runCurrent()
        assertEquals(File("old.m4a"), player.loaded.last())
        assertEquals(1L, viewModel.state.value.recording!!.sessionId)

        viewModel.onIntent(SoundIntent.ResetClicked)
        assertEquals(SoundDialog.ResetEveryone, viewModel.state.value.dialog)
        viewModel.onIntent(SoundIntent.DialogConfirmed)
        advanceTimeBy(500)
        assertTrue(SoundRules.isNeutral(sound.default.value))
    }

    @Test
    fun `with no recording to listen on the default is still set, silently`() = runTest {
        val (viewModel, _) = screen(null)
        assertNull(viewModel.state.value.player)
        assertNull(viewModel.state.value.recording)
        viewModel.onIntent(SoundIntent.PresetSelected(PresetRef.BuiltIn(BuiltInPreset.WARM)))
        viewModel.onIntent(SoundIntent.ScreenStopped)
        runCurrent()
        assertEquals(warm, sound.default.value)
    }

    @Test
    fun `a recording deleted from under the screen closes it, and sharing hands its id over`() = runTest {
        val id = recording("take.m4a")
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(SoundIntent.ShareClicked)
        runCurrent()
        assertEquals(listOf<SoundEffect>(SoundEffect.Share(id)), effects)
        sessions.delete(id)
        runCurrent()
        assertEquals(SoundEffect.Close, effects.last())
    }
}
