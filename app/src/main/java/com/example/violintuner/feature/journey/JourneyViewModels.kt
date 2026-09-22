package com.example.violintuner.feature.journey

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.journey.BoughtExtra
import com.example.violintuner.core.domain.journey.JourneyConfig
import com.example.violintuner.core.domain.journey.JourneyExtra
import com.example.violintuner.core.domain.journey.JourneyProgress
import com.example.violintuner.core.domain.journey.JourneyRepository
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.JourneyRules
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.domain.venue.Venues
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure: the stored journey → what its screens show. */
object JourneyReducer {
    val totalStops: Int get() = JourneyRoute.stops.size - 1

    fun visitedOf(progress: JourneyProgress): List<VisitedStop> = progress.arrivals
        .mapNotNull { arrival ->
            val index = JourneyRoute.indexOf(arrival.stopId)
            JourneyRoute.stops.getOrNull(index)?.let { VisitedStop(it, index, arrival.arrivedAtEpochMs, BoughtExtra(it.id, JourneyExtra.SOUVENIR) in progress.extras) }
        }
        .sortedBy { it.index }

    fun stateOf(progress: JourneyProgress, phase: JourneyPhase?): JourneyState {
        val index = JourneyRules.currentIndex(progress)
        val current = JourneyRoute.stops[index]
        return JourneyState(
            loading = false,
            phase = phase ?: if (progress.started) JourneyPhase.Idle else JourneyPhase.Intro,
            current = current,
            currentIndex = index,
            arrivedAtEpochMs = progress.arrivals.firstOrNull { it.stopId == current.id }?.arrivedAtEpochMs,
            next = JourneyRules.next(progress),
            balance = progress.balance,
            missing = JourneyRules.missing(progress),
            canDepart = JourneyRules.canDepart(progress),
            visited = visitedOf(progress),
            totalStops = totalStops,
        )
    }

    fun windowOf(progress: JourneyProgress, justEarned: Int? = null, here: Venue = Venue.Home) = JourneyWindow(
        current = JourneyRoute.stops[JourneyRules.currentIndex(progress)],
        next = JourneyRules.next(progress),
        balance = progress.balance,
        missing = JourneyRules.missing(progress),
        canDepart = JourneyRules.canDepart(progress),
        justEarned = justEarned,
        here = here,
    )

    val loading = JourneyState(
        loading = true, phase = JourneyPhase.Idle, current = JourneyRoute.stops.first(), currentIndex = 0, arrivedAtEpochMs = null, next = null,
        balance = 0, missing = 0, canDepart = false, visited = emptyList(), totalStops = totalStops,
    )
}

