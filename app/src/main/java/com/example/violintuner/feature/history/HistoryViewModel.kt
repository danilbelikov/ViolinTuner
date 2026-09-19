package com.example.violintuner.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
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
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SessionRepository,
    repertoire: RepertoireRepository,
    private val config: IntonationConfig,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    // Lives as long as this view model, that is, as long as the tab is in the back stack:
    // returning from a piece finds the repertoire where it was left (spec 3.15).
    private val section = MutableStateFlow(HistorySection.SESSIONS)

    private val selection = MutableStateFlow(Selection())

    // What the list shows now: only that can be picked. Written where the state is built, read by
    // the intents — both on the main thread; `state.value` would lag a frame behind.
    private var visibleIds: List<Long> = emptyList()

    // "Today" is read on every change, so a list left open over midnight is right again as
    // soon as anything changes; the screen is rebuilt on every return to it anyway.
    val state: StateFlow<HistoryState> =
        combine(repository.sessions, repertoire.pieces, filter, section, selection) { sessions, pieces, filter, section, selection ->
            val shown = HistoryReducer.stateOf(
                sessions, filter, LocalDate.now(clock), clock.zone, config, section,
                pieceTitles = pieces.associate { it.id to it.title },
            )
            visibleIds = shown.cards.map { it.id }
            shown.copy(selection = SelectionRules.prune(selection, visibleIds))
        }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HistoryReducer.loading(filter.value, section.value),
    )

    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            // The filter and the section are dimmed while picking: what is picked must not hide under another filter.
            is HistoryIntent.FilterSelected -> if (!selection.value.active) filter.value = intent.filter
            is HistoryIntent.SectionSelected -> if (!selection.value.active) section.value = intent.section
            is HistoryIntent.SessionClicked ->
                if (selection.value.active) select(SelectionIntent.CardToggled(intent.id)) else effectChannel.trySend(HistoryEffect.OpenSession(intent.id))
            is HistoryIntent.Select -> select(intent.intent)
        }
    }

    private fun select(intent: SelectionIntent) {
        val current = SelectionRules.prune(selection.value, visibleIds)
        if (intent == SelectionIntent.DeleteConfirmed && current.ids.isNotEmpty()) {
            viewModelScope.launch { repository.delete(current.ids) }
        }
        selection.value = SelectionRules.reduce(current, intent, visibleIds)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
