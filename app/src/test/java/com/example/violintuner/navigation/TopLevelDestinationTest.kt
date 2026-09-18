package com.example.violintuner.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `start destination is Live`() {
        assertEquals(TopLevelDestination.LIVE, TopLevelDestination.START)
    }

    @Test
    fun `bottom bar order is Live, Practice, History, Settings`() {
        assertEquals(
            listOf(
                TopLevelDestination.LIVE,
                TopLevelDestination.PRACTICE,
                TopLevelDestination.HISTORY,
                TopLevelDestination.SETTINGS,
            ),
            TopLevelDestination.entries.toList(),
        )
    }

    @Test
    fun `routes are unique`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
