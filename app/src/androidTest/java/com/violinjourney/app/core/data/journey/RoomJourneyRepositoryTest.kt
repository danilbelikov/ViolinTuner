package com.violinjourney.app.core.data.journey

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.journey.JourneyRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: the progress of the journey as the database sums it (the twin of iOS's IosJourneyDaoTest). */
@RunWith(AndroidJUnit4::class)
class RoomJourneyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: JourneyDao
    private lateinit var journey: RoomJourneyRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = database.journeyDao()
        journey = RoomJourneyRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun earn(takts: Int) = dao.insertEarning(EarningEntity(atEpochMs = 1, notesPlayed = 0, notesInTune = 0, durationMs = 0, takts = takts))

    @Test
    fun theProgressSumsTheEarningsInTheDatabaseAndSpendsFromOnePurse() = runBlocking {
        assertEquals(0L, journey.progress.first().earned)
        earn(30)
        earn(50)
        earn(20)
        assertEquals(100L, journey.progress.first().earned)
        dao.begin(JourneyRoute.HOME, 1)
        assertTrue(dao.arrive("cremona", JourneyRoute.HOME, price = 60, now = 2))
        assertTrue(dao.buyForHome("tea", "ITEM", price = 25, slot = "deskR", now = 3))
        val progress = journey.progress.first()
        assertEquals(100L, progress.earned)
        assertEquals(85L, progress.spent)
        assertEquals(15L, progress.balance)
    }
}
