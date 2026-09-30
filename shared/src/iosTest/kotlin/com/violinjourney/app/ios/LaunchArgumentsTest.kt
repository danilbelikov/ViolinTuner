package com.violinjourney.app.ios

import com.violinjourney.app.feature.live.LiveMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The word after a name among the arguments of a launch (`-openRoute live -sceneSeconds 12.5`), and the mode Live opens in. */
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

    @Test
    fun `the owners app opens Live in the mode asked for whatever its case`() {
        assertEquals(LiveMode.TUNING, LaunchArguments.liveModeOf(listOf("app", "-liveMode", "tuning"), devApp = true))
        assertEquals(LiveMode.TUNING, LaunchArguments.liveModeOf(listOf("app", "-liveMode", "TUNING"), devApp = true))
        assertEquals(LiveMode.PLAY, LaunchArguments.liveModeOf(listOf("app", "-openRoute", "live", "-liveMode", "Play"), devApp = true))
    }

    @Test
    fun `the store app does not listen to the mode`() {
        assertNull(LaunchArguments.liveModeOf(listOf("app", "-liveMode", "tuning"), devApp = false))
    }

    @Test
    fun `a word that names no mode or no word at all leaves Live as ever`() {
        assertNull(LaunchArguments.liveModeOf(listOf("app", "-liveMode", "tune"), devApp = true))
        assertNull(LaunchArguments.liveModeOf(listOf("app", "-liveMode"), devApp = true))
        assertNull(LaunchArguments.liveModeOf(launch, devApp = true))
    }
}
