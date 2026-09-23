package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(name: String = "settings"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("$name.preferences_pb")
        }

    @Test
    fun `fresh install has spec defaults and onboarding ahead`() = runTest {
        val settings = DataStoreSettingsRepository(dataStore()).settings.first()
        assertEquals(UserSettings(a4Hz = 440, tolerance = TolerancePreset.INTERMEDIATE, onboardingDone = false), settings)
    }

    @Test
    fun `choices are stored`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        repository.setA4(442)
        repository.setTolerance(TolerancePreset.BEGINNER)
        repository.setOnboardingDone(true)
        assertEquals(UserSettings(442, TolerancePreset.BEGINNER, onboardingDone = true), repository.settings.first())
    }

    @Test
    fun `unsupported reference pitch is rejected`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        val error = runCatching { repository.setA4(415) }.exceptionOrNull()
        assertTrue("was $error", error is IllegalArgumentException)
        assertEquals(440, repository.settings.first().a4Hz)
    }

    @Test
    fun `statistics are on until they are turned off`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        assertTrue(repository.settings.first().analyticsEnabled)
        repository.setAnalyticsEnabled(false)
        assertFalse(repository.settings.first().analyticsEnabled)
    }

    @Test
    fun `unknown stored values fall back to defaults`() = runTest {
        val store = dataStore()
        store.edit {
            it[intPreferencesKey("a4_hz")] = 999
            it[stringPreferencesKey("tolerance_preset")] = "EXTREME"
        }
        val settings = DataStoreSettingsRepository(store).settings.first()
        assertEquals(440, settings.a4Hz)
        assertEquals(TolerancePreset.INTERMEDIATE, settings.tolerance)
    }

    @Test
    fun `config source applies the choices to the spec config`() = runTest {
        val repository = FakeSettingsRepository()
        val source = SettingsConfigSource(IntonationConfig(), repository)
        assertEquals(IntonationConfig(), source.config.first())

        repository.setA4(443)
        repository.setTolerance(TolerancePreset.PRO)
        val config = source.config.first()
        assertEquals(443.0, config.a4Hz, 0.0)
        assertEquals(3.0, config.toleranceCents, 0.0)
        assertEquals(IntonationConfig().nearCents, config.nearCents, 0.0)
    }
}
