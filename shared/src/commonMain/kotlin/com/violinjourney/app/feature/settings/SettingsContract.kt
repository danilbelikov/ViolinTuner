package com.violinjourney.app.feature.settings

import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.feature.sound.SoundCaption

data class SettingsState(
    val a4Hz: Int,
    val a4OptionsHz: List<Int>,
    val tolerance: TolerancePreset,
    /** What the default sound of all recordings is set to: the second line of the row «Звук записей». */
    val sound: SoundCaption,
    /** The switch of «Помогать улучшать приложение» in the block «Данные» (spec 3.34). */
    val analyticsEnabled: Boolean,
    /**
     * The stored settings have been read. Until then the state holds the defaults: the reference and the tolerance show them, as they
     * always did (spec 3.36.8 «Загрузка»), but the rows whose words a default would get wrong hold their places and say nothing yet —
     * the switch of the statistics (on by default: to one who turned it off it would say «on» and then flip) and the sound of the
     * recordings («без обработки» before «Камерный зал»). Nothing blinks.
     */
    val read: Boolean = true,
)

sealed interface SettingsIntent {
    data class A4Selected(val hz: Int) : SettingsIntent

    data class ToleranceSelected(val preset: TolerancePreset) : SettingsIntent

    data object RestartOnboardingClicked : SettingsIntent

    data object SoundClicked : SettingsIntent

    data class AnalyticsToggled(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenOnboarding : SettingsEffect

    data object OpenSound : SettingsEffect
}
