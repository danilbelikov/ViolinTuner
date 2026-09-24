package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The practice in progress, in the same preferences file as the settings: it has to survive a
 * restart of the app or the phone, and a row in the database would be a practice without an end.
 */
class DataStoreRunningPracticeStore(
    private val dataStore: DataStore<Preferences>,
) : RunningPracticeStore {

    override val running: Flow<RunningPractice?> = dataStore.data
        .map { preferences ->
            preferences[STARTED_AT]?.let { RunningPractice(it, preferences[LAST_SOUND]) }
        }
        .distinctUntilChanged()

    override suspend fun start(startedAtEpochMs: Long) {
        dataStore.edit {
            it[STARTED_AT] = startedAtEpochMs
            it.remove(LAST_SOUND)
        }
    }

    override suspend fun markSound(epochMs: Long) {
        dataStore.edit { if (it.contains(STARTED_AT)) it[LAST_SOUND] = epochMs }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(STARTED_AT)
            it.remove(LAST_SOUND)
        }
    }

    private companion object {
        val STARTED_AT = longPreferencesKey("practice_started_at")
        val LAST_SOUND = longPreferencesKey("practice_last_sound_at")
    }
}
