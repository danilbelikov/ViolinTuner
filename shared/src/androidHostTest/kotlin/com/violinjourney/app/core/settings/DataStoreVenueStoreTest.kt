package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder

class DataStoreVenueStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `nothing is stored on a fresh install — a place is kept — and following the road clears it`() = runTest {
        val store = DataStoreVenueStore(dataStore())
        assertNull(store.stored.first())
        store.store("vienna")
        assertEquals("vienna", store.stored.first())
        store.store("home")
        assertEquals("home", store.stored.first())
        store.store(null)
        assertNull(store.stored.first())
    }
}
