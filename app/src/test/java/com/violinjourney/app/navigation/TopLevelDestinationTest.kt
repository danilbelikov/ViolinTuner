package com.violinjourney.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `the app opens on Practice - the first tab`() {
        assertEquals(TopLevelDestination.PRACTICE, TopLevelDestination.START)
    }

    @Test
    fun `bottom bar order is Practice, Live, History - Settings are not a tab`() {
        assertEquals(
            listOf(
                TopLevelDestination.PRACTICE,
                TopLevelDestination.LIVE,
                TopLevelDestination.HISTORY,
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
