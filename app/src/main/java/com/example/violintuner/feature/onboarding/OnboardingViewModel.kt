package com.example.violintuner.feature.onboarding

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Choices go straight to the repository, so nothing is lost on rotation or when the player
 * leaves half way; only the step lives here (in the saved state, to survive process death).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stepIndex = savedState.getStateFlow(KEY_STEP, 0)

    val state: StateFlow<OnboardingState> = combine(stepIndex, repository.settings, ::stateOf).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = stateOf(stepIndex.value, UserSettings()),
    )

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.PrimaryClicked -> when (currentStep()) {
                OnboardingStep.MICROPHONE -> effectChannel.trySend(OnboardingEffect.RequestMicPermission)
                OnboardingStep.REFERENCE_PITCH -> goTo(OnboardingStep.TOLERANCE)
                OnboardingStep.TOLERANCE -> finish()
            }
            OnboardingIntent.MicPermissionAnswered ->
                if (currentStep() == OnboardingStep.MICROPHONE) goTo(OnboardingStep.REFERENCE_PITCH)
            is OnboardingIntent.A4Selected -> viewModelScope.launch { repository.setA4(intent.hz) }
            is OnboardingIntent.ToleranceSelected -> viewModelScope.launch { repository.setTolerance(intent.preset) }
            OnboardingIntent.BackPressed -> {
                val previous = currentStep().ordinal - 1
                if (previous >= 0) savedState[KEY_STEP] = previous
            }
        }
    }

    private fun finish() {
        viewModelScope.launch {
            repository.setOnboardingDone(true)
            effectChannel.send(OnboardingEffect.Finished)
        }
    }

    private fun currentStep(): OnboardingStep = OnboardingStep.entries[stepIndex.value]

    private fun goTo(step: OnboardingStep) {
        savedState[KEY_STEP] = step.ordinal
    }

    private fun stateOf(stepIndex: Int, settings: UserSettings) = OnboardingState(
        step = OnboardingStep.entries[stepIndex],
        a4Hz = settings.a4Hz,
        a4OptionsHz = UserSettings.A4_OPTIONS_HZ,
        tolerance = settings.tolerance,
    )

    private companion object {
        const val KEY_STEP = "step"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
