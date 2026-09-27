package com.violinjourney.app.ios

import com.violinjourney.app.core.data.journey.EarningEntity
import com.violinjourney.app.core.data.journey.HomeChoiceEntity
import com.violinjourney.app.core.data.journey.JourneyDao
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.JourneyRoute
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The journey and home tables over the real Room of iOS (the app's own builder): what a purchase and a choice write, what the progress reads. */
@OptIn(ExperimentalForeignApi::class)
class IosJourneyDaoTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    private fun item(id: String) = HomeCatalog.byId.getValue(id)

    private suspend fun JourneyDao.earn(takts: Int) = insertEarning(EarningEntity(atEpochMs = 1, notesPlayed = 0, notesInTune = 0, durationMs = 0, takts = takts))

    @Test
    fun `the tree remembers what stood in its place - bought or put in - and forgets nothing it is put on again`() = runTest {
        val database = IosStorage.database(directory)
        val dao = database.journeyDao()
        val home = RoomHomeRepository(dao)
        dao.earn(5_000)
        assertTrue(home.buy(item("cello"), 1))
        assertTrue(home.buy(item("xmas"), 2))
        var choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertEquals("cello", choices[HomeState.SEASON_KEY])

        // another thing for the place takes the tree away, as any choice does
        assertTrue(home.buy(item("ficus"), 3))
        assertEquals("ficus", home.state.first().choices["floorR"])
        // the tree put in again remembers the ficus; put in where it already stands, it keeps it
        home.place("floorR", "xmas")
        choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertEquals("ficus", choices[HomeState.SEASON_KEY])
        home.place("floorR", "xmas")
        assertEquals("ficus", home.state.first().choices[HomeState.SEASON_KEY])
        // a place left bare on purpose is remembered bare
        home.place("floorR", "")
        home.place("floorR", "xmas")
        assertEquals("", home.state.first().choices[HomeState.SEASON_KEY])
        // any other thing remembers nothing
        home.place("floorR", "cello")
        assertEquals("", home.state.first().choices[HomeState.SEASON_KEY])
        database.close()
    }

    @Test
    fun `the progress sums the earnings in the database - and spends from one purse`() = runTest {
        val database = IosStorage.database(directory)
        val dao = database.journeyDao()
        val journey = RoomJourneyRepository(dao)
        assertEquals(0L, journey.progress.first().earned)
        dao.earn(30)
        dao.earn(50)
        dao.earn(20)
        assertEquals(100L, journey.progress.first().earned)
        dao.begin(JourneyRoute.HOME, 1)
        assertTrue(dao.arrive("cremona", JourneyRoute.HOME, price = 60, now = 2))
        assertTrue(dao.buyForHome("tea", "ITEM", price = 25, slot = "deskR", now = 3))
        val progress = journey.progress.first()
        assertEquals(100L, progress.earned)
        assertEquals(85L, progress.spent)
        assertEquals(15L, progress.balance)
        database.close()
    }

    @Test
    fun `a tree bought into a place that held nothing chosen forgets what an older tree remembered`() = runTest {
        val database = IosStorage.database(directory)
        val dao = database.journeyDao()
        val home = RoomHomeRepository(dao)
        dao.earn(500)
        dao.putHomeChoice(HomeChoiceEntity(HomeState.SEASON_KEY, "cello"))
        assertTrue(home.buy(item("xmas"), 1))
        val choices = home.state.first().choices
        assertEquals("xmas", choices["floorR"])
        assertFalse(HomeState.SEASON_KEY in choices, "the room's own stood there: no row")
        database.close()
    }
}
