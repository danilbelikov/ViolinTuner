package com.violinjourney.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** OnboardingViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltOnboardingViewModel @Inject constructor(
    repository: SettingsRepository,
    savedState: SavedStateHandle,
) : OnboardingViewModel(repository, savedState)
