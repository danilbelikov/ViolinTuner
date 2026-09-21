package com.example.violintuner.feature.home

import com.example.violintuner.core.domain.home.HomeCatalog
import com.example.violintuner.core.domain.home.HomeHouse
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.core.domain.home.HomeRepository
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.core.domain.journey.FakeJourneyRepository
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.TaktEarning
import com.example.violintuner.feature.journey.art.SceneMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The home in memory, paid from the journey's purse as the real one is. */
class FakeHomeRepository(private val journey: FakeJourneyRepository) : HomeRepository {
    override val state = MutableStateFlow(HomeState.EMPTY.copy(loaded = true))

    private fun pay(price: Int): Boolean {
        val progress = journey.progress.value
        if (progress.balance < price) return false
        journey.progress.value = progress.copy(spent = progress.spent + price)
        return true
    }

    override suspend fun buy(item: HomeItem, nowEpochMs: Long): Boolean {
        if (item.id in state.value.purchased || !pay(item.price)) return false
        state.update { it.copy(purchased = it.purchased + item.id, choices = it.choices + (item.slot to item.id)) }
        return true
    }

    override suspend fun buy(house: HomeHouse, nowEpochMs: Long): Boolean {
        if (!house.drawn || house.id in state.value.houses || !pay(house.price)) return false
        state.update { it.copy(houses = it.houses + house.id, choices = it.choices + (HomeState.HOUSE_KEY to house.id)) }
        return true
    }

    override suspend fun place(slot: String, itemId: String) = state.update { it.copy(choices = it.choices + (slot to itemId)) }

    override suspend fun liveIn(house: String) = state.update { it.copy(choices = it.choices + (HomeState.HOUSE_KEY to house)) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC)
    private val journey = FakeJourneyRepository()
    private val home = FakeHomeRepository(journey)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun earn(takts: Int) = journey.earn(TaktEarning(clock.millis(), takts, takts, 0, takts))

    private fun TestScope.viewModel(): Pair<HomeViewModel, MutableList<HomeEffect>> {
        val viewModel = HomeViewModel(home, journey, clock)
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun thePresentIsTakenForNothing_andTheViolinLeavesItsCase() = runTest(dispatcher) {
        val (viewModel, effects) = viewModel()
        assertTrue(HomeRules.giftWaiting(viewModel.state.value.home))

        viewModel.onIntent(HomeIntent.GiftTaken)
        runCurrent()

        assertFalse(HomeRules.giftWaiting(viewModel.state.value.home))
        assertEquals(HomeCatalog.GIFT, HomeRules.placed(viewModel.state.value.home)["violin"]?.id)
        assertEquals(HomeEffect.ShowBought(HomeCatalog.GIFT), effects.single())
        assertEquals(0L, viewModel.state.value.progress.spent)
    }

    @Test
    fun aThingIsLookedAt_triedOn_bought_andStandsInItsPlace_outOfTheSamePurseAsTheRoad() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(1_000)
        val (viewModel, effects) = viewModel()

        viewModel.onIntent(HomeIntent.ItemClicked("armchair")) // 700
        runCurrent()
        assertEquals("armchair", viewModel.state.value.card?.id)

        viewModel.onIntent(HomeIntent.TryClicked)
        viewModel.onIntent(HomeIntent.TryModeSelected(SceneMode.DAY))
        runCurrent()
        assertEquals("armchair", viewModel.state.value.tryOn?.id)
        assertNull(viewModel.state.value.card)
        assertEquals(SceneMode.DAY, viewModel.state.value.tryMode)

        viewModel.onIntent(HomeIntent.BuyClicked)
        runCurrent()
        val ui = viewModel.state.value
        assertNull(ui.tryOn)
        assertEquals("armchair", HomeRules.placed(ui.home)["chair"]?.id)
        assertEquals(300L, ui.balance)
        assertEquals(HomeEffect.ShowBought("armchair"), effects.last())
        // what is left is not enough for Cremona and its 300? it is, exactly — the purse is one
        assertTrue(ui.balance >= JourneyRoute.stops[1].price)

        // too dear now; and a thing of a city not reached has no card to buy from
        viewModel.onIntent(HomeIntent.ItemClicked("bookshelf")) // 800
        viewModel.onIntent(HomeIntent.BuyClicked)
        runCurrent()
        assertFalse(HomeRules.owned(HomeCatalog.byId.getValue("bookshelf"), viewModel.state.value.home))
        viewModel.onIntent(HomeIntent.CardClosed)
        viewModel.onIntent(HomeIntent.ItemClicked("tulips")) // 150, from Amsterdam
        viewModel.onIntent(HomeIntent.BuyClicked)
        runCurrent()
        assertFalse(HomeRules.owned(HomeCatalog.byId.getValue("tulips"), viewModel.state.value.home))
    }

