package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.FakeHomeRepository
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeGroup
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.domain.venue.FakeVenueStore
import com.violinjourney.app.core.domain.venue.VenueRules
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.journey.art.SceneMode
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedWallClock(Instant.parse("2026-09-21T10:00:00Z"), TimeZone.UTC)
    private val journey = FakeJourneyRepository()
    private val home = FakeHomeRepository(journey)
    private val venueStore = FakeVenueStore(initial = VenueRules.HOME)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun earn(takts: Int) = journey.earn(TaktEarning(clock.millis(), takts, takts, 0, takts))

    private fun TestScope.viewModel(config: JourneyConfig = JourneyConfig()): Pair<HomeViewModel, MutableList<HomeEffect>> {
        val viewModel = HomeViewModel(home, journey, clock, Venues(venueStore, journey), config)
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

    /**
     * «Назад» folds what is open before it leaves the screen; out of the try-on — the arrow of its bar and the system one alike — it
     * does what «Убрать» does: the thing leaves the room and its card is open again (spec 3.36.7; before R7 it closed the card too).
     */
    @Test
    fun backFoldsWhatIsOpenBeforeItLeaves() = runTest(dispatcher) {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(HomeIntent.ItemClicked("pouf"))
        viewModel.onIntent(HomeIntent.TryClicked)
        viewModel.onIntent(HomeIntent.TryModeSelected(SceneMode.DAY))
        runCurrent()

        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertNull(viewModel.state.value.tryOn)
        assertNull(viewModel.state.value.tryMode)
        assertEquals("pouf", viewModel.state.value.card?.id)
        assertTrue(effects.isEmpty())

        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertNull(viewModel.state.value.card)
        assertTrue(effects.isEmpty())

        viewModel.onIntent(HomeIntent.BackClicked)
        runCurrent()
        assertEquals(listOf<HomeEffect>(HomeEffect.Close), effects)
    }

    /**
     * The shop by place (spec 3.36.7): the place the route gives is taken once in the life of the model — given again (a turn of the
     * phone), it changes nothing, not even after ✕ took it off; a place the catalogue does not know is not taken; ✕, «Всё» and a row
     * take it off — one filter at a time.
     */
    @Test
    fun aShopForOnePlaceShowsOnlyItsThings() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(HomeIntent.CategorySelected(HomeGroup.PET))
        viewModel.onIntent(HomeIntent.ShopSlotGiven("deskR"))
        runCurrent()
        assertEquals("deskR", viewModel.state.value.slot)
        assertNull("the place is the one filter", viewModel.state.value.category)

        viewModel.onIntent(HomeIntent.ShopSlotGiven("deskM"))
        runCurrent()
        assertEquals("once in the life of the model", "deskR", viewModel.state.value.slot)

        viewModel.onIntent(HomeIntent.SlotFilterCleared)
        runCurrent()
        assertNull(viewModel.state.value.slot)
        viewModel.onIntent(HomeIntent.ShopSlotGiven("deskR"))
        runCurrent()
        assertNull("a turn of the phone does not bring back the filter taken off", viewModel.state.value.slot)

        val (byRow, _) = viewModel()
        byRow.onIntent(HomeIntent.ShopSlotGiven("deskR"))
        byRow.onIntent(HomeIntent.CategorySelected(HomeGroup.LIGHT))
        runCurrent()
        assertNull(byRow.state.value.slot)
        assertEquals(HomeGroup.LIGHT, byRow.state.value.category)

        val (byAll, _) = viewModel()
        byAll.onIntent(HomeIntent.ShopSlotGiven("deskR"))
        byAll.onIntent(HomeIntent.CategorySelected(null))
        runCurrent()
        assertNull("«Всё» takes it off", byAll.state.value.slot)

        val (nowhere, _) = viewModel()
        nowhere.onIntent(HomeIntent.ShopSlotGiven("attic"))
        runCurrent()
        assertNull("not a place of the catalogue", nowhere.state.value.slot)
    }

    /**
     * The outline of «Обставить» (spec 3.36.7): a tile puts the outline on the thing it put — «пусто» too, which outlines nothing —, a
     * tile of another place moves it; a change of «Комната | Снаружи» takes it off, a touch of the side shown already does not (the
     * segment chosen answers a tap too); «в лавке N →» — which opens the shop by its place — takes it off, so it is gone when one comes
     * back.
     */
    @Test
    fun theArrangeFocusFollowsTheTouchedTileAndLeavesWithAChangeOfSideAndTheShop() = runTest(dispatcher) {
        earn(2_000)
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(HomeIntent.Placed("desk", "desk_simple"))
        runCurrent()
        assertEquals(ArrangeFocus("desk", "desk_simple"), viewModel.state.value.arrangeFocus)

        viewModel.onIntent(HomeIntent.Placed("deskTop", ""))
        runCurrent()
        assertEquals("«пусто» is a touch of the place too", ArrangeFocus("deskTop", ""), viewModel.state.value.arrangeFocus)

        viewModel.onIntent(HomeIntent.Placed("chair", "desk_oak")) // not its place: nothing is put, nothing moves
        runCurrent()
        assertEquals(ArrangeFocus("deskTop", ""), viewModel.state.value.arrangeFocus)

        viewModel.onIntent(HomeIntent.Placed("desk", "desk_simple"))
        viewModel.onIntent(HomeIntent.SideSelected(false)) // «Комната», shown already
        runCurrent()
        assertEquals("the side shown already: the outline stays", ArrangeFocus("desk", "desk_simple"), viewModel.state.value.arrangeFocus)
        assertFalse(viewModel.state.value.outside)

        viewModel.onIntent(HomeIntent.SideSelected(true))
        runCurrent()
        assertNull(viewModel.state.value.arrangeFocus)
        assertTrue(viewModel.state.value.outside)

        viewModel.onIntent(HomeIntent.Placed("oL", ""))
        runCurrent()
        assertEquals(ArrangeFocus("oL", ""), viewModel.state.value.arrangeFocus)
        viewModel.onIntent(HomeIntent.ShopAtClicked("oL"))
        runCurrent()
        assertNull(viewModel.state.value.arrangeFocus)
        assertEquals(HomeEffect.OpenShopAt("oL"), effects.last())
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

        viewModel.onIntent(HomeIntent.Placed("deskTop", ""))
        runCurrent()
        assertNull(HomeRules.placed(viewModel.state.value.home)["deskTop"])
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

    /**
     * The sheet of a house says «примерно N занятий» under the plate of what is missing (spec 3.36.7, 5.18) by the numbers of the
     * journey the graph gives — the same [JourneyConfig] as the journey's own screens — not by a constant of its own: the screens read
     * it from [HomeUi.config], loaded or not.
     */
    @Test
    fun theUiCarriesTheConfigOfTheGraph() = runTest(dispatcher) {
        val config = JourneyConfig(taktsPerSessionHint = 250)
        val (viewModel, _) = viewModel(config)
        assertEquals(config, viewModel.state.value.config)
        assertFalse("loaded", viewModel.state.value.loading)
        // and before anything is read: the first value of the state is the screen's own
        assertEquals(config, HomeViewModel(home, journey, clock, Venues(venueStore, journey), config).state.value.config)
    }

    @Test
    fun onTheRoadThePlayerLeavesHome() = runTest(dispatcher) {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(HomeIntent.TravelClicked)
        runCurrent()
        // wherever the road stands, now and after the next leg (spec 3.27)
        assertEquals(null, venueStore.stored.value)
        assertEquals(HomeEffect.OpenJourney, effects.last())
    }
}
