package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.violinjourney.app.core.backup.BackupPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** When the last copy was made — in the same file as the settings: a restored copy brings its own date along, which is right. */
class DataStoreBackupPrefs(private val store: DataStore<Preferences>) : BackupPrefs {
    override val lastBackupAtEpochMs: Flow<Long?> = store.data.map { it[LAST_BACKUP_AT] }.distinctUntilChanged()

    override suspend fun setLastBackupAt(epochMs: Long) {
        store.edit { it[LAST_BACKUP_AT] = epochMs }
    }

    private companion object {
        val LAST_BACKUP_AT = longPreferencesKey("backup_last_at")
    }
}
