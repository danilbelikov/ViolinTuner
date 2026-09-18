package com.example.violintuner.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.domain.progress.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreProfileRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `a fresh install has no name and no photo`() = runTest {
        assertEquals(Profile.EMPTY, DataStoreProfileRepository(dataStore()).profile.first())
    }

    @Test
    fun `the name is stored cleaned`() = runTest {
        val repository = DataStoreProfileRepository(dataStore())
        repository.setName("  Даня ")
        assertEquals(Profile("Даня", avatarFile = null), repository.profile.first())
        repository.setName("я".repeat(30))
        assertEquals("я".repeat(24), repository.profile.first().name)
    }

    @Test
    fun `an empty name and a null photo remove what was stored`() = runTest {
        val repository = DataStoreProfileRepository(dataStore())
        repository.setName("Даня")
        repository.setAvatarFile("avatar-1.jpg")
        assertEquals(Profile("Даня", "avatar-1.jpg"), repository.profile.first())
        repository.setName("   ")
        repository.setAvatarFile(null)
        assertEquals(Profile.EMPTY, repository.profile.first())
    }

    @Test
    fun `the profile shares the file with the settings without touching them`() = runTest {
        val store = dataStore()
        val settings = DataStoreSettingsRepository(store)
        settings.setA4(442)
        settings.setTolerance(TolerancePreset.PRO)
        DataStoreProfileRepository(store).setName("Даня")
        assertEquals(442, settings.settings.first().a4Hz)
        assertEquals(TolerancePreset.PRO, settings.settings.first().tolerance)
    }
}
