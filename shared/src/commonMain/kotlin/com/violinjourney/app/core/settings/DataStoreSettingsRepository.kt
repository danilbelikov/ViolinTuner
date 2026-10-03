package com.violinjourney.app.core.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.VideoQuality
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
                videoQuality = VideoQuality.entries.firstOrNull { it.name == preferences[VIDEO_QUALITY] }
                    ?: UserSettings().videoQuality,
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

    override suspend fun setVideoQuality(quality: VideoQuality) {
        dataStore.edit { it[VIDEO_QUALITY] = quality.name }
    }

    companion object {
        private val A4_HZ = intPreferencesKey("a4_hz")
        private val TOLERANCE = stringPreferencesKey("tolerance_preset")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        private val VIDEO_QUALITY = stringPreferencesKey("video_quality")

        /**
         * For the one DataStore of the settings file, on both platforms: a file that cannot be parsed (a failing disk, a
         * broken file from a copy) starts over instead of ending every start of the app. What starts over is all that file
         * holds — the onboarding mark, A4, the tolerance, the running practice, the name, the venue and the rest; the
         * database and the files are not touched. The statistics are written off, not left at their default «on»: the
         * lost file cannot tell whether the person had switched them off, and a switched-off choice holds until the person
         * turns it back (spec 3.34, rule 2). [onUnreadable] tells the log.
         */
        fun startOverWhenUnreadable(onUnreadable: (CorruptionException) -> Unit): ReplaceFileCorruptionHandler<Preferences> =
            ReplaceFileCorruptionHandler { e ->
                onUnreadable(e)
                preferencesOf(ANALYTICS_ENABLED to false)
            }
    }
}
