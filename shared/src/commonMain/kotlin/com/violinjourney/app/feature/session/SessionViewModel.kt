package com.violinjourney.app.feature.session

import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.PlayerBacking
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.NoBackings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.playback.SessionPlayer
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.VideoPicture
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.playback.VideoSurfaceHandle
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.feature.sound.SoundReducer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class SessionViewModel(
    private val repository: SessionRepository,
    private val defaultConfig: IntonationConfig,
    private val audioFiles: SessionAudioFiles,
    private val playerFactory: SessionPlayerFactory,
    private val repertoire: RepertoireRepository,
    private val sound: SoundRepository,
    private val soundConfig: SoundConfig,
    private val pictureFactory: VideoPictureFactory,
    savedState: SavedStateHandle,
    private val backings: BackingRepository = NoBackings,
    private val backingPcm: BackingPcm? = null,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedState[ARG_SESSION_ID]) { "session id is required" }

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private var player: SessionPlayer? = null
    private var picture: VideoPicture? = null

    private val effectChannel = Channel<SessionEffect>(Channel.BUFFERED)
    val effects: Flow<SessionEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            SessionIntent.BackClicked -> effectChannel.trySend(SessionEffect.Close)
            SessionIntent.PlayPauseClicked -> player?.let { if (it.state.value.playing) it.pause() else it.play() }
            is SessionIntent.SeekRequested -> player?.seekTo(intent.positionMs)
            is SessionIntent.OriginalSelected -> player?.setOriginal(intent.original)
            is SessionIntent.BackingHeardSelected -> player?.setBackingHeard(intent.heard)
            SessionIntent.SoundClicked -> effectChannel.trySend(SessionEffect.OpenSound(sessionId))
            SessionIntent.BestClicked -> toggleBest()
            SessionIntent.ShareClicked -> {
                player?.pause() // the system sheet comes up over a silent screen
                effectChannel.trySend(SessionEffect.Share(sessionId))
            }
            SessionIntent.ScreenStopped -> player?.pause()
            is SessionIntent.SegmentClicked -> updateLoaded {
                it.copy(selectedSegment = intent.index.takeIf { index -> index in it.content.segments.indices })
            }
            SessionIntent.NoteSheetDismissed -> updateLoaded { it.copy(selectedSegment = null) }
            SessionIntent.RenameClicked -> updateLoaded { it.copy(dialog = SessionDialog.RENAME) }
            SessionIntent.DeleteClicked -> updateLoaded { it.copy(dialog = SessionDialog.DELETE) }
            SessionIntent.DialogDismissed -> updateLoaded { it.copy(dialog = null) }
            is SessionIntent.RenameConfirmed -> viewModelScope.launch {
                repository.rename(sessionId, intent.title)
                load() // the repository decides what a blank name means
            }
            is SessionIntent.FullscreenChanged -> updateLoaded { it.copy(fullscreen = intent.fullscreen && it.video?.lost == false) }
            is SessionIntent.PlaySegmentClicked -> {
                val loaded = state.value as? SessionState.Loaded
                val segment = loaded?.content?.segments?.getOrNull(intent.index)
                val current = player
                if (segment != null && current != null) {
                    // a second earlier: the way into the note is heard — and seen — too
                    current.seekTo((segment.startMs - LEAD_IN_MS).coerceAtLeast(0))
                    current.play()
                    updateLoaded { it.copy(selectedSegment = null) }
                }
            }
            SessionIntent.DeleteConfirmed -> viewModelScope.launch {
                picture?.release()
                picture = null
                player?.release() // the file is about to go
                repository.delete(sessionId)
                effectChannel.send(SessionEffect.Close)
            }
        }
    }

    /** Marks this take as the best of its piece, or clears the mark it has; says so only when a mark was set. */
    private fun toggleBest() {
        val content = (mutableState.value as? SessionState.Loaded)?.content ?: return
        val pieceId = content.pieceId ?: return
        viewModelScope.launch {
            val former = repertoire.piece(pieceId)?.bestTakeId
            repertoire.setBestTake(pieceId, if (content.best) null else sessionId)
            if (!content.best) effectChannel.send(SessionEffect.ShowBestMarked(moved = former != null && former != sessionId))
            load()
        }
    }

    private suspend fun load() {
        val details = repository.details(sessionId)
        val piece = details?.summary?.pieceId?.let { repertoire.piece(it) }
        mutableState.update { previous ->
            if (details == null) {
                SessionState.NotFound
            } else {
                // a reload after renaming keeps what is open and what is playing
                val content = SessionContentMapper.contentOf(details, defaultConfig)
                    .copy(pieceTitle = piece?.title, pieceId = piece?.id, best = piece?.bestTakeId == sessionId)
                (previous as? SessionState.Loaded ?: SessionState.Loaded(content)).copy(content = content, dialog = null)
            }
        }
        if (player == null) details?.summary?.audioPath?.let(audioFiles::existing)?.let(::startPlayer)
        if (picture == null) details?.summary?.videoPath?.let(::startPicture)
    }

    private var surface: VideoSurfaceHandle? = null

    /** The surface the picture is drawn onto. Not an intent: a surface is not state. */
    fun attachSurface(next: VideoSurfaceHandle) {
        surface = next
        picture?.setSurface(next)
    }

    /**
     * [gone] is about to be destroyed. Going into and out of the full screen swaps one view for
     * another, and the new surface may well arrive before the old one leaves: only the surface
     * in use takes the picture with it.
     */
    fun detachSurface(gone: VideoSurfaceHandle) {
        if (surface !== gone) return
        surface = null
        picture?.setSurface(null)
    }

    private fun startPicture(name: String) {
        val file = audioFiles.existing(name)
        if (file == null) {
            updateLoaded { it.copy(video = VideoUi(lost = true)) }
            return
        }
        val created = pictureFactory.create(file)
        picture = created
        surface?.let(created::setSurface) // the view may have been there before the session was read
        updateLoaded { it.copy(video = VideoUi(sizeBytes = file.sizeBytes())) }
        viewModelScope.launch {
            created.state.collect { picture ->
                updateLoaded {
                    it.copy(video = it.video?.copy(width = picture.width, height = picture.height, showing = picture.showing, undecodable = picture.failed))
                }
            }
        }
        // The picture follows the sound: every word of the player is passed on, the renderer carries the clock on in between.
        player?.let { sound ->
            viewModelScope.launch { sound.state.collect { created.follow(it.positionMs, it.playing) } }
        }
    }

    private fun startPlayer(file: PlatformFile) {
        val created = playerFactory.create(viewModelScope)
        player = created
        viewModelScope.launch {
            // a take under a backing plays with it, mixed as its «Звук» sets it (spec 3.32)
            val take = backings.takeBackings.first().firstOrNull { it.sessionId == sessionId }
            val backing = take?.let { backings.backing(it.backingId) }
            val pcm = backingPcm
            if (take == null || backing == null || pcm == null) {
                created.load(file)
                return@launch
            }
            created.loadWithBacking(file, PlayerBacking(pcm = { rate -> pcm.prepare(backing, rate) }, offsetMs = take.offsetMs, gainDb = take.gainDb, cached = { rate -> pcm.cached(backing, rate) }))
            // the shift or the level changed on «Звук» and came back here: heard at once
            backings.takeBackings.collect { all -> all.firstOrNull { it.sessionId == sessionId }?.let { created.setBackingMix(it.offsetMs, it.gainDb) } }
        }
        // The recording plays the way its settings make it sound — its own, or those of all (spec 3.17);
        // change either while it plays, and it is heard at once.
        viewModelScope.launch {
            combine(sound.effective(sessionId), sound.presets) { effective, presets ->
                effective to SoundRow(SoundReducer.captionOf(effective.settings, presets, soundConfig), effective.own)
            }.collect { (effective, row) ->
                created.setSound(effective.settings)
                updateLoaded { it.copy(sound = row) }
            }
        }
        viewModelScope.launch {
            created.state.collect { playerState ->
                updateLoaded { it.copy(player = playerState.takeIf { state -> state.ready && !state.failed }, preparingBacking = playerState.preparingBacking && !playerState.failed) }
            }
        }
    }

    override fun onCleared() {
        picture?.release()
        picture = null
        player?.release()
        player = null
    }

    private fun updateLoaded(change: (SessionState.Loaded) -> SessionState.Loaded) {
        mutableState.update { if (it is SessionState.Loaded) change(it) else it }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"

        /** «Смотреть это место» starts this much before the note (spec 5.13). */
        private const val LEAD_IN_MS = 1_000L
    }
}
