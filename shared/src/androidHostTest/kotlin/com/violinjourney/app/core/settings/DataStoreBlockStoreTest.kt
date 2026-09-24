package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.PieceBlock
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder

class DataStoreBlockStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }

    private val lesson = PracticeBlocks(
        practiceStartedAtEpochMs = 1_700_000_000_000,
        current = PieceBlock(4, 1_700_002_040_000, 1_200_000),
        finished = listOf(PieceBlock(1, 1_700_000_000_000, 600_000, 1_700_000_600_000), PieceBlock(12, 1_700_000_600_000, 900_000, 1_700_001_000_000)),
    )

    @Test
    fun `nothing is stored on a fresh install`() = runTest {
        assertNull(DataStoreBlockStore(dataStore()).blocks.first())
    }

    @Test
    fun `the blocks of the practice survive as they were — and clear forgets them`() = runTest {
        val file = dataStore()
        DataStoreBlockStore(file).update { lesson }
        // a new store on the same file: what a restart of the app would read
        assertEquals(lesson, DataStoreBlockStore(file).blocks.first())
        DataStoreBlockStore(file).clear()
        assertNull(DataStoreBlockStore(file).blocks.first())
    }

    @Test
    fun `a start and a stop are single edits through the rules`() = runTest {
        val store = DataStoreBlockStore(dataStore())
        store.update { BlockRules.started(it, 1_000, pieceId = 7, goalMs = 300_000, nowEpochMs = 2_000) }
        store.update { BlockRules.stopped(it, 1_000, nowEpochMs = 62_000) }
        assertEquals(PracticeBlocks(1_000, current = null, finished = listOf(PieceBlock(7, 2_000, 300_000, 62_000))), store.blocks.first())
        store.update { null }
        assertNull(store.blocks.first())
    }

    @Test
    fun `the text round-trips — and what cannot be read is dropped rather than failing`() {
        assertEquals(lesson, BlockCodec.decode(BlockCodec.encode(lesson)))
        val empty = PracticeBlocks(5, current = null, finished = emptyList())
        assertEquals(empty, BlockCodec.decode(BlockCodec.encode(empty)))
        assertNull(BlockCodec.decode("garbage"))
        assertEquals(PracticeBlocks(5, current = null, finished = listOf(PieceBlock(1, 2, 3, 4))), BlockCodec.decode("5|x:y|1:2:3:4,oops"))
    }

    @Test
    fun `an unreadable value reads as nothing stored`() = runTest {
        val file = dataStore()
        file.edit { it[stringPreferencesKey("practice_blocks")] = "not blocks" }
        assertNull(DataStoreBlockStore(file).blocks.first())
    }
}
