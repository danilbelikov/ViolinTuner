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

    /**
     * The big button of the step in view, tapped [times] in a row before the screen shows the next step — as two taps that both
     * reach the button before the next frame: every tap carries the step the button showed (5.29 R8).
     */
    private fun TestScope.press(viewModel: OnboardingViewModel, times: Int = 1) {
        val shown = viewModel.state.value.step
        repeat(times) { viewModel.onIntent(OnboardingIntent.PrimaryClicked(shown)) }
        runCurrent()
    }

    /** Every effect the view model sends from now on, as it sends them. */
    private fun TestScope.effectsOf(viewModel: OnboardingViewModel): List<OnboardingEffect> {
        val effects = mutableListOf<OnboardingEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return effects
    }

    /** Through the rest of the introduction by its buttons. */
    private fun TestScope.toMicrophone(viewModel: OnboardingViewModel) {
        repeat(OnboardingStep.intro.size) {
            if (viewModel.state.value.step.part == OnboardingPart.INTRO) press(viewModel)
        }
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)
    }

    /** On to «Эталон»: the system has answered the question of «Микрофон». */
    private fun TestScope.toReference(viewModel: OnboardingViewModel) {
        toMicrophone(viewModel)
        send(viewModel, OnboardingIntent.MicPermissionAnswered)
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
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
        press(viewModel)
        assertEquals(OnboardingEffect.RequestMicPermission, viewModel.effects.first())
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)

        send(viewModel, OnboardingIntent.MicPermissionAnswered)
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
    }

    @Test
    fun `a late permission answer does not skip a step`() = runTest {
        val viewModel = viewModel()
        toReference(viewModel)
        press(viewModel)
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

        press(viewModel)
        send(viewModel, OnboardingIntent.ToleranceSelected(TolerancePreset.BEGINNER))
        assertEquals(TolerancePreset.BEGINNER, repository.settings.value.tolerance)
        assertFalse(repository.settings.value.onboardingDone)
    }

    @Test
    fun `last button sets the flag and finishes`() = runTest {
        val viewModel = viewModel()
        toReference(viewModel)
        press(viewModel)
        press(viewModel)
        assertTrue(repository.settings.value.onboardingDone)
        assertEquals(OnboardingEffect.Finished, viewModel.effects.first())
    }

    @Test
    fun `back goes one screen back, from the setup into the introduction, and stops at the first`() = runTest {
        val viewModel = viewModel()
        toReference(viewModel)
        press(viewModel)
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
        press(viewModel)
        send(viewModel, OnboardingIntent.SkipClicked(OnboardingStep.LIVE))
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
        send(viewModel, OnboardingIntent.SkipClicked(OnboardingStep.DATA))
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

    // ---- a press is bound to the step its button showed (5.29 R8; two taps before the next frame, found on the emulator)

    @Test
    fun `two presses from the page about the data land on the microphone and ask nothing`() = runTest {
        val viewModel = viewModel()
        val effects = effectsOf(viewModel)
        send(viewModel, OnboardingIntent.PageShown(OnboardingStep.DATA.indexInPart))
        press(viewModel, times = 2)
        assertEquals(OnboardingStep.MICROPHONE, viewModel.state.value.step)
        assertEquals(emptyList<OnboardingEffect>(), effects)
    }

    @Test
    fun `two presses from the reference land on the tolerance and do not finish`() = runTest {
        val viewModel = viewModel()
        val effects = effectsOf(viewModel)
        toReference(viewModel)
        press(viewModel, times = 2)
        assertEquals(OnboardingStep.TOLERANCE, viewModel.state.value.step)
        assertFalse(repository.settings.value.onboardingDone)
        assertEquals(emptyList<OnboardingEffect>(), effects)
    }

    @Test
    fun `two presses from the tolerance finish once`() = runTest {
        val viewModel = viewModel()
        val effects = effectsOf(viewModel)
        toReference(viewModel)
        press(viewModel)
        press(viewModel, times = 2)
        assertTrue(repository.settings.value.onboardingDone)
        assertEquals(listOf<OnboardingEffect>(OnboardingEffect.Finished), effects)
    }

    @Test
    fun `a stale press from an earlier step never moves back and never asks`() = runTest {
        val viewModel = viewModel()
        val effects = effectsOf(viewModel)
        toReference(viewModel)
        val stale = OnboardingStep.entries.filter { it < OnboardingStep.REFERENCE_PITCH }.map { OnboardingIntent.PrimaryClicked(it) }
        send(viewModel, *stale.toTypedArray(), OnboardingIntent.SkipClicked(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
        assertEquals("«Разрешить микрофон» reaching the model after the answer asks nothing", emptyList<OnboardingEffect>(), effects)
    }

    @Test
    fun `a press of the last button that reaches the model after back does not finish`() = runTest {
        val viewModel = viewModel()
        val effects = effectsOf(viewModel)
        toReference(viewModel)
        press(viewModel)
        send(viewModel, OnboardingIntent.BackPressed, OnboardingIntent.PrimaryClicked(OnboardingStep.TOLERANCE))
        assertEquals(OnboardingStep.REFERENCE_PITCH, viewModel.state.value.step)
        assertFalse(repository.settings.value.onboardingDone)
        assertEquals(emptyList<OnboardingEffect>(), effects)
    }

    @Test
    fun `skip twice from the first page lands on the page about the data and not past it`() = runTest {
        val viewModel = viewModel()
        send(viewModel, OnboardingIntent.SkipClicked(OnboardingStep.WELCOME), OnboardingIntent.SkipClicked(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
        // «Начать» of the first page, still there under the dissolve of «Пропустить»: the page about the data is not left unread
        send(viewModel, OnboardingIntent.PrimaryClicked(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
    }

    @Test
    fun `a press on the third page before the pager has told the model goes on to the fourth`() = runTest {
        val viewModel = viewModel()
        press(viewModel)
        assertEquals(OnboardingStep.LIVE, viewModel.state.value.step)
        // swiped on to the third page, whose «Дальше» is pressed before the pager tells the model it stands there
        send(viewModel, OnboardingIntent.PrimaryClicked(OnboardingStep.JOURNEY))
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
        send(viewModel, OnboardingIntent.PageShown(OnboardingStep.DATA.indexInPart))
        assertEquals(OnboardingStep.DATA, viewModel.state.value.step)
    }

    @Test
    fun `step survives process death through the saved state`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        toReference(first)
        press(first)
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
