package com.violinjourney.app.ios

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.IosMicPitchSource
import com.violinjourney.app.core.domain.IntonationConfig
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

/**
 * Each screen that records hears through a microphone of its own, as on Android: a Live still listening after the
 * screen went away must not write into the take of the next screen or move its clock.
 */
class PitchSourcesTest {
    @Test
    fun `every screen gets its own microphone with its own sound of the take`() {
        val next = pitchSources(fakeScenario = null, config = IntonationConfig(), logStats = false, analytics = NoOpAnalytics())
        val first = next()
        val second = next()
        assertIs<IosMicPitchSource>(first)
        assertNotSame(first, second)
        assertNotSame(assertNotNull(first.audioTap), assertNotNull(second.audioTap))
        assertNotSame(assertNotNull(first.clock), assertNotNull(second.clock))
    }

    @Test
    fun `the fake scenario is one per screen too`() {
        val next = pitchSources(FakeScenario.IN_TUNE, IntonationConfig(), logStats = false, analytics = NoOpAnalytics())
        assertNotSame(next(), next())
    }
}
