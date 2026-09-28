package com.violinjourney.app.feature.journey

import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The window of the home as the reducer makes it from the stored journey (spec 3.23, 3.36.2). */
class JourneyReducerTest {
    private val home = Arrival(JourneyRoute.HOME, 1_000)

    @Test
    fun `a journey that never earned a takt is the first run`() {
        val window = JourneyReducer.windowOf(JourneyProgress.EMPTY)
        assertTrue(window.neverEarned)
        assertEquals(0L, window.balance)
        assertFalse(window.enough)
    }

    @Test
    fun `the case packed without a takt is still the first run`() {
        assertTrue(JourneyReducer.windowOf(JourneyProgress(earned = 0, spent = 0, arrivals = listOf(home), extras = emptySet())).neverEarned)
    }

    @Test
    fun `takts earned and all spent are not the first run though the purse is empty`() {
        val cremona = Arrival(JourneyRoute.stops[1].id, 2_000)
        val window = JourneyReducer.windowOf(JourneyProgress(earned = 300, spent = 300, arrivals = listOf(home, cremona), extras = emptySet()))
        assertFalse(window.neverEarned)
        assertEquals(0L, window.balance)
        assertEquals(JourneyRoute.stops[1], window.current)
    }

    @Test
    fun `enough is the next city paid for and never the end of the road`() {
        val price = JourneyRoute.stops[1].price.toLong()
        val short = JourneyReducer.windowOf(JourneyProgress(earned = price - 1, spent = 0, arrivals = listOf(home), extras = emptySet()))
        assertFalse(short.enough)
        assertEquals(1L, short.missing)
        val paid = JourneyReducer.windowOf(JourneyProgress(earned = price, spent = 0, arrivals = listOf(home), extras = emptySet()))
        assertTrue(paid.enough)
        assertTrue(paid.canDepart)
        // the takts are there before the case is packed: enough for the words of the window, though the road waits for the intro
        val unpacked = JourneyReducer.windowOf(JourneyProgress(earned = price, spent = 0, arrivals = emptyList(), extras = emptySet()))
        assertTrue(unpacked.enough)
        assertFalse(unpacked.canDepart)
        val last = JourneyRoute.stops.lastIndex
        val end = JourneyReducer.windowOf(
            JourneyProgress(earned = 1_000_000, spent = 0, arrivals = listOf(home, Arrival(JourneyRoute.stops[last].id, 3_000)), extras = emptySet()),
        )
        assertNull(end.next)
        assertFalse(end.enough)
    }
}