/** The journey's own screen: where the player is, the next leg, and the road when it is taken (spec 3.23). */
@HiltViewModel
class JourneyViewModel @Inject constructor(
    private val journey: JourneyRepository,
    private val clock: Clock,
    private val venues: Venues,
) : ViewModel() {
    // The road, the arrival and the stamp are moments of the screen, not of the journey: the leg is
    // paid and the stop reached before the train leaves, so a process that dies on the way loses nothing.
    private val phase = MutableStateFlow<JourneyPhase?>(null)
    private var reduceMotion = false
    private var latest = JourneyProgress.EMPTY

    val state: StateFlow<JourneyState> = combine(journey.progress, phase) { progress, phase ->
        latest = progress
        JourneyReducer.stateOf(progress, phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), JourneyReducer.loading)

    private val effectChannel = Channel<JourneyEffect>(Channel.BUFFERED)
    val effects: Flow<JourneyEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: JourneyIntent) {
        when (intent) {
            JourneyIntent.BackClicked -> if (phase.value == null) effectChannel.trySend(JourneyEffect.Close)
            JourneyIntent.IntroConfirmed -> viewModelScope.launch { journey.start(clock.millis()) }
            JourneyIntent.DepartClicked -> depart()
            JourneyIntent.StampClicked -> (phase.value as? JourneyPhase.Arrival)?.let { phase.value = JourneyPhase.Stamp(it.stop, it.index) }
            JourneyIntent.StampDone -> if (phase.value is JourneyPhase.Stamp) phase.value = null
            // «Сыграть здесь» after the stamp: the arrival has put the player in the city already (spec 3.27)
            JourneyIntent.PlayHereClicked -> if (phase.value is JourneyPhase.Stamp) {
                phase.value = null
                effectChannel.trySend(JourneyEffect.OpenLive)
            }
            JourneyIntent.MapClicked -> if (phase.value == null) effectChannel.trySend(JourneyEffect.OpenMap)
            JourneyIntent.PassportClicked -> if (phase.value == null) effectChannel.trySend(JourneyEffect.OpenPassport)
            is JourneyIntent.StopClicked -> if (phase.value == null) {
                // the door home takes the player home (spec 3.27): the room is where Live will be
                if (intent.stopId == JourneyRoute.HOME) viewModelScope.launch { venues.goHome() }
                effectChannel.trySend(JourneyEffect.OpenStop(intent.stopId))
            }
            is JourneyIntent.ReduceMotionChanged -> reduceMotion = intent.reduce
        }
    }

    // Opening a stop is the player's act, never automatic (handoff 26a): enough takts only light the button.
    private fun depart() {
        if (phase.value != null || !JourneyRules.canDepart(latest)) return
        val fromIndex = JourneyRules.currentIndex(latest)
        val from = JourneyRoute.stops[fromIndex]
        val to = JourneyRules.next(latest) ?: return
        val road = JourneyPhase.Road(from, to, fromIndex, JourneyMotion.roadMs(to.transport, reduceMotion))
        phase.value = road
        viewModelScope.launch {
            if (!journey.depart(to, clock.millis())) {
                phase.value = null
                return@launch
            }
            // whoever arrives is in the city: the window on «Занятия» and Live are there now
            venues.followRoad()
            delay(road.durationMs.toLong())
            phase.value = JourneyPhase.Arrival(to, fromIndex + 1)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** One stop: its postcard in the views that are open, its fact, and what can be bought here. */
@HiltViewModel
class StopViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val journey: JourneyRepository,
    private val config: JourneyConfig,
    private val clock: Clock,
    private val venues: Venues,
) : ViewModel() {
    private val stop = JourneyRoute.stops.firstOrNull { it.id == savedState.get<String>(ARG_STOP_ID) } ?: JourneyRoute.stops.first()
    private val index = JourneyRoute.indexOf(stop.id)

    private data class Shown(val day: Boolean = false, val inside: Boolean? = null, val fullscreen: Boolean = false)

    private val shown = MutableStateFlow(Shown())
    private var latest = JourneyProgress.EMPTY

    val state: StateFlow<StopState> = combine(journey.progress, shown) { progress, shown ->
        latest = progress
        val dayUnlocked = BoughtExtra(stop.id, JourneyExtra.SECOND_TIME) in progress.extras
        val secondViewUnlocked = BoughtExtra(stop.id, JourneyExtra.SECOND_VIEW) in progress.extras
        val mainInside = stop.views.firstOrNull()?.inside ?: false
        StopState(
            loading = false,
            stop = stop,
            index = index,
            totalStops = JourneyReducer.totalStops,
            arrivedAtEpochMs = progress.arrivals.firstOrNull { it.stopId == stop.id }?.arrivedAtEpochMs,
            balance = progress.balance,
            offers = JourneyRules.offers(stop, progress).map { extra ->
                val bought = BoughtExtra(stop.id, extra) in progress.extras
                ExtraOffer(extra, JourneyRules.priceOf(extra, config), bought, affordable = !bought && JourneyRules.canBuy(stop, extra, progress, config))
            },
            day = shown.day && dayUnlocked,
            inside = if (secondViewUnlocked) shown.inside ?: mainInside else mainInside,
            dayUnlocked = dayUnlocked,
            secondViewUnlocked = secondViewUnlocked,
            fullscreen = shown.fullscreen,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        StopState(true, stop, index, JourneyReducer.totalStops, null, 0, emptyList(), day = false, inside = stop.views.firstOrNull()?.inside ?: false, dayUnlocked = false, secondViewUnlocked = false),
    )

    private val effectChannel = Channel<StopEffect>(Channel.BUFFERED)

    val effects: Flow<StopEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: StopIntent) {
        when (intent) {
            // «назад» from the whole screen folds the postcard back, it does not leave the stop
            StopIntent.BackClicked -> if (shown.value.fullscreen) shown.value = shown.value.copy(fullscreen = false) else effectChannel.trySend(StopEffect.Close)
            // «Играть здесь» (spec 3.27): the player goes to this city and Live opens on its stage
            StopIntent.PlayHereClicked -> if (state.value.arrivedAtEpochMs != null) viewModelScope.launch {
                venues.choose(Venue.Hall(stop.id))
                effectChannel.send(StopEffect.OpenLive)
            }
            StopIntent.HomeClicked -> viewModelScope.launch {
                venues.goHome()
                effectChannel.send(StopEffect.OpenHome)
            }
            StopIntent.PostcardClicked -> shown.value = shown.value.copy(fullscreen = true)
            StopIntent.FullscreenClosed -> shown.value = shown.value.copy(fullscreen = false)
            is StopIntent.DaySelected -> shown.value = shown.value.copy(day = intent.day)
            is StopIntent.InsideSelected -> shown.value = shown.value.copy(inside = intent.inside)
            is StopIntent.BuyClicked -> buy(intent.extra)
        }
    }

    private fun buy(extra: JourneyExtra) {
        if (!JourneyRules.canBuy(stop, extra, latest, config)) return
        viewModelScope.launch {
            if (!journey.buy(stop, extra, JourneyRules.priceOf(extra, config), clock.millis())) return@launch
            // what was just opened is shown at once: that is what it was bought for
            when (extra) {
                JourneyExtra.SECOND_TIME -> shown.value = shown.value.copy(day = true)
                JourneyExtra.SECOND_VIEW -> shown.value = shown.value.copy(inside = !(stop.views.firstOrNull()?.inside ?: false))
                JourneyExtra.SOUVENIR -> Unit
            }
        }
    }

    companion object {
        const val ARG_STOP_ID = "stopId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
