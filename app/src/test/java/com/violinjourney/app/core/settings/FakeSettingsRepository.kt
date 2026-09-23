package com.violinjourney.app.core.settings

import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory repository for view model tests. */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setA4(hz: Int) {
        require(hz in UserSettings.A4_OPTIONS_HZ)
        settings.update { it.copy(a4Hz = hz) }
    }

    override suspend fun setTolerance(preset: TolerancePreset) = settings.update { it.copy(tolerance = preset) }

    override suspend fun setOnboardingDone(done: Boolean) = settings.update { it.copy(onboardingDone = done) }
}
