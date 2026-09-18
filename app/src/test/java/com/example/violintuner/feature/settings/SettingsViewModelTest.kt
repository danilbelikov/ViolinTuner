package com.example.violintuner.feature.settings

import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.domain.UserSettings
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.settings.FakeSettingsRepository
import com.example.violintuner.navigation.AppStartViewModel
import com.example.violintuner.navigation.ONBOARDING_ROUTE
import com.example.violintuner.navigation.TopLevelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = FakeSettingsRepository(UserSettings(442, TolerancePreset.BEGINNER, onboardingDone = true))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows and edits the stored settings`() = runTest {
        val viewModel = SettingsViewModel(repository)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER), viewModel.state.value)

        viewModel.onIntent(SettingsIntent.A4Selected(440))
        viewModel.onIntent(SettingsIntent.ToleranceSelected(TolerancePreset.PRO))
        runCurrent()
        assertEquals(UserSettings(440, TolerancePreset.PRO, onboardingDone = true), repository.settings.value)
        assertEquals(440, viewModel.state.value.a4Hz)
    }

    @Test
    fun `restarting the onboarding clears the flag and opens it`() = runTest {
        val viewModel = SettingsViewModel(repository)
        viewModel.onIntent(SettingsIntent.RestartOnboardingClicked)
        runCurrent()
        assertFalse(repository.settings.value.onboardingDone)
        assertEquals(SettingsEffect.OpenOnboarding, viewModel.effects.first())
    }

    @Test
    fun `app starts on the onboarding until it is done, and does not follow later changes`() = runTest {
        val fresh = FakeSettingsRepository()
        val sessions = FakeSessionRepository()
        val start = AppStartViewModel(fresh, sessions, FakeRunningPracticeStore())
        assertNull(start.startRoute.value)
        runCurrent()
        assertEquals(ONBOARDING_ROUTE, start.startRoute.value)
        fresh.setOnboardingDone(true)
        runCurrent()
        assertEquals(ONBOARDING_ROUTE, start.startRoute.value)

        val returning = AppStartViewModel(repository, sessions, FakeRunningPracticeStore())
        runCurrent()
        assertEquals(TopLevelDestination.LIVE.route, returning.startRoute.value)
        assertEquals("orphaned audio is cleaned up on every start", 2, sessions.orphanCleanups)
    }
}
