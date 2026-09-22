package com.example.violintuner.feature.journey

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.domain.journey.FakeJourneyRepository
import com.example.violintuner.core.domain.journey.JourneyConfig
import com.example.violintuner.core.domain.journey.JourneyExtra
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.TaktEarning
import com.example.violintuner.core.domain.venue.FakeVenueStore
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.domain.venue.VenueRules
import com.example.violintuner.core.domain.venue.Venues
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC)
    private val journey = FakeJourneyRepository()
    private val venueStore = FakeVenueStore()
    private val venues = Venues(venueStore, journey)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun earn(takts: Int) = journey.earn(TaktEarning(clock.millis(), takts, takts, 0, takts))

    private fun TestScope.viewModel(): Pair<JourneyViewModel, MutableList<JourneyEffect>> {
        val viewModel = JourneyViewModel(journey, clock, venues)
        val effects = mutableListOf<JourneyEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun aJourneyNotStartedIsTheIntro_packingTheCaseStartsItAtHome() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()
        assertEquals(JourneyPhase.Intro, viewModel.state.value.phase)

        viewModel.onIntent(JourneyIntent.IntroConfirmed)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(JourneyPhase.Idle, state.phase)
        assertEquals(0, state.currentIndex)
        assertEquals("cremona", state.next?.id)
        assertEquals(300L, state.missing)
        assertFalse(state.canDepart)
    }

    @Test
    fun withoutEnoughTaktsTheRoadIsNotTaken() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(299)
        val (viewModel, _) = viewModel()

        viewModel.onIntent(JourneyIntent.DepartClicked)
        runCurrent()

        assertEquals(JourneyPhase.Idle, viewModel.state.value.phase)
        assertEquals(299L, viewModel.state.value.balance)
    }

    @Test
    fun theRoadIsPaidAtOnce_thenComesTheArrivalTheStampAndTheNewStop() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(340)
        val (viewModel, effects) = viewModel()
        assertTrue(viewModel.state.value.canDepart)

        viewModel.onIntent(JourneyIntent.DepartClicked)
        runCurrent()
        val road = viewModel.state.value.phase as JourneyPhase.Road
        assertEquals("home" to "cremona", road.from.id to road.to.id)
        assertEquals(JourneyMotion.ROAD_TRAIN_MS, road.durationMs)
        // a process that dies on the road loses nothing: the stop is already reached
        assertEquals(40L, journey.progress.value.balance)
        assertEquals(1, viewModel.state.value.currentIndex)

        // nothing else answers on the road
        viewModel.onIntent(JourneyIntent.BackClicked)
        viewModel.onIntent(JourneyIntent.MapClicked)
        viewModel.onIntent(JourneyIntent.DepartClicked)
        runCurrent()
        assertTrue(effects.isEmpty())

        advanceTimeBy(JourneyMotion.ROAD_TRAIN_MS + 1L)
        runCurrent()
        assertEquals(JourneyPhase.Arrival(JourneyRoute.stops[1], 1), viewModel.state.value.phase)

        viewModel.onIntent(JourneyIntent.StampClicked)
        runCurrent()
        assertEquals(JourneyPhase.Stamp(JourneyRoute.stops[1], 1), viewModel.state.value.phase)

        viewModel.onIntent(JourneyIntent.StampDone)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(JourneyPhase.Idle, state.phase)
        assertEquals("cremona", state.current.id)
        assertEquals("milan", state.next?.id)
        assertEquals(listOf("home", "cremona"), state.visited.map { it.stop.id })
    }

    @Test
    fun withAnimationsRemovedTheRoadIsShort() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(300)
        val (viewModel, _) = viewModel()

        viewModel.onIntent(JourneyIntent.ReduceMotionChanged(true))
        viewModel.onIntent(JourneyIntent.DepartClicked)
        runCurrent()

        assertEquals(JourneyMotion.ROAD_REDUCED_MS, (viewModel.state.value.phase as JourneyPhase.Road).durationMs)
    }

    @Test
    fun beyondTheLastDrawnStopThereIsNoNext_theTaktsWait() = runTest(dispatcher) {
        journey.start(clock.millis())
        val drawn = JourneyRoute.stops.filter { it.available }.drop(1)
        earn(drawn.sumOf { it.price } + 5_000)
        drawn.forEach { assertTrue(journey.depart(it, clock.millis())) }
        val (viewModel, _) = viewModel()

        val state = viewModel.state.value
        assertEquals(drawn.last().id, state.current.id)
        assertNull(state.next)
        assertFalse(state.canDepart)
        assertEquals(5_000L, state.balance)
    }

    @Test
    fun theDoorsOpenOnlyFromTheCalmScreen() = runTest(dispatcher) {
        journey.start(clock.millis())
        val (viewModel, effects) = viewModel()

        viewModel.onIntent(JourneyIntent.MapClicked)
        viewModel.onIntent(JourneyIntent.PassportClicked)
        viewModel.onIntent(JourneyIntent.StopClicked("home"))
        viewModel.onIntent(JourneyIntent.BackClicked)
        runCurrent()

        assertEquals(listOf(JourneyEffect.OpenMap, JourneyEffect.OpenPassport, JourneyEffect.OpenStop("home"), JourneyEffect.Close), effects)
    }

    @Test
    fun anExtraIsBoughtOnce_andWhatItOpensIsShownAtOnce() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(300 + 500 + 800 + 1200 + 700)
        JourneyRoute.stops.subList(1, 5).forEach { assertTrue(journey.depart(it, clock.millis())) }
        val viewModel = StopViewModel(SavedStateHandle(mapOf(StopViewModel.ARG_STOP_ID to "vienna")), journey, JourneyConfig(), clock, venues)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        val before = viewModel.state.value
        assertEquals(700L, before.balance)
        assertEquals(setOf(JourneyExtra.SECOND_TIME, JourneyExtra.SECOND_VIEW, JourneyExtra.SOUVENIR), before.offers.map { it.extra }.toSet())
        assertFalse(before.day)
        assertFalse(before.inside)

        viewModel.onIntent(StopIntent.BuyClicked(JourneyExtra.SECOND_VIEW))
        runCurrent()
        assertTrue(viewModel.state.value.inside)
        assertEquals(300L, viewModel.state.value.balance)

        viewModel.onIntent(StopIntent.BuyClicked(JourneyExtra.SECOND_VIEW))
        viewModel.onIntent(StopIntent.BuyClicked(JourneyExtra.SECOND_TIME))
        runCurrent()
        assertTrue(viewModel.state.value.day)
        assertEquals(100L, viewModel.state.value.balance)

        // 150 for the souvenir is more than what is left
        viewModel.onIntent(StopIntent.BuyClicked(JourneyExtra.SOUVENIR))
        runCurrent()
        assertEquals(100L, viewModel.state.value.balance)
        assertFalse(viewModel.state.value.offers.first { it.extra == JourneyExtra.SOUVENIR }.affordable)

        viewModel.onIntent(StopIntent.DaySelected(false))
        viewModel.onIntent(StopIntent.InsideSelected(false))
        runCurrent()
        assertFalse(viewModel.state.value.day)
        assertFalse(viewModel.state.value.inside)
    }

    @Test
    fun backFromTheWholeScreenFoldsThePostcard_backAgainLeavesTheStop() = runTest(dispatcher) {
        journey.start(clock.millis())
        val viewModel = StopViewModel(SavedStateHandle(mapOf(StopViewModel.ARG_STOP_ID to "home")), journey, JourneyConfig(), clock, venues)
        val closes = mutableListOf<StopEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { closes += it } }
        runCurrent()

        viewModel.onIntent(StopIntent.PostcardClicked)
        runCurrent()
        assertTrue(viewModel.state.value.fullscreen)

        viewModel.onIntent(StopIntent.BackClicked)
        runCurrent()
        assertFalse(viewModel.state.value.fullscreen)
        assertTrue(closes.isEmpty())

        viewModel.onIntent(StopIntent.BackClicked)
        runCurrent()
        assertEquals(1, closes.size)
    }

    // ---- where the player is (spec 3.27)

    @Test
    fun theDoorHomeTakesThePlayerHome_andAnArrivalPutsThemInTheNewCity() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(340)
        val (viewModel, effects) = viewModel()

        viewModel.onIntent(JourneyIntent.StopClicked(JourneyRoute.HOME))
        runCurrent()
        assertEquals(VenueRules.HOME, venueStore.stored.value)
        assertEquals(JourneyEffect.OpenStop(JourneyRoute.HOME), effects.last())

        viewModel.onIntent(JourneyIntent.DepartClicked)
        runCurrent()
        assertEquals(null, venueStore.stored.value)
        assertEquals(Venue.Hall("cremona"), venues.current.first())

        advanceTimeBy(JourneyMotion.ROAD_TRAIN_MS + 1L)
        runCurrent()
        viewModel.onIntent(JourneyIntent.StampClicked)
        runCurrent()
        // «Сыграть здесь» on the page of the stamp: Live, in the city just reached
        viewModel.onIntent(JourneyIntent.PlayHereClicked)
        runCurrent()
        assertEquals(JourneyEffect.OpenLive, effects.last())
        assertEquals(JourneyPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun playHereTakesThePlayerToTheCity_andTheRoundDoorTakesThemHome() = runTest(dispatcher) {
        journey.start(clock.millis())
        earn(300 + 500)
        JourneyRoute.stops.subList(1, 3).forEach { assertTrue(journey.depart(it, clock.millis())) }
        val viewModel = StopViewModel(SavedStateHandle(mapOf(StopViewModel.ARG_STOP_ID to "cremona")), journey, JourneyConfig(), clock, venues)
        val effects = mutableListOf<StopEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onIntent(StopIntent.PlayHereClicked)
        runCurrent()
        assertEquals(Venue.Hall("cremona"), venues.current.first())
        assertEquals(StopEffect.OpenLive, effects.last())

        viewModel.onIntent(StopIntent.HomeClicked)
        runCurrent()
        assertEquals(Venue.Home, venues.current.first())
        assertEquals(StopEffect.OpenHome, effects.last())
    }
}
