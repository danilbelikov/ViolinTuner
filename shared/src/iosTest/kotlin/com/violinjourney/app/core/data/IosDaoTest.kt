package com.violinjourney.app.core.data

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.data.backing.BackingEntity
import com.violinjourney.app.core.data.backing.PieceBackingEntity
import com.violinjourney.app.core.data.backing.TakeBackingEntity
import com.violinjourney.app.core.data.journey.EarningEntity
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.session.SessionEntity
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.ios.IosStorage
import com.violinjourney.app.ios.NoSheetFiles
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The rules the database itself keeps, on the real storage of iOS — Room on its own SQLite, in a folder of the simulator:
 * one purse for the road, the souvenirs and the home (spec 3.24, 5.17), and a backing kept while anything points at it
 * (spec 5.25). On Android the same DAO are checked only by the instrumented DatabaseMigrationTest; here they run in every
 * `:shared:iosSimulatorArm64Test`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDaoTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    private val database = IosStorage.database(directory)

    @AfterTest
    fun cleanUp() {
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun `one purse pays the road the souvenirs and the home`() = runTest {
        val dao = database.journeyDao()
        val journey = RoomJourneyRepository(dao, NoOpAnalytics())
        val home = RoomHomeRepository(dao, NoOpAnalytics())
        dao.begin("home", now = 1)
        dao.insertEarning(EarningEntity(atEpochMs = 1, notesPlayed = 400, notesInTune = 320, durationMs = 600_000, takts = 1_000))

        assertTrue(dao.arrive("cremona", "home", price = 300, now = 2))
        assertTrue(dao.buyExtra("cremona", "SOUVENIR", price = 40, now = 3))
        assertTrue(dao.buyForHome("cat_ginger", ITEM, price = 600, slot = "pet", now = 4))
        val progress = journey.progress.first()
        assertEquals(1_000, progress.earned)
        assertEquals(940, progress.spent, "the road, the souvenir and the cat from one purse")

        assertFalse(dao.arrive("milan", "cremona", price = 100, now = 5), "60 left: the road costs more")
        assertTrue(dao.buyForHome("tea", ITEM, price = 60, slot = "deskR", now = 6), "the last 60 go on tea")
        assertFalse(dao.buyForHome("lamp", ITEM, price = 1, slot = "lamp", now = 7), "nothing left")
        assertFalse(dao.buyForHome("cat_ginger", ITEM, price = 0, slot = "pet", now = 8), "a thing is bought once")
        assertFalse(dao.buyExtra("cremona", "SOUVENIR", price = 0, now = 9), "a souvenir is bought once")
        assertFalse(dao.arrive("vienna", "milan", price = 0, now = 10), "no stop is skipped")

        assertEquals(setOf("cat_ginger", "tea"), home.state.first().purchased)
        assertEquals(mapOf("pet" to "cat_ginger", "deskR" to "tea"), home.state.first().choices)
        assertEquals(1_000, journey.progress.first().spent)
    }

    @Test
    fun `two purchases at once on the last takts - only one goes through`() = runTest {
        val dao = database.journeyDao()
        repeat(ROUNDS) { round ->
            dao.insertEarning(EarningEntity(atEpochMs = round.toLong(), notesPlayed = 0, notesInTune = 0, durationMs = 0, takts = 100))
            // two taps of «Купить» on two threads, each for all that is left
            val bought = withContext(Dispatchers.Default) {
                coroutineScope {
                    listOf("a$round" to "left$round", "b$round" to "right$round").map { (id, slot) ->
                        async { dao.buyForHome(id, ITEM, price = 100, slot = slot, now = round.toLong()) }
                    }.awaitAll()
                }
            }
            assertEquals(1, bought.count { it }, "round $round: $bought")
        }
        val progress = RoomJourneyRepository(dao, NoOpAnalytics()).progress.first()
        assertEquals(progress.earned, progress.spent, "never more spent than earned")
    }

    @Test
    fun `a backing goes only when nothing points at it`() = runTest {
        val dao = database.backingDao()
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics())
        val pieceId = repertoire.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        val sessionId = database.sessionDao().insert(take(pieceId), bucketMs = 50, samples = ByteArray(3))
        val backing = dao.insert(
            BackingEntity(fileName = "a.m4a", title = "Piano", durationMs = 1, sampleRate = 44_100, channels = 2, sizeBytes = 1, addedAtEpochMs = 1),
        )
        dao.upsertPiece(PieceBackingEntity(pieceId, backing, enabled = true))
        dao.insertTake(
            TakeBackingEntity(
                sessionId = sessionId, backingId = backing, offsetMs = 200, recordedOffsetMs = 200, gainDb = -6f, playedMs = 1_000,
                output = "BLUETOOTH", deviceName = "Buds", latencyMs = 180,
            ),
        )
        assertEquals(emptyList(), dao.deleteUnused())

        // the piece goes, its row with it — a foreign key the SQLite of iOS keeps too; the take still holds the file
        repertoire.delete(pieceId)
        assertEquals(emptyList(), dao.pieceBackings().first())
        assertEquals(emptyList(), dao.deleteUnused())
        // the take goes as well: now nothing points at the backing
        database.sessionDao().delete(listOf(sessionId))
        assertEquals(emptyList(), dao.takeBackings().first())
        assertEquals(listOf("a.m4a"), dao.deleteUnused())
        assertEquals(emptyList(), dao.backings().first())
    }

    private fun take(pieceId: Long) = SessionEntity(
        title = null, startedAtEpochMs = 1_790_000_000_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 6.5, biasCents = -2.0, previewZones = "ININ",
        audioPath = "take.m4a", pieceId = pieceId,
    )

    private companion object {
        /** The kind of a thing of the home, as RoomHomeRepository writes it. */
        const val ITEM = "ITEM"
        const val ROUNDS = 20
    }
}
