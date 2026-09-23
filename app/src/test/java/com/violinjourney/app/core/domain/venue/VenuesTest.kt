package com.violinjourney.app.core.domain.venue

import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenuesTest {
    private val store = FakeVenueStore()
    private val journey = FakeJourneyRepository().apply {
        progress.value = JourneyProgress.EMPTY.copy(earned = 5_000, arrivals = listOf(Arrival(JourneyRoute.HOME, 0), Arrival("cremona", 1), Arrival("milan", 2)))
    }
    private val venues = Venues(store, journey)

    @Test
    fun `a traveller who has never moved is where the road stands`() = runTest {
        assertEquals(Venue.Hall("milan"), venues.current.first())
    }

    @Test
    fun `going home and back on the road`() = runTest {
        venues.goHome()
        assertEquals(Venue.Home, venues.current.first())
        venues.followRoad()
        assertNull(store.stored.value)
        assertEquals(Venue.Hall("milan"), venues.current.first())
    }

    @Test
    fun `a chosen city stays chosen until the player moves on, and the road takes over after the next leg`() = runTest {
        venues.choose(Venue.Hall("cremona"))
        assertEquals(Venue.Hall("cremona"), venues.current.first())
        // the arrival puts the player on the road again
        journey.depart(JourneyRoute.stops[3], 3)
        venues.followRoad()
        assertEquals(Venue.Hall("salzburg"), venues.current.first())
    }

    @Test
    fun `choosing where the road stands follows the road`() = runTest {
        venues.goHome()
        venues.choose(Venue.Hall("milan"))
        assertNull(store.stored.value)
    }

    @Test
    fun `a city that is not reached is no choice`() = runTest {
        venues.goHome()
        venues.choose(Venue.Hall("vienna"))
        assertEquals(Venue.Home, venues.current.first())
    }
}
