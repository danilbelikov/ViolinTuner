package com.example.violintuner.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.violintuner.core.domain.repertoire.StandHintStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One flag in the same preferences file as the settings. */
class DataStoreStandHintStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : StandHintStore {

    override val seen: Flow<Boolean> = dataStore.data.map { it[SEEN] == true }.distinctUntilChanged()

    override suspend fun markSeen() {
        dataStore.edit { it[SEEN] = true }
    }

    private companion object {
        val SEEN = booleanPreferencesKey("stand_hint_seen")
    }
}
