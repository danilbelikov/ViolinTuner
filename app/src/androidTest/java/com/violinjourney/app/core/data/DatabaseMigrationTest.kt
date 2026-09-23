package com.violinjourney.app.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a database file laid out exactly as an old version (statements from
 * `app/schemas/…/1.json` … `5.json`) through Room with the migrations. Room validates the migrated
 * schema against the current entities when it opens such a file, so a migration that leaves
 * a table different from what the entities declare fails here, not on the user's phone.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = context.getDatabasePath("migration-test.db")
    private var database: AppDatabase? = null

    @Before
    fun cleanUp() {
        file.parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(file)
    }

    /**
     * Version 1 with one session; [version2] adds what version 2 had on top — practice entries
     * with one row; [version3] adds the trophies of version 3 with one row; [version4] adds the
     * repertoire of version 4: a piece with a page, and the session becomes its take.
     */
    private fun createOldFile(version2: Boolean, version3: Boolean = false, version4: Boolean = false, version5: Boolean = false, version6: Boolean = false, version7: Boolean = false, version8: Boolean = false, version9: Boolean = false, version10: Boolean = false, version11: Boolean = false, version12: Boolean = false) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`a4Hz` REAL NOT NULL, `toleranceCents` REAL NOT NULL, `nearCents` REAL NOT NULL, " +
                    "`scorePercent` INTEGER NOT NULL, `nearPercent` INTEGER NOT NULL, `offPercent` INTEGER NOT NULL, " +
                    "`maeCents` REAL NOT NULL, `biasCents` REAL NOT NULL, `previewZones` TEXT NOT NULL, `audioPath` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startedAtEpochMs` ON `sessions` (`startedAtEpochMs`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_samples` (`sessionId` INTEGER NOT NULL, `bucketMs` INTEGER NOT NULL, " +
                    "`data` BLOB NOT NULL, PRIMARY KEY(`sessionId`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO sessions (id, title, startedAtEpochMs, durationMs, a4Hz, toleranceCents, nearCents, " +
                    "scorePercent, nearPercent, offPercent, maeCents, biasCents, previewZones, audioPath) " +
                    "VALUES (1, 'Гаммы', 1700000000000, 120000, 440.0, 8.0, 20.0, 80, 15, 5, 6.5, -2.0, 'ININ', NULL)",
            )
            db.execSQL("INSERT INTO session_samples (sessionId, bucketMs, data) VALUES (1, 50, X'000000')")
            if (version2) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `practice_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`manual` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_entries_date` ON `practice_entries` (`date`)")
                db.execSQL(
                    "INSERT INTO practice_entries (id, date, startedAtEpochMs, durationMs, manual) " +
                        "VALUES (1, '2026-09-13', 1789300000000, 2700000, 0)",
                )
            }
            if (version3) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trophies` (`hours` INTEGER NOT NULL, `awardedDate` TEXT NOT NULL, " +
                        "`shown` INTEGER NOT NULL, PRIMARY KEY(`hours`))",
                )
                db.execSQL("INSERT INTO trophies (hours, awardedDate, shown) VALUES (1, '2026-09-18', 1)")
            }
            if (version4) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pieces` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`composer` TEXT NOT NULL, `keyTonic` TEXT, `keyAccidental` TEXT, `keyMode` TEXT, `tempoBpm` INTEGER, " +
                        "`status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sheet_pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pieceId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`thumbFileName` TEXT NOT NULL, FOREIGN KEY(`pieceId`) REFERENCES `pieces`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sheet_pages_pieceId` ON `sheet_pages` (`pieceId`)")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `pieceId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_pieceId` ON `sessions` (`pieceId`)")
                db.execSQL(
                    "INSERT INTO pieces (id, title, composer, status, notes, createdAtEpochMs, updatedAtEpochMs) " +
                        "VALUES (1, 'Менуэт', 'Бах', 'LEARNING', 'смычок', 1, 2)",
                )
                db.execSQL("INSERT INTO sheet_pages (id, pieceId, position, fileName, thumbFileName) VALUES (1, 1, 0, 'a.jpg', 'a-thumb.jpg')")
                db.execSQL("UPDATE sessions SET pieceId = 1 WHERE id = 1")
            }
            if (version5) {
                val columns = DatabaseMigrations.SOUND_COLUMNS
                db.execSQL("CREATE TABLE IF NOT EXISTS `sound_settings` (`ownerId` INTEGER NOT NULL, $columns, PRIMARY KEY(`ownerId`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sound_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, $columns)",
                )
                db.execSQL("UPDATE sessions SET audioPath = 'take.m4a' WHERE id = 1")
            }
            if (version6) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `videoPath` TEXT")
            if (version7) {
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `bestTakeId` INTEGER")
                db.execSQL("INSERT INTO pieces (id, title, composer, status, notes, createdAtEpochMs, updatedAtEpochMs) VALUES (2, 'Концерт', '', 'IN_REPERTOIRE', '', 3, 777)")
            }
            if (version8) {
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `section` TEXT NOT NULL DEFAULT 'PIECES'")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `groupId` INTEGER")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `scaleKind` TEXT")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `scaleOctaves` INTEGER")
                db.execSQL("ALTER TABLE `pieces` ADD COLUMN `learnedAtEpochMs` INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `piece_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL)")
            }
            if (version9) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `journey_earnings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atEpochMs` INTEGER NOT NULL, `notesPlayed` INTEGER NOT NULL, `notesInTune` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `takts` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `journey_arrivals` (`stopId` TEXT NOT NULL, `arrivedAtEpochMs` INTEGER NOT NULL, `price` INTEGER NOT NULL, PRIMARY KEY(`stopId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `journey_extras` (`stopId` TEXT NOT NULL, `extra` TEXT NOT NULL, `price` INTEGER NOT NULL, `boughtAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`stopId`, `extra`))")
                db.execSQL("INSERT INTO journey_earnings (atEpochMs, notesPlayed, notesInTune, durationMs, takts) VALUES (1, 900, 800, 3600000, 1000)")
                db.execSQL("INSERT INTO journey_arrivals (stopId, arrivedAtEpochMs, price) VALUES ('home', 1, 0), ('cremona', 2, 300)")
            }
            if (version10) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `home_purchases` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `price` INTEGER NOT NULL, `boughtAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `home_choices` (`slot` TEXT NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`slot`))")
                db.execSQL("INSERT INTO home_purchases (id, kind, price, boughtAtEpochMs) VALUES ('cat_ginger', 'ITEM', 800, 5)")
            }
            // version 11 changed no table, only rows: the rug of the rented room, as its migration leaves it
            if (version11) {
                db.execSQL("INSERT OR IGNORE INTO home_purchases (id, kind, price, boughtAtEpochMs) VALUES ('rug_plum', 'ITEM', 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO home_choices (slot, itemId) VALUES ('rug', 'rug_plum')")
            }
            // version 12: the blocks, laid out as its migration leaves them
            if (version12) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `piece_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pieceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `goalMs` INTEGER NOT NULL, " +
                        "`done` INTEGER NOT NULL, `paid` INTEGER NOT NULL, FOREIGN KEY(`pieceId`) REFERENCES `pieces`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_piece_blocks_pieceId` ON `piece_blocks` (`pieceId`)")
                db.execSQL("ALTER TABLE `journey_earnings` ADD COLUMN `piecesPaid` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("PRAGMA user_version = ${if (version12) 12 else if (version11) 11 else if (version10) 10 else if (version9) 9 else if (version8) 8 else if (version7) 7 else if (version6) 6 else if (version5) 5 else if (version4) 4 else if (version3) 3 else if (version2) 2 else 1}")
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
            .also { database = it }

    @After
    fun tearDown() {
        database?.close()
        SQLiteDatabase.deleteDatabase(file)
    }

    @Test
    fun sessionsSurviveTheMigrationAndPracticeEntriesWork() = runBlocking {
        createOldFile(version2 = false)
        val db = openMigrated()

        val sessions = db.sessionDao().observeAll().first()
        assertEquals(1, sessions.size)
        assertEquals("Гаммы", sessions[0].title)
        assertEquals(80, sessions[0].scorePercent)
        assertEquals(50L, db.sessionDao().samples(1)!!.bucketMs)

        db.practiceDao().insert(
            com.violinjourney.app.core.data.practice.PracticeEntity(
                date = "2026-09-17", startedAtEpochMs = 1L, durationMs = 60_000, manual = false,
            ),
        )
        assertEquals("2026-09-17", db.practiceDao().observeAll().first().single().date)
    }

    @Test
    fun sessionsAndPracticeSurviveTheMigrationToTrophies() = runBlocking {
        createOldFile(version2 = true)
        val db = openMigrated()

        assertEquals("Гаммы", db.sessionDao().observeAll().first().single().title)
        val practice = db.practiceDao().observeAll().first().single()
        assertEquals("2026-09-13", practice.date)
        assertEquals(2_700_000L, practice.durationMs)

        db.trophyDao().insertIfAbsent(
            com.violinjourney.app.core.data.progress.TrophyEntity(hours = 1, awardedDate = "2026-09-18", shown = false),
        )
        assertEquals(1, db.trophyDao().observeAll().first().single().hours)
    }

    @Test
    fun everythingSurvivesTheMigrationToTheRepertoireAndOldSessionsBelongToNoPiece() = runBlocking {
        createOldFile(version2 = true, version3 = true)
        val db = openMigrated()

        val session = db.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals(null, session.pieceId)
        assertEquals(50L, db.sessionDao().samples(1)!!.bucketMs)
        assertEquals(2_700_000L, db.practiceDao().observeAll().first().single().durationMs)
        assertEquals(true, db.trophyDao().observeAll().first().single().shown)

        val pieceId = db.repertoireDao().insertPiece(
            com.violinjourney.app.core.data.repertoire.PieceEntity(
                title = "Менуэт", composer = "", keyTonic = null, keyAccidental = null, keyMode = null, tempoBpm = null,
                status = "READING", notes = "", createdAtEpochMs = 1, updatedAtEpochMs = 1,
            ),
        )
        assertEquals(true, db.repertoireDao().appendPage(pieceId, "a.jpg", "a-thumb.jpg", now = 2))
        assertEquals(1, db.repertoireDao().pagesOf(pieceId).size)
    }

    @Test
    fun everythingSurvivesTheMigrationToSoundSettingsAndNothingIsProcessedYet() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true)
        val db = openMigrated()

        val session = db.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals(1L, session.pieceId)
        assertEquals(2_700_000L, db.practiceDao().observeAll().first().single().durationMs)
        assertEquals(true, db.trophyDao().observeAll().first().single().shown)
        assertEquals("a.jpg", db.repertoireDao().pagesOf(1).single().fileName)

        assertEquals(emptyList<Any>(), db.soundDao().observeSettings().first())
        assertEquals(emptyList<Any>(), db.soundDao().observePresets().first())
        val columns = com.violinjourney.app.core.data.sound.SoundMapper.columnsOf(
            com.violinjourney.app.core.domain.sound.SoundRules.off(com.violinjourney.app.core.domain.sound.SoundConfig()),
        )
        db.soundDao().put(com.violinjourney.app.core.data.sound.SoundSettingsEntity(ownerId = 1, sound = columns))
        assertEquals(1L, db.soundDao().observeSettings().first().single().ownerId)
    }

    @Test
    fun everythingSurvivesTheMigrationToVideoTakesAndOldSessionsHaveNoPicture() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true)
        val db = openMigrated()

        val session = db.sessionDao().observeAll().first().single()
        assertEquals("Гаммы", session.title)
        assertEquals("take.m4a", session.audioPath)
        assertEquals(null, session.videoPath)
        assertEquals(1L, session.pieceId)
        assertEquals(2_700_000L, db.practiceDao().observeAll().first().single().durationMs)
        assertEquals(true, db.trophyDao().observeAll().first().single().shown)
        assertEquals("a.jpg", db.repertoireDao().pagesOf(1).single().fileName)
        assertEquals(emptyList<Any>(), db.soundDao().observeSettings().first())

        val id = db.sessionDao().insert(session.copy(id = 0, audioPath = "v.mp4", videoPath = "v.mp4"), bucketMs = 50, samples = ByteArray(3))
        assertEquals("v.mp4", db.sessionDao().session(id)!!.videoPath)
    }

    @Test
    fun everythingSurvivesTheMigrationToTheBestTakeAndOldPiecesHaveNone() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true)
        val db = openMigrated()

        val piece = db.repertoireDao().piece(1)!!
        assertEquals("Менуэт", piece.title)
        assertEquals(null, piece.bestTakeId)
        assertEquals(1L, db.sessionDao().observeAll().first().single().pieceId)
        assertEquals(2_700_000L, db.practiceDao().observeAll().first().single().durationMs)
        assertEquals("a.jpg", db.repertoireDao().pagesOf(1).single().fileName)

        db.repertoireDao().setBestTake(1, 1)
        assertEquals(1L, db.repertoireDao().piece(1)!!.bestTakeId)
        db.repertoireDao().setBestTake(1, null)
        assertEquals(null, db.repertoireDao().piece(1)!!.bestTakeId)
    }

    @Test
    fun everyPieceLandsInTheMainSectionAndTheLearntOnesGetTheirDay() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true)
        val db = openMigrated()

        val minuet = db.repertoireDao().piece(1)!!
        assertEquals("PIECES", minuet.section)
        assertEquals(null, minuet.groupId)
        assertEquals(null, minuet.scaleKind)
        assertEquals(null, minuet.learnedAtEpochMs)
        assertEquals("the day of the last edit stands in for the day it was learnt", 777L, db.repertoireDao().piece(2)!!.learnedAtEpochMs)
        assertEquals(1L, db.sessionDao().observeAll().first().single().pieceId)
        assertEquals("a.jpg", db.repertoireDao().pagesOf(1).single().fileName)
        assertEquals(emptyList<Any>(), db.repertoireDao().observeGroups().first())
    }

    @Test
    fun theJourneyStartsEmptyOnTopOfEverythingThatWasThere() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true, version8 = true)
        val db = openMigrated()

        assertEquals("Менуэт", db.repertoireDao().piece(1)!!.title)
        assertEquals(2_700_000L, db.practiceDao().observeAll().first().single().durationMs)
        assertEquals(emptyList<Any>(), db.journeyDao().observeArrivals().first())

        // the road is paid from what was earned, in one transaction, and no stop is skipped
        val dao = db.journeyDao()
        dao.begin("home", 1)
        assertEquals(false, dao.arrive("cremona", "home", price = 300, now = 2))
        dao.insertEarning(com.violinjourney.app.core.data.journey.EarningEntity(0, 1, 400, 320, 600_000, 340))
        assertEquals(false, dao.arrive("milan", "cremona", price = 100, now = 2))
        assertEquals(true, dao.arrive("cremona", "home", price = 300, now = 2))
        assertEquals(false, dao.arrive("cremona", "home", price = 1, now = 3))
        assertEquals(false, dao.buyExtra("cremona", "SOUVENIR", price = 150, now = 4))
        assertEquals(true, dao.buyExtra("cremona", "SOUVENIR", price = 40, now = 4))
        assertEquals(false, dao.buyExtra("cremona", "SOUVENIR", price = 0, now = 5))
        assertEquals(listOf("cremona", "home"), dao.observeArrivals().first().map { it.stopId }.sorted())
    }

    @Test
    fun theHomeStartsEmpty_andSharesOnePurseWithTheRoad() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true, version8 = true, version9 = true)
        val db = openMigrated()
        val dao = db.journeyDao()

        assertEquals(listOf("cremona", "home"), dao.observeArrivals().first().map { it.stopId }.sorted())
        // nothing but the rug the rented room used to come with: it went to the shop, and whoever had the room keeps it for nothing
        assertEquals(listOf("rug_plum" to 0), dao.observeHomePurchases().first().map { it.id to it.price })
        assertEquals(mapOf("rug" to "rug_plum"), dao.observeHomeChoices().first().associate { it.slot to it.itemId })

        // 1000 earned, 300 gone on the road: 700 left for both the road and the home
        assertEquals(false, dao.buyForHome("piano", "ITEM", price = 5_000, slot = "floorL", now = 3))
        assertEquals(true, dao.buyForHome("cat_ginger", "ITEM", price = 600, slot = "pet", now = 3))
        assertEquals(false, dao.buyForHome("cat_ginger", "ITEM", price = 0, slot = "pet", now = 4))
        assertEquals(mapOf("rug" to "rug_plum", "pet" to "cat_ginger"), dao.observeHomeChoices().first().associate { it.slot to it.itemId })
        // what the cat cost is not there for the road any more
        assertEquals(false, dao.arrive("milan", "cremona", price = 500, now = 5))
        assertEquals(true, dao.buyForHome("tea", "ITEM", price = 60, slot = "deskR", now = 6))
        dao.putHomeChoice(com.violinjourney.app.core.data.journey.HomeChoiceEntity("deskR", ""))
        assertEquals("", dao.observeHomeChoices().first().first { it.slot == "deskR" }.itemId)
    }

    @Test
    fun whoeverHadTheRentedRoomKeepsItsRug_forNothing() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true, version8 = true, version9 = true, version10 = true)
        val dao = openMigrated().journeyDao()

        val purchases = dao.observeHomePurchases().first().associateBy { it.id }
        assertEquals(setOf("cat_ginger", "rug_plum"), purchases.keys)
        assertEquals(0, purchases.getValue("rug_plum").price)
        assertEquals("rug_plum", dao.observeHomeChoices().first().single { it.slot == "rug" }.itemId)
    }

    @Test
    fun blocksStartEmpty_andOldEarningsPaidForNoElement() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true, version8 = true, version9 = true, version10 = true, version11 = true)
        val db = openMigrated()

        assertEquals(emptyList<Any>(), db.pieceBlockDao().observeAll().first())
        val earning = db.journeyDao().observeEarnings().first().single()
        assertEquals(1000, earning.takts)
        assertEquals(0, earning.piecesPaid)
        // a piece of the older versions takes a block now, and the block goes with it
        val piece = db.repertoireDao().observePieces().first().first { it.id == 1L }
        val stored = db.pieceBlockDao().insertWhereThePieceIs(
            listOf(com.violinjourney.app.core.data.practice.PieceBlockEntity(pieceId = piece.id, date = "2026-09-22", startedAtEpochMs = 1, durationMs = 600_000, goalMs = 600_000, done = true, paid = true)),
        )
        assertEquals(1, stored.size)
        db.repertoireDao().deletePiece(piece.id)
        assertEquals(emptyList<Any>(), db.pieceBlockDao().observeAll().first())
    }

    @Test
    fun backingsStartEmpty_andGoWhenNothingPointsAtThem() = runBlocking {
        createOldFile(version2 = true, version3 = true, version4 = true, version5 = true, version6 = true, version7 = true, version8 = true, version9 = true, version10 = true, version11 = true, version12 = true)
        val db = openMigrated()
        val dao = db.backingDao()

        assertEquals(emptyList<Any>(), dao.backings().first())
        assertEquals(emptyList<Any>(), dao.pieceBackings().first())
        assertEquals(1, db.journeyDao().observeEarnings().first().size) // everything of before is there
        val piece = db.repertoireDao().observePieces().first().first()
        val session = db.sessionDao().observeAll().first().first()

        val backing = dao.insert(com.violinjourney.app.core.data.backing.BackingEntity(fileName = "a.m4a", title = "Piano", durationMs = 1, sampleRate = 44_100, channels = 2, sizeBytes = 1, addedAtEpochMs = 1))
        dao.upsertPiece(com.violinjourney.app.core.data.backing.PieceBackingEntity(piece.id, backing, enabled = true))
        dao.insertTake(com.violinjourney.app.core.data.backing.TakeBackingEntity(session.id, backing, 200, 200, -6f, 1_000, "BLUETOOTH", "Buds", 180))
        assertEquals(emptyList<String>(), dao.deleteUnused())

        // the piece goes, its row with it; the take still holds the file
        db.repertoireDao().deletePiece(piece.id)
        assertEquals(emptyList<Any>(), dao.pieceBackings().first())
        assertEquals(emptyList<String>(), dao.deleteUnused())
        // the take goes too: now nothing points at the backing
        db.sessionDao().delete(listOf(session.id))
        assertEquals(listOf("a.m4a"), dao.deleteUnused())
        assertEquals(emptyList<Any>(), dao.backings().first())
    }
}
