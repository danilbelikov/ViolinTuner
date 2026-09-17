package com.example.violintuner.core.settings

import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.domain.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Current settings and every later change; starts with defaults on a fresh install. */
    val settings: Flow<UserSettings>

    /** Values outside [UserSettings.A4_OPTIONS_HZ] are rejected. */
    suspend fun setA4(hz: Int)

    suspend fun setTolerance(preset: TolerancePreset)

    suspend fun setOnboardingDone(done: Boolean)
}
