package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.violinjourney.app.core.domain.journey.NoteCount
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The notes of the running practice, beside the practice itself in the settings file: they have
 * to outlive the process, like the timer does. Kept under the start of their practice — a count
 * that belongs to another one is replaced, never added to.
 */
class DataStorePracticeNotesStore @Inject constructor(private val store: DataStore<Preferences>) : PracticeNotesStore {
    override suspend fun add(practiceStartedAtEpochMs: Long, count: NoteCount) {
        store.edit { prefs ->
            val same = prefs[FOR_PRACTICE] == practiceStartedAtEpochMs
            prefs[FOR_PRACTICE] = practiceStartedAtEpochMs
            prefs[PLAYED] = (if (same) prefs[PLAYED] ?: 0 else 0) + count.played
            prefs[IN_TUNE] = (if (same) prefs[IN_TUNE] ?: 0 else 0) + count.inTune
        }
    }

    override suspend fun countFor(practiceStartedAtEpochMs: Long): NoteCount {
        val prefs = store.data.first()
        if (prefs[FOR_PRACTICE] != practiceStartedAtEpochMs) return NoteCount.ZERO
        return NoteCount(prefs[PLAYED] ?: 0, prefs[IN_TUNE] ?: 0)
    }

    override suspend fun clear() {
        store.edit {
            it.remove(FOR_PRACTICE)
            it.remove(PLAYED)
            it.remove(IN_TUNE)
        }
    }

    private companion object {
        val FOR_PRACTICE = longPreferencesKey("practice_notes_for")
        val PLAYED = intPreferencesKey("practice_notes_played")
        val IN_TUNE = intPreferencesKey("practice_notes_in_tune")
    }
}
