package com.violinjourney.app.feature.live

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class LivePresenterTest {
    private val config = IntonationConfig()

    @Test
    fun `a steady in-tune note reads as A4 in tune`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = LivePresenter(FakePitchSource(FakeScenario.IN_TUNE, config, testTimeSource), config, backgroundScope, dispatcher)
        backgroundScope.launch(dispatcher) { presenter.state.collect {} }
        advanceTimeBy(1_000)
        val sounding = assertIs<LiveSignal.Sounding>(presenter.state.value.signal)
        assertEquals("A4", sounding.note.name)
        assertEquals(Zone.IN_TUNE, sounding.zone)
        assertNull(presenter.state.value.statusLine) // hidden while a note sounds
    }

    @Test
    fun `silence says play — and the tuning mode says it with the string row`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = LivePresenter(FakePitchSource(FakeScenario.SILENCE, config, testTimeSource), config, backgroundScope, dispatcher)
        backgroundScope.launch(dispatcher) { presenter.state.collect {} }
        advanceTimeBy(1_000)
        assertEquals(StatusMessage.PLAY, presenter.state.value.statusLine?.message)

        presenter.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        presenter.onIntent(LiveIntent.StringClicked(ViolinString.D4))
        advanceTimeBy(100)
        val state = presenter.state.first { it.mode == LiveMode.TUNING }
        assertEquals(ViolinString.D4, state.tuning.lockedString)
        assertEquals(StatusMessage.TUNE_LOCKED, state.statusLine?.message)
    }
}
