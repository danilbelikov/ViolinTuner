package com.example.violintuner.feature.settings

import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.feature.sound.SoundCaption

data class SettingsState(
    val a4Hz: Int,
    val a4OptionsHz: List<Int>,
    val tolerance: TolerancePreset,
    /** What the default sound of all recordings is set to: the second line of the row «Звук записей». */
    val sound: SoundCaption,
)

sealed interface SettingsIntent {
    data class A4Selected(val hz: Int) : SettingsIntent

    data class ToleranceSelected(val preset: TolerancePreset) : SettingsIntent

    data object RestartOnboardingClicked : SettingsIntent

    data object SoundClicked : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenOnboarding : SettingsEffect

    data object OpenSound : SettingsEffect
}
