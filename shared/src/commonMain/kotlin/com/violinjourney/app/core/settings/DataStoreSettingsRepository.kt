package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    // Stored values are validated on the way out: a file written by another app version must
    // never put the engine on a reference pitch or tolerance the UI cannot show.
    override val settings: Flow<UserSettings> = dataStore.data
        .map { preferences ->
            UserSettings(
                a4Hz = preferences[A4_HZ]?.takeIf { it in UserSettings.A4_OPTIONS_HZ }
                    ?: UserSettings.DEFAULT_A4_HZ,
                tolerance = TolerancePreset.entries.firstOrNull { it.name == preferences[TOLERANCE] }
                    ?: UserSettings().tolerance,
                onboardingDone = preferences[ONBOARDING_DONE] ?: false,
                // Absent means on: the fourth page of the onboarding says so, and a file written
                // by an older version has nothing stored here (spec 3.34).
                analyticsEnabled = preferences[ANALYTICS_ENABLED] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setA4(hz: Int) {
        require(hz in UserSettings.A4_OPTIONS_HZ) { "unsupported reference pitch $hz Hz" }
        dataStore.edit { it[A4_HZ] = hz }
    }

    override suspend fun setTolerance(preset: TolerancePreset) {
        dataStore.edit { it[TOLERANCE] = preset.name }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    override suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[ANALYTICS_ENABLED] = enabled }
    }

    private companion object {
        val A4_HZ = intPreferencesKey("a4_hz")
        val TOLERANCE = stringPreferencesKey("tolerance_preset")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
    }
}
