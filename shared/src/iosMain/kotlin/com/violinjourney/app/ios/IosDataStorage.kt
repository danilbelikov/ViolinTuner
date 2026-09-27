package com.violinjourney.app.ios

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/**
 * The database and the settings file of one [IosGraph], with the scope the settings and the graph's long watches live in.
 * DataStore allows one instance per file and lets the file go only once the job of its scope has completed: [close]
 * cancels that scope and waits for it — its children on the main thread too — and only then closes the database. After
 * it a new graph may open the same files at once; a copy brought back (spec 3.20) is put in their place in between.
 */
internal class IosDataStorage(directory: String = IosStorage.dataDirectory()) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val database: AppDatabase = IosStorage.database(directory)
    val dataStore: DataStore<Preferences> = IosStorage.settings(directory, scope)

    suspend fun close() {
        scope.coroutineContext.job.cancelAndJoin()
        database.close()
    }
}
