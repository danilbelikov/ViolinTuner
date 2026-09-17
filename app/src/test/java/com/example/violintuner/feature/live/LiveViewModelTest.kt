package com.example.violintuner.feature.live

import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import com.example.violintuner.core.domain.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(source: PitchSource) =
        LiveViewModel(source, IntonationConfig(), StandardTestDispatcher(testScheduler))

    private fun TestScope.viewModel(scenario: FakeScenario) = viewModel(FakePitchSource(scenario))

    /** Subscribes like the screen does and lets [millis] of signal through. */
    private fun TestScope.observe(viewModel: LiveViewModel, millis: Long): Job {
        val job = backgroundScope.launch { viewModel.state.collect {} }
        advanceTimeBy(millis)
        runCurrent()
        return job
    }

    @Test
    fun `starts silent in play mode with the scale from config`() = runTest {
        val state = viewModel(FakeScenario.IN_TUNE).state.value
        assertEquals(LiveMode.PLAY, state.mode)
        assertEquals(LiveSignal.Silence, state.signal)
        assertEquals(ScaleSpec(rangeCents = 50.0, toleranceCents = 8.0), state.scale)
    }

    @Test
    fun `in tune scenario shows the note with a filling ring`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 1_200)
        val signal = viewModel.state.value.signal as LiveSignal.Sounding
        assertEquals("A4", signal.note.name)
        assertEquals(Zone.IN_TUNE, signal.zone)
        assertTrue(signal.holdProgress in 0.4..0.6)
    }

    @Test
    fun `silence and noise scenarios map to their states`() = runTest {
        val silent = viewModel(FakeScenario.SILENCE)
        observe(silent, 1_500)
        assertEquals(LiveSignal.Silence, silent.state.value.signal)

        val noisy = viewModel(FakeScenario.NOISE)
        observe(noisy, 1_500)
        assertEquals(LiveSignal.TooNoisy, noisy.state.value.signal)
    }

    @Test
    fun `selecting tuning mode switches the mode and retargets to open strings`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        runCurrent()
        assertEquals(LiveMode.TUNING, viewModel.state.value.mode)

        advanceTimeBy(50) // the engine restarted, the string has to lock again
        runCurrent()
        assertEquals(LiveSignal.Silence, viewModel.state.value.signal)
        advanceTimeBy(300)
        runCurrent()
        assertEquals("A4", (viewModel.state.value.signal as LiveSignal.Sounding).note.name)
    }

    @Test
    fun `record button only explains that recording comes later`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        viewModel.onIntent(LiveIntent.RecordClicked)
        assertEquals(LiveEffect.ShowRecordingUnavailable, viewModel.effects.first())
    }

    @Test
    fun `grant button asks the route to request the permission`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        viewModel.onIntent(LiveIntent.GrantMicClicked)
        assertEquals(LiveEffect.RequestMicPermission, viewModel.effects.first())
    }

    @Test
    fun `pitch source runs only while the state is observed`() = runTest {
        val source = CountingSource(FakePitchSource(FakeScenario.IN_TUNE))
        val viewModel = viewModel(source)
        advanceTimeBy(1_000)
        assertEquals(0, source.starts)

        val observer = observe(viewModel, 500)
        assertEquals(1, source.starts)
        assertEquals(0, source.stops)

        observer.cancel()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, source.stops)
    }

    private class CountingSource(delegate: PitchSource) : PitchSource {
        var starts = 0
        var stops = 0
        override val frames: Flow<PitchFrame> = delegate.frames
            .onStart { starts++ }
            .onCompletion { stops++ }
    }
}
