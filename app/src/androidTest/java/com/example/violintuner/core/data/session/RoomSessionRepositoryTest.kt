package com.example.violintuner.core.data.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import java.io.File
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomSessionRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomSessionRepository
    private val audioFiles = RecordingAudioFiles()

    private class RecordingAudioFiles : SessionAudioFiles {
        val deleted = mutableListOf<String>()
        var orphanCall: Triple<Set<String>, Long, Long>? = null
        override fun newFile(): File = error("not used")
        override fun existing(name: String): File? = null
        override fun delete(name: String) { deleted += name }
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
            orphanCall = Triple(referenced, nowEpochMs, minAgeMs)
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomSessionRepository(database.sessionDao(), IntonationConfig(), audioFiles, Clock.systemUTC())
    }

    @After
    fun tearDown() = database.close()

    private fun newSession(startedAt: Long, tolerance: Double = 8.0, cents: Double = 10.0, audio: String? = null): NewSession {
        val config = IntonationConfig(toleranceCents = tolerance)
        val samples = List(20) { SessionSample(69, cents) } + listOf(null) + List(20) { SessionSample(71, -2.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return NewSession(
            startedAtEpochMs = startedAt,
            durationMs = samples.size * config.sessionBucketMs,
            config = config,
            samples = samples,
            metrics = analysis.metrics!!,
            previewZones = SessionAnalyzer.previewZones(analysis.segments, config),
            audioPath = audio,
        )
    }

    @Test
    fun savedSessionsAreListedNewestFirst() = runBlocking {
        repository.save(newSession(startedAt = 1_000))
        repository.save(newSession(startedAt = 3_000))
        repository.save(newSession(startedAt = 2_000))
        assertEquals(listOf(3_000L, 2_000L, 1_000L), repository.sessions.first().map { it.startedAtEpochMs })
    }

    @Test
    fun detailsAreAnalysedWithTheToleranceOfTheRecording() = runBlocking {
        val strict = repository.save(newSession(startedAt = 1_000, tolerance = 8.0))
        val lenient = repository.save(newSession(startedAt = 2_000, tolerance = 12.0))

        val strictDetails = repository.details(strict)!!
        assertEquals(50, strictDetails.summary.scorePercent)
        assertEquals(50, strictDetails.analysis.metrics!!.scorePercent)
        assertEquals(listOf(Zone.NEAR, Zone.IN_TUNE), strictDetails.summary.previewZones)
        assertEquals(41, strictDetails.samples.size)
        assertEquals(listOf(69, 71), strictDetails.analysis.segments.map { it.midi })

        assertEquals(100, repository.details(lenient)!!.analysis.metrics!!.scorePercent)
    }

    @Test
    fun renameTrimsAndBlankRestoresTheDefaultName() = runBlocking {
        val id = repository.save(newSession(startedAt = 1_000))
        repository.rename(id, "  Гаммы D-dur ")
        assertEquals("Гаммы D-dur", repository.details(id)!!.summary.title)
        repository.rename(id, "   ")
        assertNull(repository.details(id)!!.summary.title)
    }

    @Test
    fun deleteRemovesTheSessionWithItsSamples() = runBlocking {
        val id = repository.save(newSession(startedAt = 1_000))
        assertNotNull(database.sessionDao().samples(id))
        repository.delete(id)
        assertNull(repository.details(id))
        assertNull(database.sessionDao().samples(id))
        assertEquals(0, repository.sessions.first().size)
    }

    @Test
    fun deleteRemovesTheAudioFileToo() = runBlocking {
        val withAudio = repository.save(newSession(startedAt = 1_000, audio = "take-1.m4a"))
        val silent = repository.save(newSession(startedAt = 2_000))
        repository.delete(withAudio)
        repository.delete(silent)
        assertEquals(listOf("take-1.m4a"), audioFiles.deleted)
    }

    @Test
    fun orphanCleanupKeepsFilesOfStoredSessionsAndRecentOnes() = runBlocking {
        repository.save(newSession(startedAt = 1_000, audio = "kept.m4a"))
        repository.save(newSession(startedAt = 2_000))
        repository.deleteOrphanAudio()
        val (referenced, _, minAge) = audioFiles.orphanCall!!
        assertEquals(setOf("kept.m4a"), referenced)
        assertEquals(IntonationConfig().maxSessionMs, minAge)
    }

    @Test
    fun unknownSessionHasNoDetails() = runBlocking {
        assertNull(repository.details(42))
    }
}
