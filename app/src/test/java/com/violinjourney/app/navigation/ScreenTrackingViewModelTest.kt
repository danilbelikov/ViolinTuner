package com.violinjourney.app.navigation

import com.violinjourney.app.core.analytics.FakeAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTrackingViewModelTest {
    private val analytics = FakeAnalytics()
    private val viewModel = ScreenTrackingViewModel(analytics)

    @Test
    fun `a screen is reported by name alone`() {
        viewModel.onScreenOpened("live")
        assertEquals(listOf("screen_open {screen=live}"), analytics.sent())
    }

    @Test
    fun `what the route carries stays on the phone`() {
        viewModel.onScreenOpened("piece/17")
        viewModel.onScreenOpened("session?take=3")
        assertEquals(listOf("screen_open {screen=piece}", "screen_open {screen=session}"), analytics.sent())
    }

    @Test
    fun `a destination without a route is not an event`() {
        viewModel.onScreenOpened(null)
        assertEquals(emptyList<String>(), analytics.sent())
    }
}
