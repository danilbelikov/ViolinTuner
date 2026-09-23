package com.violinjourney.app.core.ui.analytics

import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.ui.permission.MicPermissionAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsViewModelTest {
    private val analytics = FakeAnalytics()
    private val viewModel = AnalyticsViewModel(analytics)

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

    @Test
    fun `the answer about the microphone is told apart from a refusal for good`() {
        viewModel.onMicPermissionAnswered(MicPermissionAnswer.GRANTED)
        viewModel.onMicPermissionAnswered(MicPermissionAnswer.BLOCKED)
        assertEquals(
            listOf("mic_permission {result=granted}", "mic_permission {result=blocked}"),
            analytics.sent(),
        )
    }
}
