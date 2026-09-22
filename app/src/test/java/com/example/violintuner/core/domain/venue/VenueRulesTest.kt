package com.example.violintuner.core.domain.venue

import com.example.violintuner.core.domain.journey.Arrival
import com.example.violintuner.core.domain.journey.JourneyProgress
import com.example.violintuner.core.domain.journey.JourneyRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenueRulesTest {
    private fun reachedUpTo(vararg ids: String) = JourneyProgress.EMPTY.copy(arrivals = ids.map { Arrival(it, 0L) })

    private val notStarted = JourneyProgress.EMPTY
    private val inVienna = reachedUpTo(JourneyRoute.HOME, "cremona", "milan", "salzburg", "vienna")

    @Test
    fun `nothing stored follows the road — home before the trip, the farthest city after`() {
        assertEquals(Venue.Home, VenueRules.resolve(null, notStarted))
        assertEquals(Venue.Home, VenueRules.resolve(null, reachedUpTo(JourneyRoute.HOME)))
        assertEquals(Venue.Hall("vienna"), VenueRules.resolve(null, inVienna))
    }

    @Test
    fun `home and a reached city are kept as chosen`() {
        assertEquals(Venue.Home, VenueRules.resolve(VenueRules.HOME, inVienna))
        assertEquals(Venue.Hall("milan"), VenueRules.resolve("milan", inVienna))
    }

    @Test
    fun `a city that is not reached — a copy from before the trip — gives the room`() {
        assertEquals(Venue.Home, VenueRules.resolve("paris", inVienna))
        assertEquals(Venue.Home, VenueRules.resolve("nowhere", inVienna))
    }

    @Test
    fun `the place where the road stands is stored as following the road, the others by name`() {
        assertNull(VenueRules.storedFor(Venue.Hall("vienna"), inVienna))
        assertEquals("milan", VenueRules.storedFor(Venue.Hall("milan"), inVienna))
        assertEquals(VenueRules.HOME, VenueRules.storedFor(Venue.Home, inVienna))
        // before the trip home is where the road stands
        assertNull(VenueRules.storedFor(Venue.Home, notStarted))
    }

    @Test
    fun `reached places are the room and the arrivals in the order of the route`() {
        val shuffled = reachedUpTo("milan", JourneyRoute.HOME, "cremona")
        assertEquals(listOf(Venue.Home, Venue.Hall("cremona"), Venue.Hall("milan")), VenueRules.reached(shuffled))
    }

    @Test
    fun `the menu opens what is reached, marks the next stop and keeps the rest ahead`() {
        val menu = VenueRules.menu(inVienna)
        assertEquals(JourneyRoute.stops.size, menu.size)
        assertEquals(List(5) { VenueAccess.OPEN }, menu.take(5).map { it.access })
        assertEquals(VenueEntry(Venue.Hall("prague"), VenueAccess.NEXT), menu[5])
        assertEquals(List(JourneyRoute.stops.size - 6) { VenueAccess.AHEAD }, menu.drop(6).map { it.access })
        assertEquals(JourneyRoute.stops.map { it.id }, menu.map { if (it.venue is Venue.Hall) it.venue.stopId else JourneyRoute.HOME })
    }

    @Test
    fun `before the trip the room is open and the first city is next`() {
        val menu = VenueRules.menu(notStarted)
        assertEquals(VenueEntry(Venue.Home, VenueAccess.OPEN), menu.first())
        assertEquals(VenueEntry(Venue.Hall("cremona"), VenueAccess.NEXT), menu[1])
    }

    @Test
    fun `a hall knows its stop on the route`() {
        assertEquals(0, Venue.Home.stopIndex)
        assertEquals(JourneyRoute.indexOf("vienna"), Venue.Hall("vienna").stopIndex)
    }
}
