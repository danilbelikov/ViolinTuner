package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.practice.RunningPractice
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder

class DataStoreRunningPracticeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `nothing runs on a fresh install`() = runTest {
        assertNull(DataStoreRunningPracticeStore(dataStore()).running.first())
    }

    @Test
    fun `start — sound marks and clear are stored`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.startIfIdle(1_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = null), store.running.first())
        store.markSound(5_000)
        store.markSound(9_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = 9_000), store.running.first())
        store.clear()
        assertNull(store.running.first())
    }

    @Test
    fun `a start while a practice runs changes nothing`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        assertTrue(store.startIfIdle(1_000))
        store.markSound(5_000)
        assertFalse(store.startIfIdle(20_000), "one practice at a time (spec 3.12)")
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = 5_000), store.running.first())
    }

    @Test
    fun `a start after the end begins without the old sound`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.startIfIdle(1_000)
        store.markSound(5_000)
        store.clear()
        assertTrue(store.startIfIdle(20_000))
        assertEquals(RunningPractice(20_000, lastSoundEpochMs = null), store.running.first())
    }

    @Test
    fun `two starts at once make one practice`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        // «Начать занятие» on «Занятия» and the tag on Live in the same moment
        val first = async { store.startIfIdle(1_000) }
        val second = async { store.startIfIdle(1_200) }
        val started = awaitAll(first, second)
        assertEquals(1, started.count { it }, "exactly one of the two begins it")
        val winner = if (started[0]) 1_000L else 1_200L
        assertEquals(RunningPractice(winner, lastSoundEpochMs = null), store.running.first())
    }

    @Test
    fun `a sound mark without a running practice is ignored`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.markSound(5_000)
        assertNull(store.running.first())
        store.startIfIdle(1_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = null), store.running.first())
    }

    @Test
    fun `an answer marks the last mark as an answer and a sound takes it back`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        store.startIfIdle(1_000)
        store.markSound(5_000)
        // «Продолжаю заниматься» (spec 3.36.3): a sign of life that says whose it is
        store.markContinued(9_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = 9_000, lastMarkByAnswer = true), store.running.first())
        store.markSound(12_000)
        assertEquals(RunningPractice(1_000, lastSoundEpochMs = 12_000, lastMarkByAnswer = false), store.running.first())
    }

    @Test
    fun `a new practice and the end forget the answer`() = runTest {
        val store = DataStoreRunningPracticeStore(dataStore())
        // nothing runs: no answer to keep
        store.markContinued(500)
        assertNull(store.running.first())
        store.startIfIdle(1_000)
        store.markContinued(9_000)
        store.clear()
        assertTrue(store.startIfIdle(20_000))
        assertEquals(RunningPractice(20_000, lastSoundEpochMs = null, lastMarkByAnswer = false), store.running.first())
        store.markContinued(25_000)
        // one practice at a time: a start while it runs keeps the answer as it is
        assertFalse(store.startIfIdle(30_000))
        assertEquals(RunningPractice(20_000, lastSoundEpochMs = 25_000, lastMarkByAnswer = true), store.running.first())
    }

    @Test
    fun `shares the file with the settings without touching them`() = runTest {
        val dataStore = dataStore()
        val settings = DataStoreSettingsRepository(dataStore)
        val store = DataStoreRunningPracticeStore(dataStore)
        settings.setTolerance(TolerancePreset.PRO)
        store.startIfIdle(1_000)
        store.clear()
        assertEquals(TolerancePreset.PRO, settings.settings.first().tolerance)
    }
}
