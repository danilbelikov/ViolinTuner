package com.example.violintuner.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreStandHintStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `the hint is unseen on a fresh install and seen for good once marked`() = runTest {
        val store = DataStoreStandHintStore(dataStore())
        assertFalse(store.seen.first())
        store.markSeen()
        store.markSeen()
        assertTrue(store.seen.first())
    }
}
