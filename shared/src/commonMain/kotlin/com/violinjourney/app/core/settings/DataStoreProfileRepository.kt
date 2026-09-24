package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Name and avatar file name, in the same preferences file as the settings (spec 6). */
class DataStoreProfileRepository(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {
    override val profile: Flow<Profile> = dataStore.data
        .map { preferences ->
            // Cleaned on reading too: the file is not the only writer one can imagine.
            Profile(name = Profile.cleanName(preferences[NAME].orEmpty()), avatarFile = preferences[AVATAR_FILE])
        }
        .distinctUntilChanged()

    override suspend fun setName(name: String) {
        val clean = Profile.cleanName(name)
        dataStore.edit { if (clean.isEmpty()) it.remove(NAME) else it[NAME] = clean }
    }

    override suspend fun setAvatarFile(fileName: String?) {
        dataStore.edit { if (fileName == null) it.remove(AVATAR_FILE) else it[AVATAR_FILE] = fileName }
    }

    private companion object {
        val NAME = stringPreferencesKey("profile_name")
        val AVATAR_FILE = stringPreferencesKey("profile_avatar_file")
    }
}
