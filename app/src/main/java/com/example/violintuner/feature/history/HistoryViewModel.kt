package com.example.violintuner.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: SessionRepository,
    private val config: IntonationConfig,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    // "Today" is read on every change, so a list left open over midnight is right again as
    // soon as anything changes; the screen is rebuilt on every return to it anyway.
    val state: StateFlow<HistoryState> = combine(repository.sessions, filter) { sessions, filter ->
        HistoryReducer.stateOf(sessions, filter, LocalDate.now(clock), clock.zone, config)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HistoryReducer.loading(filter.value),
    )

    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.FilterSelected -> filter.value = intent.filter
            is HistoryIntent.SessionClicked -> effectChannel.trySend(HistoryEffect.OpenSession(intent.id))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
