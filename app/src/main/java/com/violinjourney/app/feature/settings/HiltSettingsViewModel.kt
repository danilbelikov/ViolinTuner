package com.violinjourney.app.feature.settings

import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** SettingsViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltSettingsViewModel @Inject constructor(
    repository: SettingsRepository,
    sound: SoundRepository,
    soundConfig: SoundConfig,
) : SettingsViewModel(repository, sound, soundConfig)
