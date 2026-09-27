package com.violinjourney.app.core.data.journey

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: what a purchase and a choice of the home write (the twin of iOS's IosJourneyDaoTest). */
@RunWith(AndroidJUnit4::class)
class RoomHomeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: JourneyDao
    private lateinit var home: RoomHomeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = database.journeyDao()
        home = RoomHomeRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    private fun item(id: String) = HomeCatalog.byId.getValue(id)

    private suspend fun earn(takts: Int) = dao.insertEarning(EarningEntity(atEpochMs = 1, notesPlayed = 0, notesInTune = 0, durationMs = 0, takts = takts))

    @Test
    fun theTreeRemembersWhatStoodInItsPlaceAndKeepsItWhenPutInAgain() = runBlocking {
        earn(5_000)
        assertTrue(home.buy(item("cello"), 1))
        assertTrue(home.buy(item("xmas"), 2))
        var choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertEquals("cello", choices[HomeState.SEASON_KEY])

        assertTrue(home.buy(item("ficus"), 3))
        assertEquals("ficus", home.state.first().choices["floorR"])
        home.place("floorR", "xmas")
        choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertEquals("ficus", choices[HomeState.SEASON_KEY])
        home.place("floorR", "xmas")
        assertEquals("ficus", home.state.first().choices[HomeState.SEASON_KEY])
        home.place("floorR", "")
        home.place("floorR", "xmas")
        assertEquals("", home.state.first().choices[HomeState.SEASON_KEY])
        home.place("floorR", "cello")
        assertEquals("", home.state.first().choices[HomeState.SEASON_KEY])
    }

    @Test
    fun aTreeBoughtIntoAPlaceThatHeldNothingChosenForgetsWhatAnOlderTreeRemembered() = runBlocking {
        earn(500)
        dao.putHomeChoice(HomeChoiceEntity(HomeState.SEASON_KEY, "cello"))
        assertTrue(home.buy(item("xmas"), 1))
        val choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertFalse(HomeState.SEASON_KEY in choices)
    }
}
