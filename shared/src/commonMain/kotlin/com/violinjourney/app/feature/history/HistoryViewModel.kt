package com.violinjourney.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.takesUnderBacking
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.Performances
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.ticksAt
import com.violinjourney.app.core.time.today
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class HistoryViewModel(
    private val repository: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val config: IntonationConfig,
    private val clock: WallClock,
    private val audioFiles: SessionAudioFiles,
    private val backings: BackingRepository,
    /**
     * The events (spec 3.35): a recording of one is named by it until it is given a name of its own; the performances among them make
     * the caption of the row «Выступления» (spec 3.36.9).
     */
    private val events: EventRepository,
    private val eventsConfig: EventsConfig,
    /** Where the list is built: off the main thread — sorting, dates and the sizes of the videos grow with the records. */
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private val selection = MutableStateFlow(Selection())

    // What the list shows now: only that can be picked. Written where the state is built, read by
    // the intents — both on the main thread; `state.value` would lag a frame behind.
    private var visibleIds: List<Long> = emptyList()
    private var shownCards: List<HistoryCard> = emptyList()

    // "Today" is read on every change, so a list left open over midnight is right again as
    // soon as anything changes; the screen is rebuilt on every return to it anyway.
    // The list itself, without the picking: built off the main thread, and only when the records, the pieces, the filter,
    // the backings or the events of the records change — a tap in the selection mode does not build it again.
    private val listed: Flow<HistoryState> = flow {
        // The sizes of the videos by file name, asked of the file system once while the list is watched: a video never
        // changes under its name. One map per collection — its steps run one after another.
        val videoSizes = HashMap<String, Long>()
        emitAll(
            combine(repository.sessions, repertoire.pieces, filter, backings.takesUnderBacking, events.recordEvents) { sessions, pieces, chosen, underBacking, recordEvents ->
                // Nothing recorded at all shows no chips (spec 3.36.5): the chip is «Все» again, its default, so the first recording
                // to come does not stand hidden under a chip that could not be seen. A chip with nothing under it keeps its choice.
                val filter = if (sessions.isEmpty()) HistoryFilter.ALL else chosen
                if (filter != chosen) this@HistoryViewModel.filter.value = filter
                val shown = HistoryReducer.stateOf(
                    sessions, filter, clock.today(), clock.zone, config,
                    pieceTitles = pieces.associate { it.id to it.title },
                    bestTakeIds = pieces.mapNotNull { it.bestTakeId }.toSet(),
                    underBackingIds = underBacking,
                    recordEvents = recordEvents,
                )
                // The dialog that deletes names the weight of what goes (spec 3.19): videos are few, and a length is cheap to ask.
                // A file that is gone is not remembered: it may come back with a restored copy.
                val videos = sessions.mapNotNull { session -> session.videoPath?.let { session.id to it } }.toMap()
                shown.copy(
                    cards = shown.cards.map { card ->
                        videos[card.id]?.let { name ->
                            card.copy(videoBytes = videoSizes[name] ?: audioFiles.existing(name)?.sizeBytes()?.also { videoSizes[name] = it } ?: 0)
                        } ?: card
                    },
                )
            },
        )
    }.flowOn(background)

    /**
     * The row «Выступления» (spec 3.36.9), apart from the list — it changes with the events and the time, the list does not: anew whenever
     * the events or their kinds change, and by itself at the end of the nearest performance and at midnight ([Performances.nextChangeAt],
     * plan D42), never in between ([ticksAt], plan D9).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val performances: Flow<HistoryPerformances> = combine(events.events, events.kinds, ::Pair)
        .flatMapLatest { (all, kinds) ->
            // the zone is read at each wake: a flight changes it under an open screen
            clock.ticksAt { at -> Performances.nextChangeAt(all, at, clock.zone, eventsConfig) }
                .map { at -> HistoryReducer.performancesOf(all, kinds, at, clock.zone, eventsConfig) }
        }
        .distinctUntilChanged()

    // The first state waits for the events too: the row comes with the strip and the chips, never after them (spec 3.36.9).
    val state: StateFlow<HistoryState> =
        combine(listed, selection, performances) { shown, selection, performances ->
            visibleIds = shown.cards.map { it.id }
            shownCards = shown.cards
            shown.copy(selection = SelectionRules.prune(selection, visibleIds), performances = performances)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryReducer.loading(filter.value),
        )

    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryEffect> = effectChannel.receiveAsFlow()

    /**
     * The row «Выступления» has been pressed: they are on their way, and the row is not heard until the tab is in sight again
     * ([HistoryIntent.Shown]). The second tap of a double tap that falls through to the tab going away — while «Выступления» are read
     * nothing of them takes it — would wait in the channel of a route that stopped collecting and open them again after «назад» (5.29 R9,
     * review of stage 99; as «Сначала сохранить текущие данные» of the passport, stage 122). `launchSingleTop` cannot see it: the first
     * screen is long gone from the top by then.
     */
    private var performancesOpened = false

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            // The filter is dimmed while picking: what is picked must not hide under another filter.
            is HistoryIntent.FilterSelected -> if (!selection.value.active) filter.value = intent.filter
            is HistoryIntent.SessionClicked ->
                if (selection.value.active) select(SelectionIntent.CardToggled(intent.id)) else effectChannel.trySend(HistoryEffect.OpenSession(intent.id))
            is HistoryIntent.Select -> select(intent.intent)
            is HistoryIntent.BestToggled -> shownCards.firstOrNull { it.id == intent.id }?.let { card ->
                val pieceId = card.pieceId ?: return
                viewModelScope.launch { repertoire.setBestTake(pieceId, if (card.best) null else card.id) }
            }
            HistoryIntent.OpenLiveClicked -> effectChannel.trySend(HistoryEffect.OpenLive)
            // the row is gone while picking (spec 3.36.9): a tap that reaches it then is not heard
            HistoryIntent.PerformancesClicked -> if (!selection.value.active && !performancesOpened) {
                performancesOpened = true
                effectChannel.trySend(HistoryEffect.OpenPerformances)
            }
            HistoryIntent.Shown -> performancesOpened = false
        }
    }

    // The picked ones, or the one card of «Удалить…» (spec 3.36.5), go the same way: their rows in one transaction, then the files.
    private fun select(intent: SelectionIntent) {
        val current = SelectionRules.prune(selection.value, visibleIds)
        if (intent == SelectionIntent.DeleteConfirmed && current.confirming && current.ids.isNotEmpty()) {
            viewModelScope.launch { repository.delete(current.ids) }
        }
        selection.value = SelectionRules.reduce(current, intent, visibleIds)
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