    @Test
    fun backFoldsWhatIsOpenBeforeItLeaves() = runTest(dispatcher) {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(HomeIntent.ItemClicked("pouf"))
        viewModel.onIntent(HomeIntent.TryClicked)
        runCurrent()

        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertNull(viewModel.state.value.tryOn)
        assertTrue(effects.isEmpty())

        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertEquals(listOf<HomeEffect>(HomeEffect.Close), effects)
    }

    @Test
    fun theWardrobePutsOnlyWhatIsOwnedAndOnlyInItsOwnPlace() = runTest(dispatcher) {
        earn(2_000)
        val (viewModel, _) = viewModel()
        viewModel.onIntent(HomeIntent.ItemClicked("desk_oak"))
        viewModel.onIntent(HomeIntent.BuyClicked)
        runCurrent()

        viewModel.onIntent(HomeIntent.Placed("desk", "desk_simple"))
        runCurrent()
        assertEquals("desk_simple", HomeRules.placed(viewModel.state.value.home)["desk"]?.id)

        viewModel.onIntent(HomeIntent.Placed("desk", "bureau")) // not owned
        viewModel.onIntent(HomeIntent.Placed("chair", "desk_oak")) // not its place
        runCurrent()
        assertEquals("desk_simple", HomeRules.placed(viewModel.state.value.home)["desk"]?.id)
        assertEquals("chair_simple", HomeRules.placed(viewModel.state.value.home)["chair"]?.id)

        viewModel.onIntent(HomeIntent.Placed("rug", ""))
        runCurrent()
        assertNull(HomeRules.placed(viewModel.state.value.home)["rug"])
    }

    @Test
    fun aHomeIsPaidForBeforeTheFilm_thenOneLivesThere_andCanGoBack() = runTest(dispatcher) {
        earn(3_500)
        val (viewModel, effects) = viewModel()

        viewModel.onIntent(HomeIntent.HouseClicked("chalet")) // not drawn yet: no card
        runCurrent()
        assertNull(viewModel.state.value.houseCard)

        viewModel.onIntent(HomeIntent.HouseClicked("wood"))
        viewModel.onIntent(HomeIntent.MoveClicked)
        runCurrent()
        assertEquals("wood", viewModel.state.value.moving?.id)
        assertEquals("wood", viewModel.state.value.house)
        assertEquals(500L, viewModel.state.value.balance)
        // nothing answers during the film
        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertTrue(effects.isEmpty())

        advanceTimeBy(HomeMotion.MOVE_MS + 1)
        runCurrent()
        assertNull(viewModel.state.value.moving)
        assertEquals(HomeEffect.OpenHome, effects.last())

        viewModel.onIntent(HomeIntent.LiveHere(HomeCatalog.START_HOUSE))
        runCurrent()
        assertEquals(HomeCatalog.START_HOUSE, viewModel.state.value.house)
        viewModel.onIntent(HomeIntent.LiveHere("villa"))
        runCurrent()
        assertEquals(HomeCatalog.START_HOUSE, viewModel.state.value.house)
    }
}
