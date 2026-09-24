package com.violinjourney.app.core.data.progress

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.progress.Trophy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomTrophyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTrophyRepository

    private val first = LocalDate.parse("2026-09-02")
    private val later = LocalDate.parse("2026-09-13")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomTrophyRepository(database.trophyDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun trophiesComeBackLowestMarkFirstAndNotShown() = runBlocking {
        repository.award(10, later)
        repository.award(1, first)
        assertEquals(
            listOf(Trophy(1, first, shown = false), Trophy(10, later, shown = false)),
            repository.trophies.first(),
        )
    }

    @Test
    fun aSecondAwardOfTheSameMarkKeepsTheFirstDateAndTheShownFlag() = runBlocking {
        repository.award(1, first)
        repository.markShown(1)
        repository.award(1, later)
        assertEquals(listOf(Trophy(1, first, shown = true)), repository.trophies.first())
    }

    @Test
    fun markShownTouchesOnlyItsMark() = runBlocking {
        repository.award(1, first)
        repository.award(10, later)
        repository.markShown(10)
        assertEquals(listOf(false, true), repository.trophies.first().map { it.shown })
    }
}
