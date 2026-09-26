package com.violinjourney.app.core.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** The date a restore leaves in the unpacked settings of its copy (spec 3.20), on a real file. */
class DataStoreBackupPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val date = longPreferencesKey("backup_last_at")
    private val analytics = booleanPreferencesKey("analytics_enabled")
    private val onboarding = booleanPreferencesKey("onboarding_done")

    private suspend fun <T> File.read(block: (androidx.datastore.preferences.core.Preferences) -> T): T {
        val life = Job()
        try {
            return block(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + life)) { this }.data.first())
        } finally {
            life.cancelAndJoin()
        }
    }

    @Test
    fun `a stamp leaves the date of the copy and keeps every other setting`() = runTest {
        val file = File(folder.root, "user_settings.preferences_pb")
        val life = Job()
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + life)) { file }.edit {
            it[analytics] = false
            it[onboarding] = true
            it[date] = 1_000L
        }
        life.cancelAndJoin()

        DataStoreBackupPrefs.stamp(file.path, 1_790_000_000_000, Dispatchers.IO) { throw it }
        file.read { settings ->
            assertEquals(1_790_000_000_000, settings[date])
            assertEquals(false, settings[analytics])
            assertEquals(true, settings[onboarding])
        }
        // a second restore in the same process opens the same file again
        DataStoreBackupPrefs.stamp(file.path, 1_790_000_000_001, Dispatchers.IO) { throw it }
        assertEquals(1_790_000_000_001, file.read { it[date] })
    }

    @Test
    fun `settings the app could not read start over as at a start - statistics off - with the date`() = runTest {
        val file = File(folder.root, "user_settings.preferences_pb").apply { writeBytes(byteArrayOf(0x0A, 0x7F)) }
        val unreadable = mutableListOf<CorruptionException>()
        DataStoreBackupPrefs.stamp(file.path, 1_790_000_000_000, Dispatchers.IO) { unreadable += it }
        assertTrue(unreadable.isNotEmpty())
        file.read { settings ->
            assertEquals(1_790_000_000_000, settings[date])
            assertEquals(false, settings[analytics])
            assertEquals(null, settings[onboarding])
        }
    }
}
