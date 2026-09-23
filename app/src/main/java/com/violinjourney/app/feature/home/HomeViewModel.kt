package com.violinjourney.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeRepository
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.venue.Venues
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The home: what stands in it, the shop with its trying-on, the wardrobe, the homes (spec 3.24). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val home: HomeRepository,
    journey: JourneyRepository,
    private val clock: Clock,
    private val venues: Venues,
) : ViewModel() {
    /** What only the screen decides. */
    private val look = MutableStateFlow(HomeUi(loading = true, home = HomeState.EMPTY, progress = JourneyProgress.EMPTY, house = HomeCatalog.START_HOUSE))
    private var reduceMotion = false

    // intents read these, not state.value: it lags a frame behind the stores
    private var latestHome = HomeState.EMPTY
    private var latestProgress = JourneyProgress.EMPTY

    val state: StateFlow<HomeUi> = combine(home.state, journey.progress, look) { home, progress, look ->
        latestHome = home
        latestProgress = progress
        look.copy(loading = !home.loaded, home = home, progress = progress, house = HomeRules.house(home))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), look.value)

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: HomeIntent) {
        val now = look.value
        when (intent) {
            // «назад» folds what is open before it leaves the screen
            HomeIntent.BackClicked -> when {
                now.moving != null -> Unit
                now.fullscreen -> look.update { it.copy(fullscreen = false) }
                now.tryOn != null -> look.update { it.copy(tryOn = null, tryMode = null) }
                now.card != null -> look.update { it.copy(card = null) }
                now.houseCard != null -> look.update { it.copy(houseCard = null) }
                else -> effectChannel.trySend(HomeEffect.Close)
            }
            is HomeIntent.SideSelected -> look.update { it.copy(outside = intent.outside) }
            HomeIntent.ShopClicked -> effectChannel.trySend(HomeEffect.OpenShop)
            HomeIntent.ArrangeClicked -> effectChannel.trySend(HomeEffect.OpenArrange)
            HomeIntent.HousesClicked -> effectChannel.trySend(HomeEffect.OpenHouses)
            // «В дорогу»: the player leaves home for where the road stands (spec 3.27)
            HomeIntent.TravelClicked -> {
                viewModelScope.launch { venues.followRoad() }
                effectChannel.trySend(HomeEffect.OpenJourney)
            }
            HomeIntent.FullscreenClicked -> look.update { it.copy(fullscreen = true) }
            HomeIntent.FullscreenClosed -> look.update { it.copy(fullscreen = false) }
            HomeIntent.GiftTaken -> buy(HomeCatalog.GIFT)
            is HomeIntent.CategorySelected -> look.update { it.copy(category = intent.group) }
            is HomeIntent.ItemClicked -> HomeCatalog.byId[intent.id]?.let { item -> look.update { it.copy(card = item) } }
            HomeIntent.CardClosed -> look.update { it.copy(card = null) }
            HomeIntent.TryClicked -> now.card?.let { item -> look.update { it.copy(tryOn = item, card = null) } }
            is HomeIntent.TryModeSelected -> look.update { it.copy(tryMode = intent.mode) }
            HomeIntent.TryClosed -> look.update { it.copy(card = it.tryOn, tryOn = null, tryMode = null) }
            HomeIntent.BuyClicked -> (now.tryOn ?: now.card)?.let { buy(it.id) }
            is HomeIntent.Placed -> place(intent.slot, intent.itemId)
            is HomeIntent.HouseClicked -> HomeCatalog.houseById[intent.id]?.takeIf { it.drawn && it.id !in HomeRules.ownedHouses(latestHome) }?.let { house -> look.update { it.copy(houseCard = house) } }
            HomeIntent.HouseCardClosed -> look.update { it.copy(houseCard = null) }
            HomeIntent.MoveClicked -> move()
            is HomeIntent.LiveHere -> if (intent.id in HomeRules.ownedHouses(latestHome)) viewModelScope.launch { home.liveIn(intent.id) }
            is HomeIntent.ReduceMotionChanged -> reduceMotion = intent.reduce
        }
    }

    private fun buy(id: String) {
        val item = HomeCatalog.byId[id] ?: return
        if (!HomeRules.canBuy(item, latestHome, latestProgress)) return
        viewModelScope.launch {
            if (!home.buy(item, clock.millis())) return@launch
            look.update { it.copy(card = null, tryOn = null, tryMode = null) }
            effectChannel.trySend(HomeEffect.ShowBought(item.id))
        }
    }

    private fun place(slot: String, itemId: String) {
        val fits = itemId.isEmpty() || HomeCatalog.byId[itemId]?.let { it.slot == slot && HomeRules.owned(it, latestHome) } == true
        if (fits) viewModelScope.launch { home.place(slot, itemId) }
    }

    // The home is paid for before the film starts: a process that dies in it loses nothing.
    private fun move() {
        val house = look.value.houseCard ?: return
        if (look.value.moving != null || !HomeRules.canBuy(house, latestHome, latestProgress)) return
        viewModelScope.launch {
            if (!home.buy(house, clock.millis())) return@launch
            look.update { it.copy(houseCard = null, moving = house, outside = false) }
            delay(if (reduceMotion) HomeMotion.MOVE_REDUCED_MS else HomeMotion.MOVE_MS)
            look.update { it.copy(moving = null) }
            effectChannel.trySend(HomeEffect.OpenHome)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** What the rest of the app needs of the home to draw it: the journey's screen, the window on «Занятия». */
@HiltViewModel
class HomeLookViewModel @Inject constructor(home: HomeRepository) : ViewModel() {
    val state: StateFlow<HomeState> = home.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeState.EMPTY)
}
