package com.violinjourney.app.core.settings

import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Current settings and every later change; starts with defaults on a fresh install. */
    val settings: Flow<UserSettings>

    /** Values outside [UserSettings.A4_OPTIONS_HZ] are rejected. */
    suspend fun setA4(hz: Int)

    suspend fun setTolerance(preset: TolerancePreset)

    suspend fun setOnboardingDone(done: Boolean)

    /** The switch of «Помогать улучшать приложение» (spec 3.34); takes effect at once. */
    suspend fun setAnalyticsEnabled(enabled: Boolean)
}
