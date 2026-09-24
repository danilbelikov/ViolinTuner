package com.violinjourney.app.core.data.repertoire

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.session.SessionEntity
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomRepertoireRepositoryTest {
    /** Remembers what it was asked to delete; there are no real files behind the rows here. */
    private class RecordingSheetFiles : SheetFiles {
        val deleted = mutableListOf<String>()
        var orphanCall: Triple<Set<String>, Long, Long>? = null

        override suspend fun import(sourceUri: String): SheetFiles.Stored? = null

        override fun existing(name: String): File? = null

        override suspend fun delete(names: Collection<String>) {
            deleted += names
        }

        override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
            orphanCall = Triple(referenced, nowEpochMs, minAgeMs)
        }

        override fun newCameraFile(): File = File("unused")
    }

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomRepertoireRepository
    private val files = RecordingSheetFiles()
    private val config = RepertoireConfig()
    private val clock: WallClock = FixedWallClock(Instant.fromEpochMilliseconds(50_000), TimeZone.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomRepertoireRepository(database.repertoireDao(), files, config, clock)
    }

    @After
    fun tearDown() = database.close()

    private val minuet = PieceDraft(
        title = "  Менуэт соль мажор ", composer = "И. С. Бах", key = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR),
        tempoBpm = 96, status = PieceStatus.LEARNING, notes = "Такты 9–12: не спешить",
    )

    private suspend fun takeOf(pieceId: Long?): Long = database.sessionDao().insert(
        SessionEntity(
            title = null, startedAtEpochMs = 1_000, durationMs = 120_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
            scorePercent = 82, nearPercent = 10, offPercent = 8, maeCents = 5.0, biasCents = -2.0, previewZones = "IN",
            audioPath = null, pieceId = pieceId,
        ),
        bucketMs = 50, samples = byteArrayOf(0, 0, 0),
    )

    @Test
    fun aPieceComesBackAsItWasSavedCleanedAndWithItsKey() = runBlocking {
        val id = repository.add(minuet, nowEpochMs = 10)
        val piece = repository.piece(id)!!
        assertEquals("Менуэт соль мажор", piece.title)
        assertEquals("G-dur", piece.key!!.germanName)
        assertEquals(96, piece.tempoBpm)
        assertEquals(PieceStatus.LEARNING, piece.status)
        assertEquals(10L, piece.createdAtEpochMs)
        assertEquals(10L, piece.updatedAtEpochMs)
        assertEquals(listOf(piece), repository.pieces.first())
    }

    @Test
    fun anEditReplacesTheFieldsAndMovesTheEditTimeOnly() = runBlocking {
        val id = repository.add(minuet, nowEpochMs = 10)
        repository.update(id, PieceDraft(title = "Гавот", status = PieceStatus.IN_REPERTOIRE), nowEpochMs = 99)
        val piece = repository.piece(id)!!
        assertEquals("Гавот", piece.title)
        assertEquals("", piece.composer)
        assertNull(piece.key)
        assertNull(piece.tempoBpm)
        assertEquals(PieceStatus.IN_REPERTOIRE, piece.status)
        assertEquals(10L, piece.createdAtEpochMs)
        assertEquals(99L, piece.updatedAtEpochMs)

        repository.setStatus(id, PieceStatus.READING, nowEpochMs = 120)
        assertEquals(PieceStatus.READING, repository.piece(id)!!.status)
        assertEquals(120L, repository.piece(id)!!.updatedAtEpochMs)
    }

    @Test
    fun pagesStandInTheOrderTheyWereAddedAndCountAsActivity() = runBlocking {
        val id = repository.add(minuet, nowEpochMs = 10)
        repository.addPage(id, "a.jpg", "a-thumb.jpg", nowEpochMs = 20)
        repository.addPage(id, "b.jpg", "b-thumb.jpg", nowEpochMs = 30)
        val pages = repository.pages.first()
        assertEquals(listOf("a.jpg", "b.jpg"), pages.map { it.fileName })
        assertEquals(listOf(0, 1), pages.map { it.position })
        assertEquals(30L, repository.piece(id)!!.updatedAtEpochMs)

        repository.deletePage(pages[0].id, nowEpochMs = 40)
        assertEquals(listOf("b.jpg"), repository.pages.first().map { it.fileName })
        assertEquals(listOf("a.jpg", "a-thumb.jpg"), files.deleted)
        assertEquals(40L, repository.piece(id)!!.updatedAtEpochMs)

        // A page added after a removal goes to the end, not into the gap.
        repository.addPage(id, "c.jpg", "c-thumb.jpg", nowEpochMs = 50)
        assertEquals(listOf("b.jpg", "c.jpg"), repository.pages.first().map { it.fileName })
    }

    @Test
    fun aPageForAPieceThatIsGoneLeavesNoFilesBehind() = runBlocking {
        repository.addPage(pieceId = 404, fileName = "x.jpg", thumbFileName = "x-thumb.jpg", nowEpochMs = 1)
        assertTrue(repository.pages.first().isEmpty())
        assertEquals(listOf("x.jpg", "x-thumb.jpg"), files.deleted)
    }

    @Test
    fun deletingAPieceRemovesItsPagesAndFilesAndLeavesItsTakesAsPlainSessions() = runBlocking {
        val id = repository.add(minuet, nowEpochMs = 10)
        val other = repository.add(PieceDraft(title = "Гавот"), nowEpochMs = 11)
        repository.addPage(id, "a.jpg", "a-thumb.jpg", nowEpochMs = 20)
        repository.addPage(other, "o.jpg", "o-thumb.jpg", nowEpochMs = 21)
        val take = takeOf(id)
        val otherTake = takeOf(other)
        val free = takeOf(null)

        repository.delete(id)

        assertEquals(listOf(other), repository.pieces.first().map { it.id })
        assertEquals(listOf("o.jpg"), repository.pages.first().map { it.fileName })
        assertEquals(listOf("a.jpg", "a-thumb.jpg"), files.deleted)
        assertNull("the take stays, unlinked", database.sessionDao().session(take)!!.pieceId)
        assertEquals(other, database.sessionDao().session(otherTake)!!.pieceId)
        assertNull(database.sessionDao().session(free)!!.pieceId)
        assertEquals(3, database.sessionDao().observeAll().first().size)
    }

    @Test
    fun orphanCleanupKnowsEveryPageAndThumbnailAndSparesFreshFiles() = runBlocking {
        val id = repository.add(minuet, nowEpochMs = 10)
        repository.addPage(id, "a.jpg", "a-thumb.jpg", nowEpochMs = 20)
        repository.deleteOrphanFiles()
        assertEquals(Triple(setOf("a.jpg", "a-thumb.jpg"), 50_000L, config.orphanPhotoMinAgeMs), files.orphanCall)
    }

    @Test
    fun aScaleKeepsItsKindAndOctavesAndASectionOfOnesOwnGivesItsPiecesBackToTheMainOne() = runBlocking {
        val spec = com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec(
            Tonic.G, Accidental.SHARP, com.violinjourney.app.core.domain.repertoire.scale.ScaleKind.HARMONIC_MINOR, 2,
        )
        val scaleId = repository.add(
            PieceDraft(title = "gis-moll", key = spec.key, section = com.violinjourney.app.core.domain.repertoire.PieceSection.SCALES, scale = spec), 1_000,
        )
        assertEquals(spec, repository.piece(scaleId)!!.scale)

        val groupId = repository.addGroup("  Двойные ноты ", 2_000)
        assertEquals("Двойные ноты", repository.groups.first().single().name)
        val id = repository.add(minuet.copy(groupId = groupId), 3_000)
        repository.renameGroup(groupId, "Терции")
        assertEquals("Терции", repository.groups.first().single().name)

        repository.deleteGroup(groupId)
        assertEquals(emptyList<Any>(), repository.groups.first())
        val moved = repository.piece(id)!!
        assertEquals(null, moved.groupId)
        assertEquals(com.violinjourney.app.core.domain.repertoire.PieceSection.PIECES, moved.section)
    }

    @Test
    fun theDayAPieceWasLearntIsSetOnceClearedOnTheWayBackAndSetAnew() = runBlocking {
        val id = repository.add(minuet, 1_000)
        assertEquals(null, repository.piece(id)!!.learnedAtEpochMs)

        repository.setStatus(id, PieceStatus.IN_REPERTOIRE, 5_000)
        assertEquals(5_000L, repository.piece(id)!!.learnedAtEpochMs)
        repository.update(id, minuet.copy(status = PieceStatus.IN_REPERTOIRE, notes = "ещё"), 6_000)
        assertEquals("an edit of a learnt piece keeps its day", 5_000L, repository.piece(id)!!.learnedAtEpochMs)

        repository.setStatus(id, PieceStatus.LEARNING, 7_000)
        assertEquals(null, repository.piece(id)!!.learnedAtEpochMs)
        repository.update(id, minuet.copy(status = PieceStatus.IN_REPERTOIRE), 8_000)
        assertEquals(8_000L, repository.piece(id)!!.learnedAtEpochMs)

        val bornLearnt = repository.add(minuet.copy(title = "Старое", status = PieceStatus.IN_REPERTOIRE), 9_000)
        assertEquals(9_000L, repository.piece(bornLearnt)!!.learnedAtEpochMs)
    }
}
