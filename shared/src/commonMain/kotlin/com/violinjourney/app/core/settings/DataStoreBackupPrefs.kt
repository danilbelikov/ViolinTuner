package com.violinjourney.app.core.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.violinjourney.app.core.backup.BackupPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/**
 * When the last copy was made — in the same file as the settings, so it travels in a copy with them. The file in a copy is
 * taken before that copy is dated, though: a restore leaves the date of its own copy in the unpacked file ([stamp]).
 */
class DataStoreBackupPrefs(private val store: DataStore<Preferences>) : BackupPrefs {
    override val lastBackupAtEpochMs: Flow<Long?> = store.data.map { it[LAST_BACKUP_AT] }.distinctUntilChanged()

    override suspend fun setLastBackupAt(epochMs: Long) {
        store.edit { it[LAST_BACKUP_AT] = epochMs }
    }

    companion object {
        private val LAST_BACKUP_AT = longPreferencesKey("backup_last_at")

        /**
         * Leaves [epochMs] as the date of the last copy in the settings file at [settingsPath] — an unpacked copy, before it
         * is put in place (spec 3.20): after the restore the block «Данные» speaks of the copy it came from. The file is
         * read as the app will read it at its next start: one it cannot parse starts over the same way, with the statistics
         * off ([DataStoreSettingsRepository.startOverWhenUnreadable], [onUnreadable] tells the log), and a disk that does
         * not take it throws. A DataStore of its own for the time of this call: the next restore opens the same file again.
         */
        suspend fun stamp(settingsPath: String, epochMs: Long, io: CoroutineDispatcher, onUnreadable: (CorruptionException) -> Unit) {
            val life = Job()
            try {
                val store = PreferenceDataStoreFactory.createWithPath(
                    corruptionHandler = DataStoreSettingsRepository.startOverWhenUnreadable(onUnreadable),
                    scope = CoroutineScope(io + life),
                    produceFile = { settingsPath.toPath() },
                )
                store.edit { it[LAST_BACKUP_AT] = epochMs }
            } finally {
                // one DataStore a file: this one is let go before anybody opens the file again
                withContext(NonCancellable) { life.cancelAndJoin() }
            }
        }
    }
}
