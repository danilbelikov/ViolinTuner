package com.violinjourney.app.ios

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.violinjourney.app.core.data.AppDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Where the iOS app keeps its data: the database and the settings in Application Support — the app's own, backed up
 * with the phone, never shown in Files. The database is created at the current version (no migrations: there was no
 * earlier iOS app), on the SQLite the app brings.
 */
internal object IosStorage {
    const val SETTINGS_FILE = "user_settings.preferences_pb"

    fun database(directory: String = dataDirectory()): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = "$directory/${AppDatabase.FILE_NAME}")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /**
     * DataStore allows one instance per file: whoever calls this keeps it for the life of the app, or cancels [scope]
     * before the file is opened again.
     */
    fun settings(
        directory: String = dataDirectory(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { "$directory/$SETTINGS_FILE".toPath() })

    @OptIn(ExperimentalForeignApi::class)
    fun dataDirectory(): String {
        val url = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        return requireNotNull(url?.path) { "no Application Support directory" }
    }
}
