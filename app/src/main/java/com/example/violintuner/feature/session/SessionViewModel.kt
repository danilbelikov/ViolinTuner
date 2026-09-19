package com.example.violintuner.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.playback.SessionPlayer
import com.example.violintuner.core.audio.playback.SessionPlayerFactory
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRepository
import com.example.violintuner.feature.sound.SoundReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val defaultConfig: IntonationConfig,
    private val audioFiles: SessionAudioFiles,
    private val playerFactory: SessionPlayerFactory,
    private val repertoire: RepertoireRepository,
    private val sound: SoundRepository,
    private val soundConfig: SoundConfig,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedState[ARG_SESSION_ID]) { "session id is required" }

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private var player: SessionPlayer? = null

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
            SessionIntent.SoundClicked -> effectChannel.trySend(SessionEffect.OpenSound(sessionId))
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
            SessionIntent.DeleteConfirmed -> viewModelScope.launch {
                player?.release() // the file is about to go
                repository.delete(sessionId)
                effectChannel.send(SessionEffect.Close)
            }
        }
    }

    private suspend fun load() {
        val details = repository.details(sessionId)
        val pieceTitle = details?.summary?.pieceId?.let { repertoire.piece(it) }?.title
        mutableState.update { previous ->
            if (details == null) {
                SessionState.NotFound
            } else {
                // a reload after renaming keeps what is open and what is playing
                val content = SessionContentMapper.contentOf(details, defaultConfig).copy(pieceTitle = pieceTitle)
                (previous as? SessionState.Loaded ?: SessionState.Loaded(content)).copy(content = content, dialog = null)
            }
        }
        if (player == null) details?.summary?.audioPath?.let(audioFiles::existing)?.let(::startPlayer)
    }

    private fun startPlayer(file: File) {
        val created = playerFactory.create(viewModelScope)
        player = created
        created.load(file)
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
                updateLoaded { it.copy(player = playerState.takeIf { state -> state.ready && !state.failed }) }
            }
        }
    }

    override fun onCleared() {
        player?.release()
        player = null
    }

    private fun updateLoaded(change: (SessionState.Loaded) -> SessionState.Loaded) {
        mutableState.update { if (it is SessionState.Loaded) change(it) else it }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
