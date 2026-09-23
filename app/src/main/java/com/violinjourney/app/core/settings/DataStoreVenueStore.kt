package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.venue.VenueStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** «Where we are» (spec 3.27), in the same preferences file as the settings: the copy of the data takes it along. */
class DataStoreVenueStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : VenueStore {

    override val stored: Flow<String?> = dataStore.data.map { it[HERE] }.distinctUntilChanged()

    override suspend fun store(value: String?) {
        dataStore.edit { if (value == null) it.remove(HERE) else it[HERE] = value }
    }

    private companion object {
        val HERE = stringPreferencesKey("venue_here")
    }
}
