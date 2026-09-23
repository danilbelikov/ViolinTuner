package com.violinjourney.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.settings.SettingsRepository
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
        val step = currentStep()
        when (intent) {
            OnboardingIntent.PrimaryClicked -> when (step) {
                OnboardingStep.MICROPHONE -> effectChannel.trySend(OnboardingEffect.RequestMicPermission)
                OnboardingStep.TOLERANCE -> finish()
                else -> OnboardingFlow.next(step)?.let(::goTo)
            }
            OnboardingIntent.SkipClicked -> goTo(OnboardingFlow.skip(step))
            is OnboardingIntent.PageShown -> goTo(OnboardingFlow.swipedTo(step, intent.page))
            OnboardingIntent.MicPermissionAnswered ->
                if (step == OnboardingStep.MICROPHONE) goTo(OnboardingStep.REFERENCE_PITCH)
            is OnboardingIntent.A4Selected -> viewModelScope.launch { repository.setA4(intent.hz) }
            is OnboardingIntent.ToleranceSelected -> viewModelScope.launch { repository.setTolerance(intent.preset) }
            OnboardingIntent.BackPressed -> OnboardingFlow.back(step)?.let(::goTo)
        }
    }

    private fun finish() {
        viewModelScope.launch {
            repository.setOnboardingDone(true)
            effectChannel.send(OnboardingEffect.Finished)
        }
    }

    private fun currentStep(): OnboardingStep = stepOf(stepIndex.value)

    private fun goTo(step: OnboardingStep) {
        savedState[KEY_STEP] = step.ordinal
    }

    private fun stateOf(stepIndex: Int, settings: UserSettings) = OnboardingState(
        step = stepOf(stepIndex),
        a4Hz = settings.a4Hz,
        a4OptionsHz = UserSettings.A4_OPTIONS_HZ,
        tolerance = settings.tolerance,
    )

    private fun stepOf(index: Int) = OnboardingStep.entries.getOrElse(index) { OnboardingStep.WELCOME }

    private companion object {
        const val KEY_STEP = "step"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
