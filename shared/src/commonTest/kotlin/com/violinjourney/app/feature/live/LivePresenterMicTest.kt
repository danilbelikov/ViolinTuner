package com.violinjourney.app.feature.live

import com.violinjourney.app.core.audio.MicUnavailableException
import com.violinjourney.app.core.audio.MicUnavailableReason
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LivePresenterMicTest {
    private val config = IntonationConfig()

    /** A microphone: [script] says what each opening does, in turn; the last one repeats. */
    private class Mic(private val script: List<Opening>) : PitchSource {
        enum class Opening { FAIL, ZEROS, PLAYS }

        var openings = 0
        override val requiresMicPermission = true
        override val audioTap: AudioTap? = null

        override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
            val opening = script[minOf(openings, script.lastIndex)]
            openings++
            var t = 0L
            while (true) {
                when (opening) {
                    Opening.FAIL -> throw MicUnavailableException(MicUnavailableReason.OPEN_FAILED, "busy")
                    Opening.ZEROS -> emit(PitchFrame.unpitched(t, clarity = 0.0, rms = 0.0))
                    Opening.PLAYS -> emit(PitchFrame.pitched(t, 440.0, clarity = 0.97, rms = 0.2, a4Hz = config.a4Hz))
                }
                t += 10
                delay(10)
            }
        }
    }

    private fun TestScope.presenter(mic: Mic, active: MutableStateFlow<Boolean> = MutableStateFlow(true)): LivePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LivePresenter(mic, config, backgroundScope, dispatcher, active).also { p ->
            backgroundScope.launch(dispatcher) { p.state.collect {} }
        }
    }

    @Test
    fun `until the permission is known the screen says play — and the microphone stays closed`() = runTest {
        val mic = Mic(listOf(Mic.Opening.PLAYS))
        val presenter = presenter(mic)
        advanceTimeBy(500)
        assertEquals(LiveSignal.Silence, presenter.state.value.signal)
        assertEquals(StatusMessage.PLAY, presenter.state.value.statusLine?.message)
        assertEquals(0, mic.openings)
    }

    @Test
    fun `without the permission — the prompt, and its button asks the platform`() = runTest {
        val mic = Mic(listOf(Mic.Opening.PLAYS))
        val presenter = presenter(mic)
        presenter.onIntent(LiveIntent.MicPermissionChanged(false))
        advanceTimeBy(500)
        assertEquals(LiveSignal.NoMicPermission, presenter.state.value.signal)
        assertEquals(0, mic.openings)
        presenter.onIntent(LiveIntent.GrantMicClicked)
        assertEquals(LiveEffect.RequestMicPermission, presenter.effects.first())
    }

    @Test
    fun `with the permission it listens`() = runTest {
        val mic = Mic(listOf(Mic.Opening.PLAYS))
        val presenter = presenter(mic)
        presenter.onIntent(LiveIntent.MicPermissionChanged(true))
        advanceTimeBy(500)
        assertIs<LiveSignal.Sounding>(presenter.state.value.signal)
    }

    @Test
    fun `a failed microphone is tried again — and believed only once it gives more than zeros`() = runTest {
        val mic = Mic(listOf(Mic.Opening.FAIL, Mic.Opening.ZEROS, Mic.Opening.PLAYS))
        val presenter = presenter(mic)
        presenter.onIntent(LiveIntent.MicPermissionChanged(true))
        advanceTimeBy(100)
        assertEquals(LiveSignal.MicUnavailable, presenter.state.value.signal)
        assertEquals(StatusMessage.MIC_UNAVAILABLE, presenter.state.value.statusLine?.message)

        // reopened after the pause, but the input gives exact zeros: still unavailable, not «Играйте…»
        advanceTimeBy(config.micRetryDelayMs + 500)
        assertEquals(2, mic.openings)
        assertEquals(LiveSignal.MicUnavailable, presenter.state.value.signal)
    }

    @Test
    fun `after the failure a living input brings the notes back`() = runTest {
        val mic = Mic(listOf(Mic.Opening.FAIL, Mic.Opening.PLAYS))
        val presenter = presenter(mic)
        presenter.onIntent(LiveIntent.MicPermissionChanged(true))
        advanceTimeBy(config.micRetryDelayMs + 500)
        assertIs<LiveSignal.Sounding>(presenter.state.value.signal)
    }

    @Test
    fun `in the background the microphone is closed — and opened again on return`() = runTest {
        val mic = Mic(listOf(Mic.Opening.PLAYS))
        val active = MutableStateFlow(true)
        val presenter = presenter(mic, active)
        presenter.onIntent(LiveIntent.MicPermissionChanged(true))
        advanceTimeBy(500)
        assertEquals(1, mic.openings)

        active.value = false
        advanceTimeBy(500)
        assertEquals(LiveSignal.Silence, presenter.state.value.signal)
        val openedBefore = mic.openings
        advanceTimeBy(5_000)
        assertEquals(openedBefore, mic.openings)

        active.value = true
        advanceTimeBy(500)
        assertEquals(2, mic.openings)
        assertIs<LiveSignal.Sounding>(presenter.state.value.signal)
    }
}
