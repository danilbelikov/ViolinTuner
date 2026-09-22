package com.example.violintuner.core.data.practice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.domain.practice.SavedBlock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Blocks of saved practices (spec 3.28): stored only with their element, gone with it. Runs on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class RoomPieceBlockRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomPieceBlockRepository
    private val day = LocalDate.of(2026, 9, 22)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomPieceBlockRepository(database.pieceBlockDao())
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO pieces (id, title, composer, status, notes, createdAtEpochMs, updatedAtEpochMs) " +
                "VALUES (1, 'Менуэт', 'Бах', 'LEARNING', '', 0, 0), (2, 'G-dur · 3 октавы', '', 'LEARNING', '', 0, 0)",
        )
    }

    @After
    fun tearDown() = database.close()

    private fun block(pieceId: Long, startedAt: Long, done: Boolean = true, paid: Boolean = done) =
        SavedBlock(pieceId, day, startedAt, durationMs = 600_000, goalMs = 600_000, done = done, paid = paid)

    @Test
    fun blocksOfAnElementThatIsGoneAreSkippedAndTheRestComeBackInOrder() = runBlocking {
        val stored = repository.add(listOf(block(2, startedAt = 5_000), block(9, startedAt = 6_000), block(1, startedAt = 1_000, done = false)))
        assertEquals(listOf(2L, 1L), stored.map { it.pieceId })
        assertEquals(2, stored.map { it.id }.distinct().size)

        val all = repository.blocks.first()
        assertEquals(listOf(1L, 2L), all.map { it.pieceId })
        assertEquals(block(1, startedAt = 1_000, done = false).copy(id = all[0].id), all[0])
        assertEquals(day, all[1].date)
        assertEquals(true, all[1].paid)
    }

    @Test
    fun deletingAnElementTakesItsBlocksWithIt() = runBlocking {
        repository.add(listOf(block(1, startedAt = 1_000), block(2, startedAt = 2_000)))
        database.repertoireDao().deletePiece(1)
        assertEquals(listOf(2L), repository.blocks.first().map { it.pieceId })
    }
}
