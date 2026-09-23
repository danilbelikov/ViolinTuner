package com.violinjourney.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val repository = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): OnboardingViewModel {
        val viewModel = OnboardingViewModel(repository, savedState)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(viewModel: OnboardingViewModel, vararg intents: OnboardingIntent) {
        intents.forEach(viewModel::onIntent)
        runCurrent()
    }

    /** Through the rest of the introduction by its buttons. */
    private fun TestScope.toMicrophone(viewModel: OnboardingViewModel) {
        repeat(OnboardingStep.intro.size) {
            if (viewModel.state.value.step.part == OnboardingPart.INTRO) send(viewModel, OnboardingIntent.PrimaryClicked)
        }
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)
    }

    @Test
    fun `starts with the welcome page and the stored defaults`() = runTest {
        val state = viewModel().state.value
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertEquals(440, state.a4Hz)
        assertEquals(listOf(440, 441, 442, 443), state.a4OptionsHz)
        assertEquals(TolerancePreset.INTERMEDIATE, state.tolerance)
    }

    @Test
    fun `microphone button asks for the permission and any answer moves on`() = runTest {
        val viewModel = viewModel()
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.PrimaryClicked)
        assertEquals(OnboardingEffect.RequestMicPermission, viewModel.effects.first())
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)

        send(viewModel, OnboardingIntent.MicPermissionAnswered)
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
    }

    @Test
    fun `a late permission answer does not skip a step`() = runTest {
        val viewModel = viewModel()
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.MicPermissionAnswered, OnboardingIntent.PrimaryClicked)
        assertEquals(OnboardingStep.TOLERANCE, viewModel.state.value.step)
        send(viewModel, OnboardingIntent.MicPermissionAnswered)
        assertEquals(OnboardingStep.TOLERANCE, viewModel.state.value.step)
    }

    @Test
    fun `choices are stored at once`() = runTest {
        val viewModel = viewModel()
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.MicPermissionAnswered, OnboardingIntent.A4Selected(442))
        assertEquals(442, repository.settings.value.a4Hz)
        assertEquals(442, viewModel.state.value.a4Hz)

        send(viewModel, OnboardingIntent.PrimaryClicked, OnboardingIntent.ToleranceSelected(TolerancePreset.BEGINNER))
        assertEquals(TolerancePreset.BEGINNER, repository.settings.value.tolerance)
        assertFalse(repository.settings.value.onboardingDone)
    }

    @Test
    fun `last button sets the flag and finishes`() = runTest {
        val viewModel = viewModel()
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.MicPermissionAnswered, OnboardingIntent.PrimaryClicked, OnboardingIntent.PrimaryClicked)
        assertTrue(repository.settings.value.onboardingDone)
        assertEquals(OnboardingEffect.Finished, viewModel.effects.first())
    }

    @Test
    fun `back goes one screen back, from the setup into the introduction, and stops at the first`() = runTest {
        val viewModel = viewModel()
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.MicPermissionAnswered, OnboardingIntent.PrimaryClicked)
        send(viewModel, OnboardingIntent.BackPressed)
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
        send(viewModel, OnboardingIntent.BackPressed, OnboardingIntent.BackPressed)
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
        repeat(OnboardingStep.entries.size) { send(viewModel, OnboardingIntent.BackPressed) }
        assertEquals(OnboardingStep.WELCOME, viewModel.state.value.step)
    }

    @Test
    fun `skip leads to the page about the data and no further`() = runTest {
        val viewModel = viewModel()
        send(viewModel, OnboardingIntent.PrimaryClicked, OnboardingIntent.SkipClicked)
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
        send(viewModel, OnboardingIntent.SkipClicked)
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
    }

    @Test
    fun `a swipe moves within the introduction only`() = runTest {
        val viewModel = viewModel()
        send(viewModel, OnboardingIntent.PageShown(2))
        assertEquals(OnboardingStep.JOURNEY, viewModel.state.value.step)
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.PageShown(3))
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)
    }

    @Test
    fun `step survives process death through the saved state`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        toMicrophone(first)
        send(first, OnboardingIntent.MicPermissionAnswered, OnboardingIntent.PrimaryClicked)
        assertEquals(OnboardingStep.TOLERANCE, viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })).state.value.step)
    }

    @Test
    fun `seeing the onboarding again starts from the stored choices`() = runTest {
        repository.settings.value = UserSettings(443, TolerancePreset.PRO, onboardingDone = false)
        val state = viewModel().state.value
        assertEquals(443, state.a4Hz)
        assertEquals(TolerancePreset.PRO, state.tolerance)
    }
}
