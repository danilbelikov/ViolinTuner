package com.violinjourney.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

open class HistoryViewModel(
    private val repository: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val config: IntonationConfig,
    private val clock: WallClock,
    private val audioFiles: SessionAudioFiles,
    private val sectionAsk: HistorySectionAsk = HistorySectionAsk(),
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    // Lives as long as this view model, that is, as long as the tab is in the back stack:
    // returning from a piece finds the repertoire where it was left (spec 3.15).
    private val section = MutableStateFlow(HistorySection.SESSIONS)

    private val selection = MutableStateFlow(Selection())

    init {
        // «Открыть репертуар» from Live (spec 3.28) asks for a section: it is taken once and forgotten, the tab stays
        // wherever the player moves it afterwards
        viewModelScope.launch {
            sectionAsk.asked.collect {
                sectionAsk.take()?.let { wanted ->
                    selection.value = Selection()
                    section.value = wanted
                }
            }
        }
    }

    // What the list shows now: only that can be picked. Written where the state is built, read by
    // the intents — both on the main thread; `state.value` would lag a frame behind.
    private var visibleIds: List<Long> = emptyList()
    private var shownCards: List<HistoryCard> = emptyList()

    // "Today" is read on every change, so a list left open over midnight is right again as
    // soon as anything changes; the screen is rebuilt on every return to it anyway.
    val state: StateFlow<HistoryState> =
        combine(repository.sessions, repertoire.pieces, filter, section, selection) { sessions, pieces, filter, section, selection ->
            val shown = HistoryReducer.stateOf(
                sessions, filter, clock.today(), clock.zone, config, section,
                pieceTitles = pieces.associate { it.id to it.title },
                bestTakeIds = pieces.mapNotNull { it.bestTakeId }.toSet(),
            )
            visibleIds = shown.cards.map { it.id }
            shownCards = shown.cards
            // The dialog that deletes names the weight of what goes (spec 3.19): videos are few, and a length is cheap to ask.
            val videos = sessions.mapNotNull { session -> session.videoPath?.let { session.id to it } }.toMap()
            shown.copy(
                cards = shown.cards.map { card -> videos[card.id]?.let { card.copy(videoBytes = audioFiles.existing(it)?.sizeBytes() ?: 0) } ?: card },
                selection = SelectionRules.prune(selection, visibleIds),
            )
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
            is HistoryIntent.BestToggled -> shownCards.firstOrNull { it.id == intent.id }?.let { card ->
                val pieceId = card.pieceId ?: return
                viewModelScope.launch { repertoire.setBestTake(pieceId, if (card.best) null else card.id) }
            }
        }
    }

    private fun select(intent: SelectionIntent) {
        val current = SelectionRules.prune(selection.value, visibleIds)
        if (intent == SelectionIntent.DeleteConfirmed && current.ids.isNotEmpty()) {
            viewModelScope.launch { repository.delete(current.ids) }
        }
        selection.value = SelectionRules.reduce(current, intent, visibleIds)
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
