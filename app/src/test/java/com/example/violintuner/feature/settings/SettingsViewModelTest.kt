package com.example.violintuner.feature.settings

import com.example.violintuner.core.audio.playback.FakeSessionWaveforms
import com.example.violintuner.core.audio.share.FakeShareFiles
import com.example.violintuner.core.data.profile.FakeAvatarFiles
import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.domain.UserSettings
import com.example.violintuner.core.domain.practice.FakePracticeRepository
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeFinisher
import com.example.violintuner.core.domain.progress.FakeProfileRepository
import com.example.violintuner.core.domain.progress.FakeTrophyRepository
import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.TrophyAwarder
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.FakeSoundRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.settings.FakeSettingsRepository
import com.example.violintuner.feature.sound.SoundCaption
import com.example.violintuner.navigation.AppStartViewModel
import com.example.violintuner.navigation.ONBOARDING_ROUTE
import com.example.violintuner.navigation.TopLevelDestination
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
        assertEquals(SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER, SoundCaption.BuiltIn(BuiltInPreset.OFF)), viewModel.state.value)

        viewModel.onIntent(SettingsIntent.A4Selected(440))
        viewModel.onIntent(SettingsIntent.ToleranceSelected(TolerancePreset.PRO))
        runCurrent()
        assertEquals(UserSettings(440, TolerancePreset.PRO, onboardingDone = true), repository.settings.value)
        assertEquals(440, viewModel.state.value.a4Hz)
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
        assertEquals(TopLevelDestination.LIVE.route, returning.startRoute.value)
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
