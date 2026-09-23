package com.example.violintuner.feature.sound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.fx.SoundMeters
import com.example.violintuner.core.audio.playback.SessionPlayer
import com.example.violintuner.core.audio.playback.SessionPlayerFactory
import com.example.violintuner.core.audio.playback.PlayerBacking
import com.example.violintuner.core.audio.backing.BackingPcm
import com.example.violintuner.core.domain.backing.Backing
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.BackingOffset
import com.example.violintuner.core.domain.backing.BackingRepository
import com.example.violintuner.core.domain.backing.NoBackings
import com.example.violintuner.core.domain.backing.TakeBacking
import kotlin.math.roundToInt
import com.example.violintuner.core.audio.playback.SessionWaveforms
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.core.domain.sound.EqBand
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundParam
import com.example.violintuner.core.domain.sound.SoundParams
import com.example.violintuner.core.domain.sound.SoundRepository
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings
import com.example.violintuner.core.domain.sound.UserPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The «Звук» screen of one recording, or — without a session id — of the default for all of them
 * (spec 3.17). The settings on the screen are a draft kept here: every touch goes to the player
 * at once, so that it is heard, and to the database a moment later, so that a dragged slider
 * does not write a row a frame. There is no «Сохранить».
 */
@HiltViewModel
class SoundViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sound: SoundRepository,
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val audioFiles: SessionAudioFiles,
    private val playerFactory: SessionPlayerFactory,
    private val waveforms: SessionWaveforms,
    private val config: SoundConfig,
    private val backings: BackingRepository = NoBackings,
    private val backingPcm: BackingPcm? = null,
    private val backingConfig: BackingConfig = BackingConfig(),
) : ViewModel() {

    private val sessionId: Long? = savedState.get<Long>(ARG_SESSION_ID)?.takeIf { it != EVERYONE }
    private val mode = if (sessionId == null) SoundMode.EVERYONE else SoundMode.RECORDING

    private val mutableState = MutableStateFlow(
        SoundState(
            loading = true, mode = mode, recording = null, own = false, settings = SoundRules.off(config),
            caption = SoundCaption.Custom, chips = emptyList(), custom = false, canReset = false, savedHint = false,
            expanded = null, band = EqBand.PRESENCE, details = false, player = null, waveform = null,
            recordings = emptyList(), affected = 0, dialog = null,
        ),
    )
    val state: StateFlow<SoundState> = mutableState.asStateFlow()

    /** Apart from [state]: it changes thirty times a second while the sound plays. */
    private val mutableMeters = MutableStateFlow<SoundMeters?>(null)
    val meters: StateFlow<SoundMeters?> = mutableMeters.asStateFlow()

    private val effectChannel = Channel<SoundEffect>(Channel.BUFFERED)
    val effects: Flow<SoundEffect> = effectChannel.receiveAsFlow()

    private var userPresets: List<UserPreset> = emptyList()
    private var player: SessionPlayer? = null
    private var playingId: Long? = null
    private var persistJob: Job? = null
    private var hintJob: Job? = null
    private var waveformJob: Job? = null
    private var unsaved = false
    private var heldOriginal = false

    // The backing of this take (spec 3.32), read once: the block is a draft like the settings, saved a moment after a touch.
    private var take: TakeBacking? = null
    private var backing: Backing? = null
    private var backingJob: Job? = null
    private var backingUnsaved = false

    init {
        viewModelScope.launch {
            val own = sessionId?.let { sound.own.first()[it] }
            val settings = own ?: sound.default.first()
            userPresets = sound.presets.first()
            loadBacking()
            show(settings, own = own != null, loading = false)
            launch { sound.presets.collect { userPresets = it; show(state.value.settings) } }
            launch { follow() }
        }
    }

    fun onIntent(intent: SoundIntent) {
        when (intent) {
            SoundIntent.BackClicked -> {
                flush()
                effectChannel.trySend(SoundEffect.Close)
            }
            SoundIntent.ScreenStopped -> {
                player?.pause()
                flush()
            }
            SoundIntent.PlayPauseClicked -> player?.let { if (it.state.value.playing) it.pause() else it.play() }
            is SoundIntent.SeekRequested -> player?.seekTo(intent.positionMs)
            is SoundIntent.OriginalSelected -> selectOriginal(intent)
            is SoundIntent.ModeSelected -> selectMode(intent.own)
            SoundIntent.ResetClicked -> if (state.value.canReset) {
                mutableState.update { it.copy(dialog = if (mode == SoundMode.RECORDING) SoundDialog.BackToEveryone else SoundDialog.ResetEveryone) }
            }
            is SoundIntent.PresetSelected -> SoundReducer.settingsOf(intent.ref, userPresets, config)?.let { preset -> edit { preset } }
            SoundIntent.SavePresetClicked -> if (state.value.custom) mutableState.update { it.copy(dialog = SoundDialog.SavePreset) }
            is SoundIntent.PresetNameConfirmed -> {
                val settings = state.value.settings
                mutableState.update { it.copy(dialog = null) }
                viewModelScope.launch { sound.savePreset(intent.name, settings) }
            }
            is SoundIntent.PresetLongPressed -> (intent.ref as? PresetRef.User)?.let { ref ->
                userPresets.firstOrNull { it.id == ref.id }?.let { preset ->
                    mutableState.update { it.copy(dialog = SoundDialog.DeletePreset(preset.id, preset.name)) }
                }
            }
            SoundIntent.DialogConfirmed -> confirm()
            SoundIntent.DialogDismissed -> mutableState.update { it.copy(dialog = null) }
            is SoundIntent.BlockSwitched -> edit { SoundReducer.withBlock(it, intent.block, intent.on) }
            is SoundIntent.BlockHeaderClicked -> mutableState.update { it.copy(expanded = intent.block.takeIf { block -> block != it.expanded }) }
            is SoundIntent.BandSelected -> mutableState.update { it.copy(band = intent.band) }
            is SoundIntent.LowCutSwitched -> edit { it.copy(eq = it.eq.copy(lowCut = it.eq.lowCut.copy(enabled = intent.on))) }
            is SoundIntent.SpaceSelected -> edit { it.copy(reverb = SoundRules.withSpace(it.reverb, intent.space, config)) }
            SoundIntent.DetailsClicked -> mutableState.update { it.copy(details = !it.details) }
            is SoundIntent.ParamChanged -> edit { SoundParams.set(intent.param, SoundParams.snapped(intent.param, intent.value), it, config) }
            is SoundIntent.ParamStepped -> edit { settings ->
                val range = SoundParams.range(intent.param, settings, config)
                val from = SoundParams.get(intent.param, settings) ?: range.default
                SoundParams.set(intent.param, SoundParams.stepped(intent.param, from, intent.up, range), settings, config)
            }
            is SoundIntent.ParamReset -> edit { SoundParams.set(intent.param, SoundParams.range(intent.param, it, config).default, it, config) }
            is SoundIntent.BandDragged -> {
                mutableState.update { it.copy(band = intent.band) }
                edit {
                    SoundReducer.dragged(it, intent.band, SoundParams.snapped(SoundParam.BODY_HZ, intent.hz), SoundParams.snapped(SoundParam.BODY_GAIN, intent.gainDb), config)
                }
            }
            SoundIntent.ListenOnClicked -> if (state.value.recordings.size > 1) mutableState.update { it.copy(dialog = SoundDialog.PickRecording) }
            is SoundIntent.RecordingPicked -> {
                mutableState.update { it.copy(dialog = null) }
                viewModelScope.launch { listenOn(sessions.sessions.first().firstOrNull { it.id == intent.sessionId }) }
            }
            SoundIntent.ShareClicked -> sessionId?.let {
                player?.pause()
                flush()
                effectChannel.trySend(SoundEffect.Share(it))
            }
            is SoundIntent.BackingHeardSelected -> player?.setBackingHeard(intent.heard)
            is SoundIntent.BackingGainChanged -> editBacking { it.copy(gainDb = backingConfig.minGainDb + intent.fraction * (backingConfig.maxGainDb - backingConfig.minGainDb)) }
            is SoundIntent.BackingGainStepped -> editBacking { it.copy(gainDb = it.gainDb + if (intent.up) backingConfig.gainStepDb else -backingConfig.gainStepDb) }
            SoundIntent.BackingGainReset -> editBacking { it.copy(gainDb = backingConfig.defaultGainDb) }
            is SoundIntent.BackingOffsetChanged -> editBacking {
                it.copy(offsetMs = (backingConfig.minOffsetMs + intent.fraction * (backingConfig.maxOffsetMs - backingConfig.minOffsetMs)).roundToInt())
            }
            is SoundIntent.BackingOffsetStepped -> editBacking { it.copy(offsetMs = it.offsetMs + if (intent.up) backingConfig.offsetStepMs else -backingConfig.offsetStepMs) }
            SoundIntent.BackingOffsetRecorded -> editBacking { it.copy(offsetMs = it.recordedOffsetMs) }
        }
    }

    /** The take's backing; nothing for a take without one, or for the screen of everyone. */
    private suspend fun loadBacking() {
        val id = sessionId ?: return
        val found = backings.takeBackings.first().firstOrNull { it.sessionId == id } ?: return
        take = found
        backing = backings.backing(found.backingId)
        mutableState.update { it.copy(backing = BackingBlockState(found.gainDb, found.offsetMs, found.recordedOffsetMs)) }
    }

    private fun editBacking(change: (BackingBlockState) -> BackingBlockState) {
        val before = state.value.backing ?: return
        val changed = change(before)
        val clean = changed.copy(gainDb = BackingOffset.snapGain(changed.gainDb, backingConfig), offsetMs = BackingOffset.clamp(changed.offsetMs, backingConfig))
        if (clean.gainDb == before.gainDb && clean.offsetMs == before.offsetMs) return
        mutableState.update { it.copy(backing = clean) }
        player?.setBackingMix(clean.offsetMs, clean.gainDb)
        backingUnsaved = true
        backingJob?.cancel()
        backingJob = viewModelScope.launch {
            delay(PERSIST_AFTER_MS)
            persistBacking()
        }
    }

    private suspend fun persistBacking() = withContext(NonCancellable) {
        if (!backingUnsaved) return@withContext
        backingUnsaved = false
        val id = sessionId ?: return@withContext
        val block = state.value.backing ?: return@withContext
        backings.setTakeMix(id, block.offsetMs, block.gainDb)
    }

    /** The recordings: whose screen this is, what the default can be listened on, how many it touches. */
    private suspend fun follow() {
        kotlinx.coroutines.flow.combine(sessions.sessions, sound.own, repertoire.pieces) { all, own, pieces -> Triple(all, own.keys, pieces.associate { it.id to it.title }) }
            .collect { (all, own, titles) ->
                fun nameOf(session: SessionSummary) = RecordingName(session.id, session.title, session.pieceId?.let(titles::get), session.startedAtEpochMs, hasVideo = session.videoPath != null)
                val playable = SoundReducer.withSound(all).filter { it.audioPath?.let(audioFiles::existing) != null }
                val mine = all.firstOrNull { it.id == sessionId }
                if (mode == SoundMode.RECORDING && mine == null) {
                    effectChannel.trySend(SoundEffect.Close) // deleted from under the screen
                    return@collect
                }
                mutableState.update {
                    it.copy(
                        recordings = if (mode == SoundMode.EVERYONE) playable.map(::nameOf) else emptyList(),
                        affected = SoundReducer.affected(all, own),
                        recording = (mine ?: playable.firstOrNull { session -> session.id == playingId } ?: playable.firstOrNull())?.let(::nameOf),
                    )
                }
                if (playingId == null) listenOn(mine ?: playable.firstOrNull())
            }
    }

    private fun listenOn(session: SessionSummary?) {
        val file = session?.audioPath?.let(audioFiles::existing) ?: return
        if (session.id == playingId) return
        playingId = session.id
        val current = player ?: playerFactory.create(viewModelScope).also { created ->
            player = created
            created.setSound(state.value.settings)
            viewModelScope.launch { created.state.collect { playerState -> mutableState.update { it.copy(player = playerState.takeIf { p -> p.ready && !p.failed }) } } }
            viewModelScope.launch { created.meters.collect { mutableMeters.value = it } }
        }
        val under = take?.takeIf { session.id == sessionId }
        val file0 = backing
        val pcm = backingPcm
        if (under != null && file0 != null && pcm != null) {
            val block = state.value.backing
            current.loadWithBacking(file, PlayerBacking(pcm = { rate -> pcm.prepare(file0, rate) }, offsetMs = block?.offsetMs ?: under.offsetMs, gainDb = block?.gainDb ?: under.gainDb))
        } else {
            current.load(file)
        }
        mutableState.update { it.copy(waveform = null, recording = if (mode == SoundMode.EVERYONE) it.recordings.firstOrNull { r -> r.sessionId == session.id } ?: it.recording else it.recording) }
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            val waveform = waveforms.of(file)
            mutableState.update { it.copy(waveform = waveform?.toList()) }
        }
    }

    /** Every change of the sound goes through here. The first one gives a recording settings of its own — without asking. */
    private fun edit(change: (SoundSettings) -> SoundSettings) {
        val before = state.value
        if (before.loading) return
        val settings = SoundRules.clean(change(before.settings), config)
        if (settings == before.settings) return
        val becomesOwn = mode == SoundMode.RECORDING && !before.own
        show(settings, own = before.own || becomesOwn)
        player?.setSound(settings)
        if (becomesOwn) {
            mutableState.update { it.copy(savedHint = true) }
            hintJob?.cancel()
            hintJob = viewModelScope.launch {
                delay(SAVED_HINT_MS)
                mutableState.update { it.copy(savedHint = false) }
            }
        }
        persistSoon()
    }

    private fun selectMode(own: Boolean) {
        val current = state.value
        if (mode != SoundMode.RECORDING || current.loading || own == current.own) return
        if (own) {
            // Same sound, but from now on this recording keeps it whatever becomes of the default.
            show(current.settings, own = true)
            persistSoon()
        } else {
            mutableState.update { it.copy(dialog = SoundDialog.BackToEveryone) }
        }
    }

    private fun confirm() {
        val dialog = state.value.dialog
        mutableState.update { it.copy(dialog = null) }
        when (dialog) {
            SoundDialog.BackToEveryone -> viewModelScope.launch {
                val id = sessionId ?: return@launch
                persistJob?.cancel()
                unsaved = false
                sound.clearOwn(id)
                val everyone = sound.default.first()
                show(everyone, own = false)
                mutableState.update { it.copy(savedHint = false) }
                player?.setSound(everyone)
            }
            SoundDialog.ResetEveryone -> edit { SoundRules.off(config) }
            is SoundDialog.DeletePreset -> viewModelScope.launch { sound.deletePreset(dialog.id) }
            SoundDialog.SavePreset, SoundDialog.PickRecording, null -> Unit
        }
    }

    private fun selectOriginal(intent: SoundIntent.OriginalSelected) {
        val current = player ?: return
        if (intent.held) {
            // «Пока держишь»: only a press that began at B goes back to B.
            if (intent.original && !current.state.value.original) {
                heldOriginal = true
                current.setOriginal(true)
            } else if (!intent.original && heldOriginal) {
                heldOriginal = false
                current.setOriginal(false)
            }
        } else {
            heldOriginal = false
            current.setOriginal(intent.original)
        }
    }

    private fun show(settings: SoundSettings, own: Boolean = state.value.own, loading: Boolean = state.value.loading) {
        val chips = SoundReducer.chipsOf(settings, userPresets, config)
        mutableState.update {
            it.copy(
                loading = loading, settings = settings, own = own, chips = chips, custom = chips.none { chip -> chip.selected },
                caption = SoundReducer.captionOf(settings, userPresets, config),
                canReset = SoundReducer.canReset(mode, own, settings, config),
            )
        }
    }

    private fun persistSoon() {
        unsaved = true
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_AFTER_MS)
            persist()
        }
    }

    /** Leaving the screen does not wait for the delay. */
    private fun flush() {
        if (backingUnsaved) {
            backingJob?.cancel()
            backingJob = viewModelScope.launch { persistBacking() }
        }
        if (!unsaved) return
        persistJob?.cancel()
        persistJob = viewModelScope.launch { persist() }
    }

    private suspend fun persist() = withContext(NonCancellable) {
        unsaved = false
        val current = state.value
        when {
            mode == SoundMode.EVERYONE -> sound.setDefault(current.settings)
            current.own -> sessionId?.let { sound.setOwn(it, current.settings) }
        }
    }

    override fun onCleared() {
        player?.release()
        player = null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"

        /** In place of a session id: the screen of the default. */
        const val EVERYONE = -1L

        private const val PERSIST_AFTER_MS = 400L
        private const val SAVED_HINT_MS = 3_000L
    }
}
