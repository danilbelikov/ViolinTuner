package com.violinjourney.app.navigation

import com.violinjourney.app.core.analytics.screenKeyOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopLevelDestinationTest {

    @Test
    fun `the app opens on Practice - the first tab`() {
        assertEquals(TopLevelDestination.PRACTICE, TopLevelDestination.START)
    }

    /** Section 4 as rewritten by 3.36.1 (spec 0.79, the owner confirmed four tabs on 28.09.2026): begun, playing, what is played, listening. */
    @Test
    fun `bottom bar order is Practice - Live - Repertoire - History and Settings are not a tab`() {
        assertEquals(
            listOf(
                TopLevelDestination.PRACTICE,
                TopLevelDestination.LIVE,
                TopLevelDestination.REPERTOIRE,
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

    /**
     * The keys of `screen_open` the tabs give (spec 5.27, 5.29), through the same cut the app makes: a new name here is a new
     * screen in the statistics, on both platforms — «Записи» keeps `history`, the repertoire has its own `repertoire`.
     */
    @Test
    fun `the tabs open in the statistics as practice - live - repertoire - history`() {
        assertEquals(
            listOf("practice", "live", "repertoire", "history"),
            TopLevelDestination.entries.map { screenKeyOf(it.route) },
        )
    }

    /** A tab's route is its key in `screen_open` (spec 5.27, 5.29): sharing a name with a screen above the tabs would merge the two. */
    @Test
    fun `no tab takes the name of a screen above the tabs`() {
        val above = setOf(
            ONBOARDING_ROUTE, Routes.SESSION, Routes.SOUND, Routes.PIECE, Routes.CAPTURE, Routes.STAND, Routes.PIECE_FORM,
            Routes.SCALE_FORM, Routes.SECTION, Routes.JOURNEY, Routes.JOURNEY_MAP, Routes.JOURNEY_PASSPORT, Routes.JOURNEY_STOP,
            Routes.HOME, Routes.HOME_SHOP, Routes.HOME_ARRANGE, Routes.HOME_HOUSES, Routes.SPLASH_AWAY, Routes.SPLASH_HOME,
            Routes.SETTINGS, Routes.BACKUP, Routes.RESTORE,
        )
        TopLevelDestination.entries.forEach { tab ->
            assertTrue(tab.route !in above, "the tab ${tab.name} is named as a screen above the tabs: ${tab.route}")
        }
    }
}
