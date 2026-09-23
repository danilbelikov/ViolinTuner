package com.violinjourney.app.feature.settings

import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.share.FakeShareFiles
import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.progress.FakeProfileRepository
import com.violinjourney.app.core.domain.progress.FakeTrophyRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.FakeSoundRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.navigation.AppStartViewModel
import com.violinjourney.app.navigation.ONBOARDING_ROUTE
import com.violinjourney.app.navigation.TopLevelDestination
import java.time.Clock
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
import org.junit.Assert.assertTrue
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
        val viewModel = SettingsViewModel(repository, FakeSoundRepository(), SoundConfig())
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(
            SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER, SoundCaption.BuiltIn(BuiltInPreset.OFF), analyticsEnabled = true),
            viewModel.state.value,
        )

        viewModel.onIntent(SettingsIntent.A4Selected(440))
        viewModel.onIntent(SettingsIntent.ToleranceSelected(TolerancePreset.PRO))
        runCurrent()
        assertEquals(UserSettings(440, TolerancePreset.PRO, onboardingDone = true), repository.settings.value)
        assertEquals(440, viewModel.state.value.a4Hz)
    }

    @Test
    fun `the statistics switch is stored at once`() = runTest {
        val viewModel = SettingsViewModel(repository, FakeSoundRepository(), SoundConfig())
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertTrue(viewModel.state.value.analyticsEnabled)

        viewModel.onIntent(SettingsIntent.AnalyticsToggled(false))
        runCurrent()
        assertFalse(repository.settings.value.analyticsEnabled)
        assertFalse(viewModel.state.value.analyticsEnabled)
    }

    @Test
    fun `restarting the onboarding clears the flag and opens it`() = runTest {
        val viewModel = SettingsViewModel(repository, FakeSoundRepository(), SoundConfig())
        viewModel.onIntent(SettingsIntent.RestartOnboardingClicked)
        runCurrent()
        assertFalse(repository.settings.value.onboardingDone)
        assertEquals(SettingsEffect.OpenOnboarding, viewModel.effects.first())
    }

    @Test
    fun `app starts on the onboarding until it is done, and does not follow later changes`() = runTest {
        val fresh = FakeSettingsRepository()
        val sessions = FakeSessionRepository()
        val start = appStart(fresh, sessions)
        assertNull(start.startRoute.value)
        runCurrent()
        assertEquals(ONBOARDING_ROUTE, start.startRoute.value)
        fresh.setOnboardingDone(true)
        runCurrent()
        assertEquals(ONBOARDING_ROUTE, start.startRoute.value)

        val returning = appStart(repository, sessions)
        runCurrent()
        assertEquals(TopLevelDestination.PRACTICE.route, returning.startRoute.value)
        assertEquals("orphaned audio is cleaned up on every start", 2, sessions.orphanCleanups)
    }

    private fun appStart(settings: FakeSettingsRepository, sessions: FakeSessionRepository): AppStartViewModel {
        val store = FakeRunningPracticeStore()
        val clock = Clock.systemUTC()
        val practice = FakePracticeRepository()
        val trophies = FakeTrophyRepository()
        return AppStartViewModel(
            settings, sessions, store, PracticeFinisher(practice, store, clock), PracticeConfig(), clock,
            practice, trophies, TrophyAwarder(trophies, ProgressConfig(), clock), FakeProfileRepository(), FakeAvatarFiles(), FakeRepertoireRepository(), FakeSessionWaveforms(), FakeShareFiles(),
        )
    }

    @Test
    fun `the row of the recordings' sound names the default and leads to its screen`() = runTest {
        val sound = FakeSoundRepository()
        val viewModel = SettingsViewModel(FakeSettingsRepository(), sound, SoundConfig())
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        assertEquals(SoundCaption.BuiltIn(BuiltInPreset.OFF), viewModel.state.value.sound)

        sound.setDefault(SoundPresets.settingsOf(BuiltInPreset.WARM, SoundConfig()))
        runCurrent()
        assertEquals(SoundCaption.BuiltIn(BuiltInPreset.WARM), viewModel.state.value.sound)

        viewModel.onIntent(SettingsIntent.SoundClicked)
        runCurrent()
        assertEquals(listOf<SettingsEffect>(SettingsEffect.OpenSound), effects)
    }
}
