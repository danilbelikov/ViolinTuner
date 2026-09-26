package com.violinjourney.app.core.data.backing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.core.domain.backing.PieceBacking
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.time.FixedWallClock
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. The backings of pieces (spec 3.32, 5.25). */
@RunWith(AndroidJUnit4::class)
class RoomBackingRepositoryTest {
    /** Remembers what it was asked to delete; there are no real files behind the rows here. */
    private class RecordingBackingFiles : BackingFiles {
        val deleted = mutableListOf<String>()

        override fun newFile(extension: String): File = File("unused.$extension")

        override fun existing(name: String): File? = null

        override fun delete(name: String) {
            deleted += name
        }

        override fun deleteOrphans(kept: Set<String>) = Unit
    }

    private object NoSheetFiles : SheetFiles {
        override suspend fun import(sourceUri: String): SheetFiles.Stored? = null

        override fun existing(name: String): File? = null

        override suspend fun delete(names: Collection<String>) = Unit

        override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit

        override fun newCameraFile(): File = File("unused")
    }

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomBackingRepository
    private lateinit var pieces: RoomRepertoireRepository
    private val files = RecordingBackingFiles()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomBackingRepository(database.backingDao(), files, Dispatchers.IO)
        pieces = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), FixedWallClock(Instant.fromEpochMilliseconds(1), TimeZone.UTC))
    }

    @After
    fun tearDown() = database.close()

    private fun backing(fileName: String) =
        Backing(fileName = fileName, title = fileName.substringBefore('.'), durationMs = 60_000, sampleRate = 48_000, channels = 2, sizeBytes = 1_000, addedAtEpochMs = 1)

    @Test
    fun aNewBackingIsStoredOnItsPieceInOneGoAndTheHousekeepingKeepsIt() = runBlocking {
        val pieceId = pieces.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        val id = repository.addForPiece(pieceId, backing("piano.m4a"))

        assertEquals(listOf(PieceBacking(pieceId, id, enabled = true)), repository.pieceBackings.first())
        val kept = repository.deleteUnused()
        assertTrue(kept.contains("piano.m4a"))
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun aReplacementTakesThePlaceOfTheOldOneWithTheChipOnAndTheOldOneGoesWithItsFile() = runBlocking {
        val pieceId = pieces.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        repository.addForPiece(pieceId, backing("old.m4a"))
        repository.setEnabled(pieceId, false)
        val id = repository.addForPiece(pieceId, backing("new.m4a"))

        // «Заменить» switches the chip on with the new one (setForPiece's rule)
        assertEquals(listOf(PieceBacking(pieceId, id, enabled = true)), repository.pieceBackings.first())
        repository.deleteUnused()
        assertEquals(listOf("old.m4a"), files.deleted)
    }
}
