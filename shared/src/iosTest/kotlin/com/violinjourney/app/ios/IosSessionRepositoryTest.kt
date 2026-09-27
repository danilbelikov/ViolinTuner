package com.violinjourney.app.ios

import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.time.SystemWallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The session repository over the real Room of iOS: what it says of the sound, and what a deletion tells the statistics. */
@OptIn(ExperimentalForeignApi::class)
class IosSessionRepositoryTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    /** Knows only the files it is given; the rest are gone. */
    private class Files(private val present: Set<String>) : SessionAudioFiles {
        val deleted = mutableListOf<String>()
        override fun newFile() = PlatformFile("/dev/null")
        override fun existing(name: String): PlatformFile? = PlatformFile("/tmp/$name").takeIf { name in present }
        override fun delete(name: String) {
            deleted += name
        }
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private val config = IntonationConfig()

    private fun take(audio: String?, video: String? = null): NewSession {
        val samples = List(20) { SessionSample(69, 2.0) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return NewSession(
            startedAtEpochMs = 1_790_000_000_000, durationMs = 1_000, config = config, samples = samples, metrics = analysis.metrics!!,
            previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = audio, videoPath = video,
        )
    }

    @Test
    fun `a recording whose sound file is gone is read without sound — the row keeps it`() = runTest {
        val database = IosStorage.database(directory)
        val files = Files(present = setOf("here.m4a"))
        val repository = RoomSessionRepository(database.sessionDao(), config, files, SystemWallClock, io = Dispatchers.Default, analytics = NoOpAnalytics())
        val here = repository.save(take("here.m4a"))
        val gone = repository.save(take("gone.m4a"))

        val listed = repository.sessions.first().associate { it.id to it.audioPath }
        assertEquals(mapOf(here to "here.m4a", gone to null), listed)
        assertEquals("here.m4a", repository.details(here)!!.summary.audioPath)
        assertNull(repository.details(gone)!!.summary.audioPath)
        assertNull(repository.summary(gone)!!.audioPath)
        // the file may come back with another copy: the row still names it
        assertEquals(setOf("here.m4a", "gone.m4a"), database.sessionDao().audioPaths().toSet())
        database.close()
    }

    @Test
    fun `a deletion tells the statistics what went — sound and video apart — and an id gone already tells nothing`() = runTest {
        val database = IosStorage.database(directory)
        val files = Files(present = emptySet())
        val analytics = FakeAnalytics()
        val repository = RoomSessionRepository(database.sessionDao(), config, files, SystemWallClock, analytics, io = Dispatchers.Default)
        val sound = repository.save(take("one.m4a"))
        val silent = repository.save(take(null))
        val video = repository.save(take("shot.mp4", video = "shot.mp4"))

        repository.delete(listOf(sound, silent, video, 99L))
        assertEquals(listOf("take_deleted {kind=audio, count=2}", "take_deleted {kind=video, count=1}"), analytics.sent().sorted())
        assertEquals(setOf("one.m4a", "shot.mp4"), files.deleted.toSet())

        repository.delete(listOf(sound, video))
        assertEquals(2, analytics.events.size, "nothing was there to delete")
        database.close()
    }
}
