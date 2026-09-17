package com.example.violintuner.feature.settings

import com.example.violintuner.core.domain.TolerancePreset

data class SettingsState(
    val a4Hz: Int,
    val a4OptionsHz: List<Int>,
    val tolerance: TolerancePreset,
)

sealed interface SettingsIntent {
    data class A4Selected(val hz: Int) : SettingsIntent

    data class ToleranceSelected(val preset: TolerancePreset) : SettingsIntent

    data object RestartOnboardingClicked : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenOnboarding : SettingsEffect
}
