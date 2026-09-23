package com.violinjourney.app.feature.onboarding

import com.violinjourney.app.core.domain.TolerancePreset

/** Steps in the order they are shown (spec 3.7). */
enum class OnboardingStep { MICROPHONE, REFERENCE_PITCH, TOLERANCE }

data class OnboardingState(
    val step: OnboardingStep,
    val a4Hz: Int,
    val a4OptionsHz: List<Int>,
    val tolerance: TolerancePreset,
)

sealed interface OnboardingIntent {
    /** The big button at the bottom; what it does depends on the step. */
    data object PrimaryClicked : OnboardingIntent

    /** The system dialog was answered; either answer moves on (spec 3.7). */
    data object MicPermissionAnswered : OnboardingIntent

    data class A4Selected(val hz: Int) : OnboardingIntent

    data class ToleranceSelected(val preset: TolerancePreset) : OnboardingIntent

    /** System back on any step but the first. */
    data object BackPressed : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object RequestMicPermission : OnboardingEffect

    /** Settings are stored and the flag is set: open Live. */
    data object Finished : OnboardingEffect
}
