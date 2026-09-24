package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.practice.RunningPractice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder

class DataStoreRunningPracticeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `nothing runs on a fresh install`() = runTest {
        assertNull(DataStoreRunningPracticeStore(dataStore()).running.first())
    }

    @Test
    fun `start — sound marks and clear are stored`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.start(1_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = null), store.running.first())
        store.markSound(5_000)
        store.markSound(9_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = 9_000), store.running.first())
        store.clear()
        assertNull(store.running.first())
    }

    @Test
    fun `a new start forgets the sound of the previous practice`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.start(1_000)
        store.markSound(5_000)
        store.start(20_000)
        assertEquals(RunningPractice(20_000, lastSoundEpochMs = null), store.running.first())
    }

    @Test
    fun `a sound mark without a running practice is ignored`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.markSound(5_000)
        assertNull(store.running.first())
        store.start(1_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = null), store.running.first())
    }

    @Test
    fun `shares the file with the settings without touching them`() = runTest {
        val dataStore = dataStore()
        val settings = DataStoreSettingsRepository(dataStore)
        val store = DataStoreRunningPracticeStore(dataStore)
        settings.setTolerance(TolerancePreset.PRO)
        store.start(1_000)
        store.clear()
        assertEquals(TolerancePreset.PRO, settings.settings.first().tolerance)
    }
}
