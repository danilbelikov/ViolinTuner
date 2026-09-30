package com.violinjourney.app.feature.sound

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.playback.FakeSessionPlayer
import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.FakeSoundRepository
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    /** Sunday 27 September 2026, noon in Moscow: the day the cards of «Слушать на» are dated against. */
    private val moscow = TimeZone.of("Europe/Moscow")
    private val clock = FixedWallClock(LocalDateTime.parse("2026-09-27T12:00:00").toInstant(moscow), moscow)

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

    private val backings = com.violinjourney.app.core.domain.backing.FakeBackingRepository()
    private val backingPcm = object : com.violinjourney.app.core.audio.backing.BackingPcm {
        override fun cached(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): File? = null
        override fun prepare(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): File? = File("pcm-$sampleRate")
        override fun deleteOrphans(keptFiles: Set<String>) = Unit
    }

    private fun TestScope.screen(
        sessionId: Long?,
        sound: SoundRepository = this@SoundViewModelTest.sound,
        audioFiles: SessionAudioFiles = AudioFiles,
        backingConfig: BackingConfig = BackingConfig(),
        clock: WallClock = this@SoundViewModelTest.clock,
    ): Pair<SoundViewModel, MutableList<SoundEffect>> {
        val viewModel = SoundViewModel(
            SavedStateHandle(mapOf(SoundViewModel.ARG_SESSION_ID to (sessionId ?: SoundViewModel.EVERYONE))),
            sound, sessions, repertoire, audioFiles, { player }, waveforms, config, backings, backingPcm, backingConfig,
            clock = clock, io = StandardTestDispatcher(testScheduler),
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
        assertEquals(listOf(own, 1L), state.recordings.map { it.id })
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
    fun `the position is a flow of its own and the state keeps it to the second`() = runTest {
        val id = recording("take.m4a")
        val (viewModel, _) = screen(id)
        viewModel.onIntent(SoundIntent.PlayPauseClicked)
        runCurrent()
        player.state.update { it.copy(positionMs = 2_345) }
        runCurrent()
        val states = mutableListOf<SoundState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        assertEquals(2_000L, viewModel.state.value.player!!.positionMs)
        assertEquals(2_345L, viewModel.position.value)

        // a chunk later, in the same second: the waveform is redrawn, the screen is not recomposed
        player.state.update { it.copy(positionMs = 2_700) }
        runCurrent()
        assertEquals(2_700L, viewModel.position.value)
        assertEquals(1, states.size)
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

    @Test
    fun `sharing waits until what was just changed is stored`() = runTest {
        val id = recording("take.m4a")
        val stored = CompletableDeferred<Unit>()
        val slow = object : SoundRepository by sound {
            override suspend fun setOwn(sessionId: Long, settings: SoundSettings) {
                stored.await()
                sound.setOwn(sessionId, settings)
            }
        }
        val (viewModel, effects) = screen(id, sound = slow)
        viewModel.onIntent(SoundIntent.ParamChanged(SoundParam.REVERB_MIX, 0.4))
        viewModel.onIntent(SoundIntent.ShareClicked)
        runCurrent()
        assertTrue("the sheet reads the settings from the database: not before they are there", effects.isEmpty())
        stored.complete(Unit)
        runCurrent()
        assertEquals(listOf<SoundEffect>(SoundEffect.Share(id)), effects)
        assertEquals(0.4, sound.own.value.getValue(id).reverb.mix, 0.0)
    }

    @Test
    fun `one recording's screen does not look for the files of the others`() = runTest {
        recording("first.m4a")
        val id = recording("take.m4a", startedAt = 2_000)
        recording("third.m4a", startedAt = 3_000)
        val looked = mutableListOf<String>()
        val counting = object : SessionAudioFiles by AudioFiles {
            override fun existing(name: String): File? = AudioFiles.existing(name).also { looked += name }
        }
        val (viewModel, _) = screen(id, audioFiles = counting)
        repeat(2) {
            viewModel.onIntent(SoundIntent.ParamChanged(SoundParam.REVERB_MIX, 0.3 + it * 0.1))
            advanceTimeBy(500)
        }
        assertEquals(0.4, sound.own.value.getValue(id).reverb.mix, 0.0)
        assertEquals("only its own sound, once, to play it", listOf("take.m4a"), looked)
    }

    private suspend fun underBacking(sessionId: Long, output: com.violinjourney.app.core.domain.backing.BackingOutput = com.violinjourney.app.core.domain.backing.BackingOutput.BLUETOOTH) {
        val backingId = backings.add(backings.backing())
        backings.saveTake(com.violinjourney.app.core.domain.backing.TakeBacking(sessionId, backingId, 200, 200, -6f, 2_000, output, "Buds", latencyMs = 200))
    }

    @Test
    fun `while the backing's sound is made the screen says so, and the player comes when it is ready`() = runTest {
        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(preparingBacking = true)
        runCurrent()
        assertTrue(viewModel.state.value.preparingBacking)
        assertNull(viewModel.state.value.player)

        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(ready = true, durationMs = 1_000, hasBacking = true)
        runCurrent()
        assertFalse(viewModel.state.value.preparingBacking)
        assertTrue(viewModel.state.value.player!!.hasBacking)
    }

    @Test
    fun `a take under a backing plays with it and has its block, a take without one has neither`() = runTest {
        val plain = recording("plain.m4a")
        screen(plain)
        assertNull(player.backing)

        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        assertEquals(
            BackingBlockState(-6f, 200, 200, title = "Piano", durationMs = 220_000, recordedWith = RecordedWith.Wireless("Buds", latencyMs = 200)),
            viewModel.state.value.backing,
        )
        assertEquals(200, player.backing?.offsetMs)
        assertEquals(File("pcm-48000"), player.backing?.pcm?.invoke(48_000))
        assertTrue(player.state.value.hasBacking)
    }

    @Test
    fun `the shift steps by five, is heard at once and saved a moment later`() = runTest {
        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        viewModel.onIntent(SoundIntent.BackingOffsetStepped(up = true))
        viewModel.onIntent(SoundIntent.BackingOffsetStepped(up = true))
        assertEquals(210, viewModel.state.value.backing?.offsetMs)
        assertEquals(210 to -6f, player.mixes.last())
        assertEquals(200, backings.takeBackings.value.single().offsetMs)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(210, backings.takeBackings.value.single().offsetMs)

        viewModel.onIntent(SoundIntent.BackingOffsetRecorded)
        assertEquals(200, viewModel.state.value.backing?.offsetMs)
        viewModel.onIntent(SoundIntent.BackingGainChanged(1f))
        assertEquals(6f, viewModel.state.value.backing?.gainDb)
    }

    @Test
    fun `the backing sliders stand where the injected config sets the values`() = runTest {
        val under = recording("under.m4a")
        val backingId = backings.add(backings.backing())
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(under, backingId, 0, 0, -6f, 2_000, com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, "Jack"),
        )
        // ranges other than the defaults: a slider read by another config would put the value elsewhere
        val narrow = BackingConfig(minOffsetMs = -500, maxOffsetMs = 500, minGainDb = -12f, maxGainDb = 0f)
        val (viewModel, _) = screen(under, backingConfig = narrow)

        viewModel.onIntent(SoundIntent.BackingOffsetChanged(0.75f))
        viewModel.onIntent(SoundIntent.BackingGainChanged(0.25f))
        assertEquals(250, viewModel.state.value.backing?.offsetMs)
        assertEquals(-9f, viewModel.state.value.backing?.gainDb)
        // the screen draws the thumbs by the view model's config: where they were let go, over the values they set
        assertEquals(narrow, viewModel.backingConfig)
        assertEquals(0.75f, BackingSliders.offsetFraction(250, viewModel.backingConfig))
        assertEquals(0.25f, BackingSliders.gainFraction(-9f, viewModel.backingConfig))
    }

    @Test
    fun `a dragged shift lands on a five millisecond step, and «как записано» gives back the exact one`() = runTest {
        val under = recording("under.m4a")
        val backingId = backings.add(backings.backing())
        backings.saveTake(com.violinjourney.app.core.domain.backing.TakeBacking(under, backingId, 203, 203, -6f, 2_000, com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, "Jack"))
        val (viewModel, _) = screen(under)
        // 0.5343 of −2000…+2000 is +137.2 ms
        viewModel.onIntent(SoundIntent.BackingOffsetChanged(0.5343f))
        assertEquals(135, viewModel.state.value.backing?.offsetMs)
        assertEquals(135 to -6f, player.mixes.last())
        viewModel.onIntent(SoundIntent.BackingOffsetChanged(0.5357f))
        assertEquals(145, viewModel.state.value.backing?.offsetMs)

        viewModel.onIntent(SoundIntent.BackingOffsetRecorded)
        assertEquals(203, viewModel.state.value.backing?.offsetMs)
    }

    @Test
    fun `a take whose backing could not be prepared has a word instead of the sliders`() = runTest {
        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(ready = true, durationMs = 1_000, hasBacking = false)
        runCurrent()
        assertTrue(viewModel.state.value.backingUnavailable)

        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(ready = true, durationMs = 1_000, hasBacking = true)
        runCurrent()
        assertFalse(viewModel.state.value.backingUnavailable)

        // not while it is still being made: that is «Готовим минусовку…»
        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(preparingBacking = true)
        runCurrent()
        assertFalse(viewModel.state.value.backingUnavailable)

        val plain = recording("plain.m4a")
        val (other, _) = screen(plain)
        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(ready = true, durationMs = 1_000)
        runCurrent()
        assertFalse(other.state.value.backingUnavailable)
    }

    private fun at(time: String): Long = LocalDateTime.parse(time).toInstant(moscow).toEpochMilliseconds()

    @Test
    fun `when the screen opens every card is closed, and opening one closes none of the others`() = runTest {
        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        assertEquals(emptySet<SoundCard>(), viewModel.state.value.expanded)

        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.EQ))
        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.REVERB))
        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.BACKING))
        assertEquals(setOf(SoundCard.EQ, SoundCard.REVERB, SoundCard.BACKING), viewModel.state.value.expanded)

        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.EQ))
        assertEquals("the others stay open", setOf(SoundCard.REVERB, SoundCard.BACKING), viewModel.state.value.expanded)
        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.BACKING))
        assertEquals("«Минусовка» folds as the others do", setOf(SoundCard.REVERB), viewModel.state.value.expanded)
        assertTrue("opening and closing is no change of the sound", sound.own.value.isEmpty())
    }

    @Test
    fun `«Минусовка» that could not be prepared does not open, and a take without a backing has none to open`() = runTest {
        val under = recording("under.m4a")
        underBacking(under)
        val (viewModel, _) = screen(under)
        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(ready = true, durationMs = 1_000, hasBacking = false)
        runCurrent()
        viewModel.onIntent(SoundIntent.CardToggled(SoundCard.BACKING))
        assertEquals(emptySet<SoundCard>(), viewModel.state.value.expanded)

        val (plain, _) = screen(recording("plain.m4a"))
        plain.onIntent(SoundIntent.CardToggled(SoundCard.BACKING))
        assertEquals(emptySet<SoundCard>(), plain.state.value.expanded)
    }

    @Test
    fun `the card of the backing names its file and its length, and says what the take was recorded in`() = runTest {
        val under = recording("under.m4a")
        val backingId = backings.add(backings.backing(title = "фортепиано"))
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(
                under, backingId, 200, 200, -6f, 2_000, com.violinjourney.app.core.domain.backing.BackingOutput.BLUETOOTH, "Pixel Buds", latencyMs = 200,
            ),
        )
        val (viewModel, _) = screen(under)
        val block = viewModel.state.value.backing!!
        assertEquals("фортепиано", block.title)
        assertEquals(220_000L, block.durationMs)
        assertEquals(RecordedWith.Wireless("Pixel Buds", latencyMs = 200), block.recordedWith)
    }

    @Test
    fun `the line of the headphones is what the take keeps - wired, wireless with nothing added, and nothing for a nameless one`() = runTest {
        suspend fun recordedWith(output: com.violinjourney.app.core.domain.backing.BackingOutput, name: String?, latencyMs: Int): RecordedWith? {
            val take = recording("take-${output.name}-$name.m4a")
            val backingId = backings.add(backings.backing())
            backings.saveTake(com.violinjourney.app.core.domain.backing.TakeBacking(take, backingId, 0, 0, -6f, 2_000, output, name, latencyMs))
            return screen(take).first.state.value.backing!!.recordedWith
        }
        assertEquals(RecordedWith.Wired, recordedWith(com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, "Jack", 0))
        assertEquals(RecordedWith.Wired, recordedWith(com.violinjourney.app.core.domain.backing.BackingOutput.USB, null, 0))
        assertEquals(RecordedWith.Wireless("Buds", 0), recordedWith(com.violinjourney.app.core.domain.backing.BackingOutput.BLUETOOTH, "Buds", 0))
        assertNull(recordedWith(com.violinjourney.app.core.domain.backing.BackingOutput.BLUETOOTH, null, 200))
    }

    @Test
    fun `«Слушать на» is the cards of the recordings that play - newest first, dated, with «лучший» and the sign of the backing`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val best = recording("best.m4a", startedAt = at("2026-09-26T18:42:00"), pieceId = pieceId)
        repertoire.setBestTake(pieceId, best)
        val free = recording("free.m4a", startedAt = at("2026-09-27T09:15:00"))
        val under = recording("under.m4a", startedAt = at("2026-09-25T20:00:00"), pieceId = pieceId)
        underBacking(under)
        recording(null, startedAt = at("2026-09-27T10:00:00"))
        recording("gone.m4a", startedAt = at("2026-09-27T11:00:00"))

        val (viewModel, _) = screen(null)
        val state = viewModel.state.value
        assertEquals("only those that play, newest first", listOf(free, best, under), state.recordings.map { it.id })
        assertEquals(LocalDate(2026, 9, 27), state.today)
        val (first, second, third) = state.recordings
        assertEquals(LocalDate(2026, 9, 27), first.date)
        assertFalse(first.take || first.best || first.underBacking)
        assertEquals(LocalDate(2026, 9, 26), second.date)
        assertEquals("Менуэт", second.pieceTitle)
        assertTrue(second.take && second.best && second.hasAudio)
        assertTrue(third.underBacking && !third.best)
        assertEquals("heard on the newest", free, state.recording!!.sessionId)
    }

    /**
     * The days of «Слушать на» are those of the zone of the screen's clock (D14), as the days of «Записи» are: half past midnight in
     * Vladivostok is still the day before in Moscow, where the tests run, and in UTC — the card stands under the day of the clock,
     * and that day is «сегодня».
     */
    @Test
    fun `«Слушать на» dates its cards and today in the zone of its clock - half past midnight there`() = runTest {
        val vladivostok = TimeZone.of("Asia/Vladivostok")
        val night = LocalDateTime.parse("2026-09-27T00:30:00").toInstant(vladivostok).toEpochMilliseconds()
        val id = recording("night.m4a", startedAt = night)
        val clock = FixedWallClock(LocalDateTime.parse("2026-09-27T00:45:00").toInstant(vladivostok), vladivostok)

        val state = screen(null, clock = clock).first.state.value
        assertEquals(LocalDate(2026, 9, 27), state.today)
        assertEquals(listOf(id), state.recordings.map { it.id })
        assertEquals(LocalDate(2026, 9, 27), state.recordings.single().date)
    }

    @Test
    fun `the panel of the player stands in the first state of the screen, and goes when the file cannot be played`() = runTest {
        val id = recording("take.m4a")
        val viewModel = SoundViewModel(
            SavedStateHandle(mapOf(SoundViewModel.ARG_SESSION_ID to id)),
            sound, sessions, repertoire, AudioFiles, { player }, waveforms, config, backings, backingPcm, BackingConfig(),
            clock = clock, io = StandardTestDispatcher(testScheduler),
        )
        val states = mutableListOf<SoundState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect { states += it } }
        runCurrent()
        val shown = states.first { !it.loading }
        assertTrue("known before the player is ready: nothing under it jumps when it comes", shown.listening)

        player.state.value = com.violinjourney.app.core.audio.playback.PlayerState(failed = true)
        runCurrent()
        assertFalse(viewModel.state.value.listening)
        assertNull(viewModel.state.value.player)
    }

    @Test
    fun `the screen of everyone with nothing to listen on has no panel`() = runTest {
        recording(null)
        val (viewModel, _) = screen(null)
        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.listening)
        assertTrue(viewModel.state.value.recordings.isEmpty())
    }
}
