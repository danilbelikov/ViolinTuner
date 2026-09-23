package com.violinjourney.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.SoundReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    sound: SoundRepository,
    private val soundConfig: SoundConfig,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(repository.settings, sound.default, sound.presets) { settings, default, presets ->
        stateOf(settings, SoundReducer.captionOf(default, presets, soundConfig))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = stateOf(UserSettings(), SoundCaption.BuiltIn(BuiltInPreset.OFF)),
    )

    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.A4Selected -> repository.setA4(intent.hz)
                is SettingsIntent.ToleranceSelected -> repository.setTolerance(intent.preset)
                SettingsIntent.RestartOnboardingClicked -> {
                    // cleared first: if the app dies on the way, the next start shows onboarding
                    repository.setOnboardingDone(false)
                    effectChannel.send(SettingsEffect.OpenOnboarding)
                }
                SettingsIntent.SoundClicked -> effectChannel.send(SettingsEffect.OpenSound)
                is SettingsIntent.AnalyticsToggled -> repository.setAnalyticsEnabled(intent.enabled)
            }
        }
    }

    private fun stateOf(settings: UserSettings, sound: SoundCaption) = SettingsState(
        a4Hz = settings.a4Hz,
        a4OptionsHz = UserSettings.A4_OPTIONS_HZ,
        tolerance = settings.tolerance,
        sound = sound,
        analyticsEnabled = settings.analyticsEnabled,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
