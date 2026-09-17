package com.example.violintuner.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val defaultConfig: IntonationConfig,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedState[ARG_SESSION_ID]) { "session id is required" }

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val effectChannel = Channel<SessionEffect>(Channel.BUFFERED)
    val effects: Flow<SessionEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            SessionIntent.BackClicked -> effectChannel.trySend(SessionEffect.Close)
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
                repository.delete(sessionId)
                effectChannel.send(SessionEffect.Close)
            }
        }
    }

    private suspend fun load() {
        val details = repository.details(sessionId)
        mutableState.value = if (details == null) {
            SessionState.NotFound
        } else {
            SessionState.Loaded(SessionContentMapper.contentOf(details, defaultConfig))
        }
    }

    private fun updateLoaded(change: (SessionState.Loaded) -> SessionState.Loaded) {
        mutableState.update { if (it is SessionState.Loaded) change(it) else it }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
