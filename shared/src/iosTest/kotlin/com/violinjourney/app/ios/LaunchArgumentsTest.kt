package com.violinjourney.app.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The word after a name among the arguments of a launch (`-openRoute live -sceneSeconds 12.5`). */
class LaunchArgumentsTest {
    private val launch = listOf("/path/ViolinJourney", "-openRoute", "live", "-sceneSeconds", "12.5", "-openRoute", "history", "-noBake")

    @Test
    fun `the word after the name is its value`() {
        assertEquals("12.5", LaunchArguments.valueAfter(launch, "-sceneSeconds"))
    }

    @Test
    fun `the first of two wins`() {
        assertEquals("live", LaunchArguments.valueAfter(launch, "-openRoute"))
    }

    @Test
    fun `a name not given or given last has no value`() {
        assertNull(LaunchArguments.valueAfter(launch, "-fakeScenario"))
        assertNull(LaunchArguments.valueAfter(launch, "-noBake"))
    }
}
