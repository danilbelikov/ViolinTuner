package com.example.violintuner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.UserSettings
import com.example.violintuner.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsState> = repository.settings.map(::stateOf).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = stateOf(UserSettings()),
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
            }
        }
    }

    private fun stateOf(settings: UserSettings) = SettingsState(
        a4Hz = settings.a4Hz,
        a4OptionsHz = UserSettings.A4_OPTIONS_HZ,
        tolerance = settings.tolerance,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
